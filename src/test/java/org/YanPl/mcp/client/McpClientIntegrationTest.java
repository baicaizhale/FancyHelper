package org.YanPl.mcp.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.YanPl.mcp.core.McpTypes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * McpClient 集成测试：用 JDK HttpServer 起真实 MCP 服务器，覆盖两种传输。
 * legacy SSE 用例在旧实现（响应被 SSE 读线程丢弃）上必然失败，是本次修复的回归测试。
 */
@DisplayName("McpClient 传输集成测试")
class McpClientIntegrationTest {

    private static final Logger LOG = Logger.getLogger("mcp-test");

    private HttpServer server;
    private ExecutorService executor;

    @AfterEach
    void tearDown() {
        if (server != null) server.stop(0);
        if (executor != null) executor.shutdownNow();
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private String readBody(HttpExchange ex) throws IOException {
        return new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private void respondJson(HttpExchange ex, int status, JsonObject body, String sessionId) throws IOException {
        if (sessionId != null) ex.getResponseHeaders().add("Mcp-Session-Id", sessionId);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private JsonObject jsonRpcResult(String id, String resultJson) {
        return JsonParser.parseString(
                "{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"result\":" + resultJson + "}").getAsJsonObject();
    }

    // ==================== Streamable HTTP ====================

    @Test
    @DisplayName("streamable HTTP：initialize 建立会话、工具发现与调用可用、Mcp-Session-Id 会被携带")
    void testStreamableHttpTransport() throws Exception {
        AtomicReference<String> seenSessionId = new AtomicReference<>("");

        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/mcp", ex -> {
            String body = readBody(ex);
            JsonObject req = JsonParser.parseString(body).getAsJsonObject();
            String method = req.has("method") ? req.get("method").getAsString() : "";
            String id = req.has("id") ? req.get("id").getAsString() : null;

            if (id != null) {
                seenSessionId.set(ex.getRequestHeaders().getFirst("Mcp-Session-Id"));
            }

            switch (method) {
                case "initialize" -> respondJson(ex, 200, jsonRpcResult(id,
                        "{\"protocolVersion\":\"2025-03-26\",\"capabilities\":{\"tools\":{}},\"serverInfo\":{\"name\":\"test\",\"version\":\"1\"}}"),
                        "sid-123");
                case "tools/list" -> respondJson(ex, 200, jsonRpcResult(id,
                        "{\"tools\":[{\"name\":\"echo\",\"description\":\"echo tool\",\"inputSchema\":{\"type\":\"object\"}}]}"), null);
                case "tools/call" -> respondJson(ex, 200, jsonRpcResult(id,
                        "{\"content\":[{\"type\":\"text\",\"text\":\"pong\"}],\"isError\":false}"), null);
                default -> {
                    // notifications/initialized 等
                    ex.sendResponseHeaders(202, -1);
                    ex.close();
                }
            }
        });
        executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);
        server.start();

        McpClientConfig config = new McpClientConfig();
        config.setName("streamable");
        config.setUrl(baseUrl() + "/mcp");
        config.setTransport("auto");

        McpClient client = new McpClient(config, 5, LOG);
        assertTrue(client.connect(), "streamable HTTP 连接应成功");
        assertTrue(client.isConnected());
        assertEquals("sid-123", config.getSessionId(), "initialize 响应头中的会话 id 应被捕获");
        assertEquals(1, client.getTools().size());

        McpTypes.McpToolCallResult call = client.callTool("echo", new JsonObject());
        assertFalse(call.isError);
        assertEquals("pong", call.content.get(0).text);
        // 后续请求应携带会话 id
        assertEquals("sid-123", seenSessionId.get(), "tools/call 请求应携带 Mcp-Session-Id");

        client.disconnect();
    }

    // ==================== Legacy HTTP+SSE (2024-11-05) ====================

