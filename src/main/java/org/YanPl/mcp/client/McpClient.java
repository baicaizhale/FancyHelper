package org.YanPl.mcp.client;

import com.google.gson.JsonObject;
import org.YanPl.mcp.core.JsonRpcHandler;
import org.YanPl.mcp.core.JsonRpcMessage;
import org.YanPl.mcp.core.McpTypes;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * MCP 客户端：支持两种传输方式
 * <ul>
 *   <li>streamable HTTP（transport: auto/http）：POST JSON-RPC，响应可能是 application/json
 *       或 text/event-stream，会话通过 Mcp-Session-Id 头维持</li>
 *   <li>legacy HTTP+SSE（transport: sse，2024-11-05）：GET 建立 SSE 长连接获取消息端点，
 *       POST 只负责投递（通常 202），响应经由 SSE 流按 JSON-RPC id 分发</li>
 * </ul>
 * SSE 长连接不设请求超时（timeout 只用于单次请求型 POST）；读线程退出时置 connected=false
 * 并让所有挂起请求失败，由 McpClientManager 的重连任务负责恢复。
 */
public class McpClient {

    private static final String MCP_PROTOCOL_VERSION = "2024-11-05";

    private final McpClientConfig config;
    private final HttpClient httpClient;
    private final Logger logger;

    /** 等待响应的请求表：JSON-RPC id -> future。SSE 读线程与 HTTP 流消费线程按 id 分发完成 */
    private final Map<String, CompletableFuture<JsonRpcMessage.Response>> pendingRequests = new ConcurrentHashMap<>();

    private volatile List<McpTypes.McpTool> tools = new ArrayList<>();
    private volatile boolean connected = false;
    private volatile boolean running = false;
    private volatile String postEndpoint;
    private volatile CountDownLatch sseEndpointLatch;
    private volatile Thread sseReaderThread;
    /** 持有 SSE 长连接的响应体，disconnect 时关闭（interrupt 无法唤醒阻塞中的 read） */
    private volatile InputStream sseStream;

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
            boolean isSse = "sse".equalsIgnoreCase(config.getTransport());

            String endpoint = resolveEndpoint();
            if (endpoint == null) {
                logger.warning("[MCP] " + config.getName() + ": 无法解析端点 URL");
                return false;
            }

            if (isSse) {
                sseEndpointLatch = new CountDownLatch(1);
                startSseReader(endpoint);
                try {
                    if (!sseEndpointLatch.await(config.getCallTimeout(), TimeUnit.SECONDS)) {
                        logger.warning("[MCP] " + config.getName() + ": SSE 端点获取超时");
                        running = false;
                        return false;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    running = false;
                    return false;
                }
                if (postEndpoint == null) {
                    logger.warning("[MCP] " + config.getName() + ": SSE 会话建立失败");
                    running = false;
                    return false;
                }
                logger.fine("[MCP] " + config.getName() + ": SSE 会话已建立，消息端点 " + postEndpoint);
            } else {
                postEndpoint = endpoint;
            }

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
                running = false;
                failAllPending("连接中断");
                return false;
            }

            JsonObject result = initResp.result != null ? initResp.result.getAsJsonObject() : null;
            if (result != null && result.has("protocolVersion")) {
                logger.fine("[MCP] " + config.getName() + ": 协议版本 " + result.get("protocolVersion").getAsString());
            }

            sendNotification("notifications/initialized", null);

            connected = true;

            if (!discoverTools()) {
                logger.warning("[MCP] " + config.getName() + ": 工具发现失败");
            }
            logger.info("[MCP] " + config.getName() + ": 连接成功，已发现 " + tools.size() + " 个工具");

