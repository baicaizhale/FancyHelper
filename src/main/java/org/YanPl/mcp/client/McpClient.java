package org.YanPl.mcp.client;

import com.google.gson.JsonObject;
import org.YanPl.mcp.core.JsonRpcHandler;
import org.YanPl.mcp.core.JsonRpcMessage;
import org.YanPl.mcp.core.McpTypes;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class McpClient {

    private static final String MCP_PROTOCOL_VERSION = "2024-11-05";

    private final McpClientConfig config;
    private final HttpClient httpClient;
    private final Logger logger;
    private volatile List<McpTypes.McpTool> tools = new ArrayList<>();
    private volatile boolean connected = false;
    private volatile String postEndpoint;
    private volatile boolean running = false;
    private volatile CountDownLatch sseEndpointLatch;
    private volatile Thread sseReaderThread;

    public McpClient(McpClientConfig config, int connectTimeoutSeconds, Logger logger) {
        this.config = config;
        this.logger = logger;
        int timeout = connectTimeoutSeconds > 0 ? connectTimeoutSeconds : 10;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeout))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    public String getName() { return config.getName(); }
    public boolean isConnected() { return connected; }
    public List<McpTypes.McpTool> getTools() { return Collections.unmodifiableList(tools); }

    public boolean connect() {
        running = true;
        try {
            String transport = config.getTransport();
            boolean isSse = "sse".equalsIgnoreCase(transport);

            String endpoint = resolveEndpoint();
            if (endpoint == null) {
                logger.warning("[MCP] " + config.getName() + ": 无法解析端点 URL");
                return false;
            }

            // SSE 模式：先建立 SSE 连接获取消息端点
            if (isSse) {
                sseEndpointLatch = new CountDownLatch(1);
                startSseReader(endpoint);
                try {
                    if (!sseEndpointLatch.await(config.getCallTimeout(), TimeUnit.SECONDS)) {
                        logger.warning("[MCP] " + config.getName() + ": SSE 端点获取超时");
                        return false;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
                if (postEndpoint == null) {
                    logger.warning("[MCP] " + config.getName() + ": SSE 会话建立失败");
                    return false;
                }
                logger.fine("[MCP] " + config.getName() + ": SSE 会话已建立");
            } else {
                postEndpoint = endpoint;
            }

            // 现在 postEndpoint 已设置，发送握手请求到正确的端点
            JsonObject initParams = new JsonObject();
            initParams.addProperty("protocolVersion", MCP_PROTOCOL_VERSION);
            JsonObject clientInfo = new JsonObject();
            clientInfo.addProperty("name", "FancyHelper");
            clientInfo.addProperty("version", "1.0.0");
            initParams.add("clientInfo", clientInfo);
            initParams.add("capabilities", new JsonObject());

            JsonRpcMessage.Response initResp = sendRequest("initialize", initParams);
            if (initResp == null || initResp.error != null) {
                String err = initResp != null && initResp.error != null ? initResp.error.message : "无响应";
                logger.warning("[MCP] " + config.getName() + ": initialize 失败 - " + err);
                return false;
            }

            JsonObject result = initResp.result != null ? initResp.result.getAsJsonObject() : null;
            if (result != null && result.has("protocolVersion")) {
                String serverVersion = result.get("protocolVersion").getAsString();
                logger.fine("[MCP] " + config.getName() + ": 协议版本 " + serverVersion);
            }

            sendNotification("notifications/initialized", null);

            connected = true;

            if (!discoverTools()) {
                logger.warning("[MCP] " + config.getName() + ": 工具发现失败");
            }
            logger.info("[MCP] " + config.getName() + ": 连接成功，已发现 " + tools.size() + " 个工具");

            if (!isSse) {
                startPing();
            }
            return true;

        } catch (Exception e) {
            running = false;
            logger.warning("[MCP] " + config.getName() + ": 连接失败 - " + e.getMessage());
            return false;
        }
    }

    private void startSseReader(String sseUrl) {
        Thread t = new Thread(() -> {
            try {
                HttpRequest.Builder sseBuilder = HttpRequest.newBuilder()
                        .uri(URI.create(sseUrl))
                        .header("Accept", "text/event-stream")
                        .timeout(Duration.ofSeconds(config.getCallTimeout()))
                        .GET();

                String apiKey = config.getApiKey();
                if (apiKey != null && !apiKey.isEmpty()) {
                    sseBuilder.header("Authorization", "Bearer " + apiKey);
                }

                HttpRequest request = sseBuilder.build();
                HttpResponse<java.io.InputStream> response = httpClient.send(request,
                        HttpResponse.BodyHandlers.ofInputStream());

                if (response.statusCode() != 200) {
                    logger.warning("[MCP] " + config.getName() + ": SSE 连接返回 HTTP " + response.statusCode());
                    response.body().close();
                    return;
                }

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(response.body(), java.nio.charset.StandardCharsets.UTF_8))) {
                    String line;
                    while (running && (line = reader.readLine()) != null) {
                        if (line.startsWith("data: ")) {
                            String data = line.substring(6).trim();
                            if (data.startsWith("{")) {
                                JsonObject json = com.google.gson.JsonParser.parseString(data)
                                        .getAsJsonObject();
                                if (json.has("uri")) {
                                    postEndpoint = json.get("uri").getAsString();
                                    sseEndpointLatch.countDown();
                                }
                                if (json.has("sessionId")) {
                                    config.setSessionId(json.get("sessionId").getAsString());
                                }
                            } else {
                                postEndpoint = data;
                                sseEndpointLatch.countDown();
                            }
                        }
                        // 忽略 SSE 注释（keep-alive 等）
                    }
                } catch (java.io.IOException e) {
                    if (running) {
                        logger.warning("[MCP] " + config.getName() + ": SSE 流读取异常 - " + e.getMessage());
                    }
                }
            } catch (Exception e) {
                if (running) {
                    logger.warning("[MCP] " + config.getName() + ": SSE 会话异常 - " + e.getMessage());
                }
            }
        }, "mcp-sse-" + config.getName());
        t.setDaemon(true);
        sseReaderThread = t;
        t.start();
    }

    public boolean discoverTools() {
        JsonRpcMessage.Response resp = sendRequest("tools/list", null);
        if (resp == null || resp.error != null) {
            String err = resp != null && resp.error != null ? resp.error.message : "无响应";
            logger.warning("[MCP] " + config.getName() + ": tools/list 失败 - " + err);
            return false;
        }

        try {
            McpTypes.ToolsListResult listResult = JsonRpcHandler.parseResult(resp.result, McpTypes.ToolsListResult.class);
            if (listResult != null && listResult.tools != null) {
                tools = listResult.tools;
            }
        } catch (Exception e) {
            logger.warning("[MCP] " + config.getName() + ": 解析工具列表失败 - " + e.getMessage());
            return false;
        }
        return true;
    }

    public McpTypes.McpToolCallResult callTool(String toolName, JsonObject arguments) {
        if (!connected) return McpTypes.McpToolCallResult.error("MCP 服务器未连接: " + config.getName());

        try {
            JsonObject params = new JsonObject();
            params.addProperty("name", toolName);
            params.add("arguments", arguments != null ? arguments : new JsonObject());

            JsonRpcMessage.Response resp = sendRequest("tools/call", params);
            if (resp == null) {
                return McpTypes.McpToolCallResult.error("MCP 服务器无响应: " + config.getName());
            }
            if (resp.error != null) {
                return McpTypes.McpToolCallResult.error("MCP 错误: " + resp.error.message);
            }

            return JsonRpcHandler.parseResult(resp.result, McpTypes.McpToolCallResult.class);
        } catch (Exception e) {
            return McpTypes.McpToolCallResult.error("MCP 调用异常: " + e.getMessage());
        }
    }

    public void disconnect() {
        running = false;
        connected = false;
        tools = new ArrayList<>();
        postEndpoint = null;
        Thread t = sseReaderThread;
        if (t != null) {
            sseReaderThread = null;
            t.interrupt();
        }
    }

    private String getPostEndpoint() {
        return postEndpoint != null ? postEndpoint : resolveEndpoint();
    }

    private JsonRpcMessage.Response sendRequest(String method, JsonObject params) {
        try {
            String requestBody = JsonRpcHandler.buildRequestJson(method, params);
            HttpRequest request = buildHttpRequest(requestBody);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                logger.warning("[MCP] " + config.getName() + " " + method + " 返回 HTTP " + response.statusCode());
                return null;
            }
            return JsonRpcMessage.parseResponse(response.body());
        } catch (Exception e) {
            logger.warning("[MCP] " + config.getName() + " " + method + " 请求失败: " + e.getMessage());
            return null;
        }
    }

    private void sendNotification(String method, JsonObject params) {
        try {
            String ep = getPostEndpoint();
            if (ep == null) return;

            String body = JsonRpcHandler.buildNotificationJson(method, params);
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(ep))
                    .timeout(Duration.ofSeconds(config.getCallTimeout()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body));

            String apiKey = config.getApiKey();
            if (apiKey != null && !apiKey.isEmpty()) {
                builder.header("Authorization", "Bearer " + apiKey);
            }

            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200 && response.statusCode() != 202) {
                String respBody = response.body();
                if (respBody != null && respBody.length() > 200) respBody = respBody.substring(0, 200);
                logger.fine("[MCP] " + config.getName() + " 通知 " + method + " 返回 HTTP " + response.statusCode() + ": " + respBody);
            }
        } catch (Exception e) {
            logger.fine("[MCP] " + config.getName() + " 通知 " + method + " 发送失败: " + e.getMessage());
        }
    }

    private HttpRequest buildHttpRequest(String body) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(getPostEndpoint()))
                .timeout(Duration.ofSeconds(config.getCallTimeout()))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(body));

        String apiKey = config.getApiKey();
        if (apiKey != null && !apiKey.isEmpty()) {
            builder.header("Authorization", "Bearer " + apiKey);
        }
        return builder.build();
    }

    private void startPing() {
        Thread t = new Thread(() -> {
            while (running && connected) {
                try {
                    Thread.sleep(30_000);
                    if (!running || !connected) break;
                    JsonObject params = new JsonObject();
                    sendRequest("ping", params);
                } catch (InterruptedException e) { break; }
                catch (Exception e) { logger.fine("[MCP] " + config.getName() + " ping 异常: " + e.getMessage()); }
            }
        }, "mcp-ping-" + config.getName());
        t.setDaemon(true);
        t.start();
    }

    public boolean reconnect() {
        logger.fine("[MCP] " + config.getName() + ": 正在重连...");
        disconnect();
        try { Thread.sleep(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        return connect();
    }

    private String resolveEndpoint() {
        String url = config.getUrl();
        if (url == null || url.trim().isEmpty()) return null;
        return url.trim();
    }

}