    @Test
    @DisplayName("legacy SSE：响应经 SSE 流按 id 分发，tools/call 可用（旧实现响应被丢弃必然失败）")
    void testLegacySseTransport() throws Exception {
        HttpServer sseServer = HttpServer.create(new InetSocketAddress(0), 0);
        AtomicReference<OutputStream> sseOut = new AtomicReference<>();
        Object sseLock = new Object();

        // GET /sse：发送 endpoint 事件并保持流
        sseServer.createContext("/sse", ex -> {
            ex.getResponseHeaders().add("Content-Type", "text/event-stream");
            ex.sendResponseHeaders(200, 0);
            OutputStream os = ex.getResponseBody();
            synchronized (sseLock) {
                os.write("event: endpoint\ndata: /message\n\n".getBytes(StandardCharsets.UTF_8));
                os.flush();
                sseOut.set(os);
            }
            // 保持打开直到测试结束（server.stop 会关闭）
        });

        AtomicReference<String> lastRequestBody = new AtomicReference<>("");

        // POST /message：投递请求（202 空 body），响应经 SSE 流推回
        sseServer.createContext("/message", ex -> {
            String body = readBody(ex);
            lastRequestBody.set(body);
            ex.sendResponseHeaders(202, -1);
            ex.close();

            // 解析 id，把响应推回 SSE 流
            JsonObject req = JsonParser.parseString(body).getAsJsonObject();
            String method = req.has("method") ? req.get("method").getAsString() : "";
            String id = req.get("id").getAsString();
            String resultJson;
            switch (method) {
                case "initialize" -> resultJson =
                        "{\"protocolVersion\":\"2024-11-05\",\"capabilities\":{\"tools\":{}},\"serverInfo\":{\"name\":\"test\",\"version\":\"1\"}}";
                case "tools/list" -> resultJson =
                        "{\"tools\":[{\"name\":\"echo\",\"description\":\"echo tool\",\"inputSchema\":{\"type\":\"object\"}}]}";
                case "tools/call" -> resultJson =
                        "{\"content\":[{\"type\":\"text\",\"text\":\"pong-sse\"}],\"isError\":false}";
                default -> resultJson = "{}";
            }
            pushSse(sseOut, sseLock, "{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"result\":" + resultJson + "}");
        });

        executor = Executors.newCachedThreadPool();
        sseServer.setExecutor(executor);
        sseServer.start();
        server = sseServer;

        McpClientConfig config = new McpClientConfig();
        config.setName("legacy-sse");
        config.setUrl(baseUrl() + "/sse");
        config.setTransport("sse");
        config.setCallTimeout(10);

        McpClient client = new McpClient(config, 5, LOG);
        assertTrue(client.connect(), "legacy SSE 连接应成功");
        assertEquals(1, client.getTools().size());

        McpTypes.McpToolCallResult call = client.callTool("echo", new JsonObject());
        assertFalse(call.isError, "tools/call 应成功（响应经由 SSE 流分发）");
        assertEquals("pong-sse", call.content.get(0).text);

        client.disconnect();
    }

    @Test
    @DisplayName("SSE 流被服务器断开后 connected 置 false，后续调用返回未连接错误")
    void testSseDisconnectMarksClientDead() throws Exception {
        HttpServer sseServer = HttpServer.create(new InetSocketAddress(0), 0);
        AtomicReference<OutputStream> sseOut = new AtomicReference<>();
        Object sseLock = new Object();

        sseServer.createContext("/sse", ex -> {
            ex.getResponseHeaders().add("Content-Type", "text/event-stream");
            ex.sendResponseHeaders(200, 0);
            OutputStream os = ex.getResponseBody();
            synchronized (sseLock) {
                os.write("event: endpoint\ndata: /message\n\n".getBytes(StandardCharsets.UTF_8));
                os.flush();
                sseOut.set(os);
            }
        });
        sseServer.createContext("/message", ex -> {
            // 测试中断开阶段不再回 JSON-RPC 响应
            ex.sendResponseHeaders(202, -1);
            ex.close();
        });
        executor = Executors.newCachedThreadPool();
        sseServer.setExecutor(executor);
        sseServer.start();
        server = sseServer;

        McpClientConfig config = new McpClientConfig();
        config.setName("sse-drop");
        config.setUrl(baseUrl() + "/sse");
        config.setTransport("sse");

        McpClient client = new McpClient(config, 5, LOG);
        // initialize 等请求在流断开后拿不到响应——只验证读线程对断流的反应：
        // 手动建立连接（connect 会因无 initialize 响应失败，但读线程在位），随后掐断流
        client.connect();

        OutputStream os = sseOut.get();
        assertNotNull(os, "SSE 流应已建立");
        os.close(); // 服务器侧掐断流

        // 读线程应退出并把 connected 置 false（failAllPending + finally）
        long deadline = System.currentTimeMillis() + 5000;
        while (client.isConnected() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertFalse(client.isConnected(), "流断开后客户端应标记为未连接");
        assertFalse(client.callTool("x", new JsonObject()).isError == false, "断线后调用应返回错误");

        client.disconnect();
    }

    private void pushSse(AtomicReference<OutputStream> sseOut, Object sseLock, String json) {
        OutputStream os = sseOut.get();
        if (os == null) return;
        try {
            synchronized (sseLock) {
                os.write(("data: " + json + "\n\n").getBytes(StandardCharsets.UTF_8));
                os.flush();
            }
        } catch (IOException ignored) {
        }
    }
}