            startPing();
            return true;

        } catch (Exception e) {
            running = false;
            connected = false;
            failAllPending("连接失败");
            logger.warning("[MCP] " + config.getName() + ": 连接失败 - " + e.getMessage());
            return false;
        }
    }

    /**
     * 启动 SSE 长连接读线程。注意：GET 请求不能设置 .timeout()——
     * java.net.http 的 request timeout 覆盖整个响应生命周期，会把长连接在超时后掐断。
     */
    private void startSseReader(String sseUrl) {
        Thread t = new Thread(() -> {
            try {
                HttpRequest.Builder sseBuilder = HttpRequest.newBuilder()
                        .uri(URI.create(sseUrl))
                        .header("Accept", "text/event-stream")
                        .GET();

                String apiKey = config.getApiKey();
                if (apiKey != null && !apiKey.isEmpty()) {
                    sseBuilder.header("Authorization", "Bearer " + apiKey);
                }

                HttpRequest request = sseBuilder.build();
                HttpResponse<InputStream> response = httpClient.send(request,
                        HttpResponse.BodyHandlers.ofInputStream());

                if (response.statusCode() != 200) {
                    logger.warning("[MCP] " + config.getName() + ": SSE 连接返回 HTTP " + response.statusCode());
                    response.body().close();
                    return;
                }
                sseStream = response.body();

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(response.body(), java.nio.charset.StandardCharsets.UTF_8))) {
                    String line;
                    while (running && (line = reader.readLine()) != null) {
                        if (line.startsWith("data:")) {
                            String data = line.substring(5).trim();
                            if (data.isEmpty()) continue;
                            if (data.startsWith("{")) {
                                handleSseData(data);
                            } else {
                                // endpoint 事件：可能是相对路径（如 /message?sessionId=x），按 SSE URL 解析
                                postEndpoint = resolveAgainst(sseUrl, data);
                                CountDownLatch latch = sseEndpointLatch;
                                if (latch != null) latch.countDown();
                            }
                        }
                        // 忽略 event:/注释/keep-alive 行
                    }
                } catch (IOException e) {
                    if (running) {
                        logger.warning("[MCP] " + config.getName() + ": SSE 流读取异常 - " + e.getMessage());
                    }
                }
            } catch (Exception e) {
                if (running) {
                    logger.warning("[MCP] " + config.getName() + ": SSE 会话异常 - " + e.getMessage());
                }
            } finally {
                // 流断开即视为连接失效，挂起请求全部失败，交给重连任务恢复
                if (running) {
                    connected = false;
                    failAllPending("SSE 连接已断开");
                }
            }
        }, "mcp-sse-" + config.getName());
        t.setDaemon(true);
        sseReaderThread = t;
        t.start();
    }

    /**
     * 处理 SSE 流上的一条 JSON 消息：JSON-RPC 响应按 id 分发到挂起的请求，
     * 服务器主动请求（不支持）回 METHOD_NOT_FOUND，通知忽略。
     */
    private void handleSseData(String data) {
        try {
            String id = JsonRpcMessage.extractId(data);
            if (id != null && JsonRpcMessage.isResponse(data)) {
                CompletableFuture<JsonRpcMessage.Response> future = pendingRequests.remove(id);
                if (future != null) {
                    future.complete(JsonRpcMessage.parseResponse(data));
                }
                return;
            }
            if (id != null && JsonRpcMessage.isRequest(data)) {
                // 服务器主动发起的请求（sampling/roots 等）当前不支持，回错误避免服务器悬挂
                String method = JsonRpcMessage.extractMethod(data);
                logger.fine("[MCP] " + config.getName() + ": 收到不支持的服务器请求 " + method + "，已回拒");
                sendErrorResponse(id, JsonRpcMessage.METHOD_NOT_FOUND, "FancyHelper 不支持服务器主动请求: " + method);
            }
            // 通知：忽略
        } catch (Exception e) {
            logger.fine("[MCP] " + config.getName() + ": SSE 消息解析失败 - " + e.getMessage());
        }
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
        failAllPending("客户端已断开");
        InputStream stream = sseStream;
        sseStream = null;
        if (stream != null) {
            try { stream.close(); } catch (IOException ignored) {}
        }
        Thread t = sseReaderThread;
        if (t != null) {
            sseReaderThread = null;
            t.interrupt();
        }
    }

    /**
     * 发送 JSON-RPC 请求并等待响应。
     * <ul>
     *   <li>legacy SSE：POST 投递（2xx 即可，body 通常为空），响应由 SSE 读线程按 id 分发</li>
     *   <li>streamable HTTP：POST 后按 Content-Type 处理——application/json 直接解析，
     *       text/event-stream 同步消费流直到拿到本请求的响应</li>
     * </ul>
     */
    private JsonRpcMessage.Response sendRequest(String method, JsonObject params) {
        String id = JsonRpcHandler.generateId();
        CompletableFuture<JsonRpcMessage.Response> future = new CompletableFuture<>();
        pendingRequests.put(id, future);
        try {
            String requestBody = JsonRpcHandler.buildRequestJson(id, method, params);
            HttpRequest request = buildHttpRequest(requestBody);

            boolean legacySse = "sse".equalsIgnoreCase(config.getTransport());
            if (legacySse) {
                // 2024-11-05 SSE：POST 只投递，响应走 SSE 流
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    logger.warning("[MCP] " + config.getName() + " " + method + " 返回 HTTP " + response.statusCode());
                    return null;
                }
                // 兼容直接回 JSON 的非严格实现
                String body = response.body();
                if (body != null && body.trim().startsWith("{")) {
                    handleSseData(body.trim());
                }
            } else {
                // streamable HTTP：响应可能是 JSON 或 SSE 流
                HttpResponse<InputStream> response = httpClient.send(request,
                        HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    logger.warning("[MCP] " + config.getName() + " " + method + " 返回 HTTP " + response.statusCode());
                    return null;
                }
                captureSessionId(response);
                String contentType = response.headers().firstValue("Content-Type").orElse("").toLowerCase(Locale.ROOT);
                if (contentType.contains("text/event-stream")) {
                    consumeSseResponse(response.body(), id, method);
                } else {
                    String body = new String(response.body().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    response.body().close();
                    if (body.trim().startsWith("{")) {
                        handleSseData(body.trim());
                    }
                }
            }

            JsonRpcMessage.Response resp = future.get(config.getCallTimeout(), TimeUnit.SECONDS);
            // 会话 id 也可能在响应体中返回（部分实现）
            if (resp != null && config.getSessionId() == null && resp.result != null
                    && resp.result.isJsonObject() && resp.result.getAsJsonObject().has("sessionId")) {
                config.setSessionId(resp.result.getAsJsonObject().get("sessionId").getAsString());
            }
            return resp;
        } catch (java.util.concurrent.TimeoutException e) {
            logger.warning("[MCP] " + config.getName() + " " + method + " 响应超时（" + config.getCallTimeout() + "s）");
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            logger.warning("[MCP] " + config.getName() + " " + method + " 请求失败: " + e.getMessage());
            return null;
        } finally {
            pendingRequests.remove(id);
        }
    }

    /**
     * 同步消费 streamable HTTP 的 SSE 响应流，直到拿到 id 匹配的响应或流结束。
     */
    private void consumeSseResponse(InputStream body, String requestId, String method) {
        long deadline = System.nanoTime() + config.getCallTimeout() * 2_000_000_000L;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(body, java.nio.charset.StandardCharsets.UTF_8))) {
            String line;
            while (System.nanoTime() < deadline && (line = reader.readLine()) != null) {
                if (!line.startsWith("data:")) continue;
                String data = line.substring(5).trim();
                if (!data.startsWith("{")) continue;
                handleSseData(data);
                if (pendingRequests.get(requestId) == null) {
                    // 本请求的响应已被分发并移除，结束消费（服务器通知暂不持续订阅）
                    break;
                }
            }
        } catch (IOException e) {
            if (running) {
                logger.fine("[MCP] " + config.getName() + " " + method + " 响应流中断: " + e.getMessage());
            }
        } finally {
            try { body.close(); } catch (IOException ignored) {}
        }
    }

    private void captureSessionId(HttpResponse<?> response) {
        if (config.getSessionId() != null) return;
        response.headers().firstValue("Mcp-Session-Id").ifPresent(config::setSessionId);
    }

    private String getPostEndpoint() {
        return postEndpoint != null ? postEndpoint : resolveEndpoint();
    }

    private void sendErrorResponse(String requestId, int code, String message) {
        try {
            String body = JsonRpcHandler.buildErrorResponseJson(requestId, code, message);
            HttpRequest request = buildHttpRequest(body);
            httpClient.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            logger.fine("[MCP] " + config.getName() + " 错误响应发送失败: " + e.getMessage());
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

            appendAuthAndSession(builder);

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

        appendAuthAndSession(builder);
        return builder.build();
    }

    private void appendAuthAndSession(HttpRequest.Builder builder) {
        String apiKey = config.getApiKey();
        if (apiKey != null && !apiKey.isEmpty()) {
            builder.header("Authorization", "Bearer " + apiKey);
        }
        String sessionId = config.getSessionId();
        if (sessionId != null && !sessionId.isEmpty()) {
            builder.header("Mcp-Session-Id", sessionId);
        }
    }

    private void startPing() {
        Thread t = new Thread(() -> {
            while (running && connected) {
                try {
                    Thread.sleep(30_000);
                    if (!running || !connected) break;
                    JsonRpcMessage.Response resp = sendRequest("ping", new JsonObject());
                    if (resp == null) {
                        // ping 失败：标记断线，交由重连任务恢复
                        logger.warning("[MCP] " + config.getName() + ": ping 失败，标记断线等待重连");
                        connected = false;
                        failAllPending("ping 失败");
                        break;
                    }
                } catch (InterruptedException e) { break; }
                catch (Exception e) { logger.fine("[MCP] " + config.getName() + " ping 异常: " + e.getMessage()); }
            }
        }, "mcp-ping-" + config.getName());
        t.setDaemon(true);
        t.start();
    }

    private void failAllPending(String reason) {
        for (Map.Entry<String, CompletableFuture<JsonRpcMessage.Response>> entry : pendingRequests.entrySet()) {
            entry.getValue().completeExceptionally(new java.io.IOException(reason));
            pendingRequests.remove(entry.getKey());
        }
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

    /** endpoint 事件给出的地址可能相对 SSE URL，需解析成绝对地址 */
    private String resolveAgainst(String baseUrl, String endpoint) {
        if (endpoint.startsWith("http://") || endpoint.startsWith("https://")) {
            return endpoint;
        }
        try {
            return URI.create(baseUrl).resolve(endpoint).toString();
        } catch (Exception e) {
            return endpoint;
        }
    }

}
