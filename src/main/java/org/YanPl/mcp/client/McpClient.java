package org.YanPl.mcp.client;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.YanPl.mcp.core.JsonRpcHandler;
import org.YanPl.mcp.core.JsonRpcMessage;
import org.YanPl.mcp.core.McpTypes;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

/**
 * 单个 MCP 服务器的 HTTP 连接客户端
 */
public class McpClient {

    private static final Gson gson = new Gson();
    private static final String MCP_PROTOCOL_VERSION = "2024-11-05";

    private final McpClientConfig config;
    private final HttpClient httpClient;
    private final Logger logger;
    private List<McpTypes.McpTool> tools = new ArrayList<>();
    private boolean connected = false;
    /** SSE 模式下的会话 ID，用于后续 POST 请求 */
    private String sessionId;

    public McpClient(McpClientConfig config, Logger logger) {
        this.config = config;
        this.logger = logger;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    public String getName() { return config.getName(); }
    public boolean isConnected() { return connected; }
    public List<McpTypes.McpTool> getTools() { return Collections.unmodifiableList(tools); }

    /**
     * 连接并完成 MCP 握手
     */
    public boolean connect() {
        try {
            String endpoint = resolveEndpoint();
            if (endpoint == null) {
                logger.warning("[MCP] " + config.getName() + ": 无法解析端点 URL");
                return false;
            }

            // 1. initialize 握手
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

            // 验证协议版本
            JsonObject result = initResp.result != null ? initResp.result.getAsJsonObject() : null;
            if (result != null && result.has("protocolVersion")) {
                String serverVersion = result.get("protocolVersion").getAsString();
                logger.info("[MCP] " + config.getName() + ": 协议版本 " + serverVersion);
            }

            // 2. 发送 initialized 通知
            sendNotification("notifications/initialized", null);

            // 3. 标记已连接
            connected = true;

            // 4. 发现工具
            if (!discoverTools()) {
                logger.warning("[MCP] " + config.getName() + ": 工具发现失败");
            }
            logger.info("[MCP] " + config.getName() + ": 连接成功，已发现 " + tools.size() + " 个工具");
            return true;

        } catch (Exception e) {
            logger.warning("[MCP] " + config.getName() + ": 连接失败 - " + e.getMessage());
            return false;
        }
    }

    /**
     * 解析端点 URL
     */
    private String resolveEndpoint() {
        String url = config.getUrl();
        if (url == null || url.isEmpty()) return null;

        // 如果以 /mcp 结尾，直接用
        if (url.endsWith("/mcp")) return url;
        // 如果以 /sse 结尾，去掉 /sse
        if (url.endsWith("/sse")) return url.substring(0, url.length() - 4) + "/mcp";
        // 否则追加重试，假设服务器暴露 /mcp 端点
        return url.endsWith("/") ? url + "mcp" : url + "/mcp";
    }

    /**
     * 发现工具列表
     */
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
                logger.info("[MCP] " + config.getName() + ": 发现 " + tools.size() + " 个工具");
            }
        } catch (Exception e) {
            logger.warning("[MCP] " + config.getName() + ": 解析工具列表失败 - " + e.getMessage());
            return false;
        }
        return true;
    }

    /**
     * 调用工具
     */
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

    /**
     * 断开连接
     */
    public void disconnect() {
        connected = false;
        tools.clear();
    }

    // ── 内部 HTTP 方法 ──

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
            String body = JsonRpcHandler.buildNotificationJson(method, params);
            HttpRequest request = buildHttpRequest(body);
            httpClient.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            logger.fine("[MCP] " + config.getName() + " 通知发送失败: " + e.getMessage());
        }
    }

    private HttpRequest buildHttpRequest(String body) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(resolveEndpoint()))
                .timeout(Duration.ofSeconds(config.getCallTimeout()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));

        String apiKey = config.getApiKey();
        if (apiKey != null && !apiKey.isEmpty()) {
            builder.header("Authorization", "Bearer " + apiKey);
        }
        return builder.build();
    }
}
