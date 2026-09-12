package org.YanPl.mcp.core;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.concurrent.atomic.AtomicLong;

/**
 * JSON-RPC 协议处理工具
 */
public class JsonRpcHandler {

    private static final Gson gson = new Gson();
    private static final AtomicLong idCounter = new AtomicLong(1);

    public static String generateId() {
        return String.valueOf(idCounter.getAndIncrement());
    }

    public static String buildRequestJson(String method, JsonObject params) {
        String id = generateId();
        return gson.toJson(JsonRpcMessage.Request.create(id, method, params));
    }

    /**
     * 以指定 id 构建请求（发送方需要用 id 关联异步到达的响应时使用，如 MCP SSE 传输）
     */
    public static String buildRequestJson(String id, String method, JsonObject params) {
        return gson.toJson(JsonRpcMessage.Request.create(id, method, params));
    }

    /**
     * 构建对服务器主动请求的错误响应（如不支持 sampling/roots 时回 METHOD_NOT_FOUND）
     */
    public static String buildErrorResponseJson(String id, int code, String message) {
        return gson.toJson(JsonRpcMessage.Response.error(id, code, message));
    }

    public static String buildNotificationJson(String method, JsonObject params) {
        return gson.toJson(JsonRpcMessage.Notification.create(method, params));
    }

    public static <T> T parseParams(JsonObject params, Class<T> clazz) {
        if (params == null) return null;
        return gson.fromJson(params, clazz);
    }

    public static <T> T parseResult(JsonElement result, Class<T> clazz) {
        if (result == null) return null;
        return gson.fromJson(result, clazz);
    }

    public static JsonObject params(String key, JsonElement value) {
        JsonObject obj = new JsonObject();
        obj.add(key, value);
        return obj;
    }

    public static JsonObject params(String k1, String v1) {
        JsonObject obj = new JsonObject();
        obj.addProperty(k1, v1);
        return obj;
    }

    public static JsonObject params(String k1, String v1, String k2, String v2) {
        JsonObject obj = new JsonObject();
        obj.addProperty(k1, v1);
        obj.addProperty(k2, v2);
        return obj;
    }

    public static String toolCallParams(String name, JsonObject arguments) {
        JsonObject obj = new JsonObject();
        obj.addProperty("name", name);
        obj.add("arguments", arguments != null ? arguments : new JsonObject());
        return gson.toJson(obj);
    }
}
