package org.YanPl.mcp.core;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;

/**
 * JSON-RPC 2.0 消息模型
 */
public class JsonRpcMessage {

    private static final Gson gson = new Gson();

    /** JSON-RPC 版本常量 */
    public static final String VERSION = "2.0";

    // ── 预定义错误码 ──
    public static final int PARSE_ERROR = -32700;
    public static final int INVALID_REQUEST = -32600;
    public static final int METHOD_NOT_FOUND = -32601;
    public static final int INVALID_PARAMS = -32602;
    public static final int INTERNAL_ERROR = -32603;
    public static final int SERVER_NOT_INITIALIZED = -32002;

    // ── 请求 ──
    public static class Request {
        public String jsonrpc = VERSION;
        public String id;
        public String method;
        public JsonObject params;

        public static Request create(String id, String method, JsonObject params) {
            Request r = new Request();
            r.id = id;
            r.method = method;
            r.params = params != null ? params : new JsonObject();
            return r;
        }
    }

    // ── 响应 ──
    public static class Response {
        public String jsonrpc = VERSION;
        public String id;
        public JsonElement result;
        public Error error;

        public static Response success(String id, JsonElement result) {
            Response r = new Response();
            r.id = id;
            r.result = result;
            return r;
        }

        public static Response error(String id, int code, String message) {
            Response r = new Response();
            r.id = id;
            r.error = new Error(code, message);
            return r;
        }
    }

    // ── 通知（无需响应） ──
    public static class Notification {
        public String jsonrpc = VERSION;
        public String method;
        public JsonObject params;

        public static Notification create(String method, JsonObject params) {
            Notification n = new Notification();
            n.method = method;
            n.params = params != null ? params : new JsonObject();
            return n;
        }
    }

    // ── 错误 ──
    public static class Error {
        public int code;
        public String message;
        @SerializedName("data")
        public JsonElement data;

        public Error() {}

        public Error(int code, String message) {
            this.code = code;
            this.message = message;
        }

        public Error(int code, String message, JsonElement data) {
            this.code = code;
            this.message = message;
            this.data = data;
        }
    }

    // ── 序列化工具方法 ──

    public static String toJson(Object msg) {
        return gson.toJson(msg);
    }

    public static Request parseRequest(String json) {
        return gson.fromJson(json, Request.class);
    }

    public static Response parseResponse(String json) {
        return gson.fromJson(json, Response.class);
    }

    public static boolean isResponse(String json) {
        try {
            JsonObject obj = gson.fromJson(json, JsonObject.class);
            return obj.has("id") && (obj.has("result") || obj.has("error"));
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isRequest(String json) {
        try {
            JsonObject obj = gson.fromJson(json, JsonObject.class);
            return obj.has("method") && obj.has("id");
        } catch (Exception e) {
            return false;
        }
    }

    public static String extractId(String json) {
        try {
            JsonObject obj = gson.fromJson(json, JsonObject.class);
            if (!obj.has("id")) return null;
            JsonElement idElement = obj.get("id");
            return idElement.isJsonPrimitive() ? idElement.getAsJsonPrimitive().getAsString() : idElement.toString();
        } catch (Exception e) {
            return null;
        }
    }

    public static String extractMethod(String json) {
        try {
            JsonObject obj = gson.fromJson(json, JsonObject.class);
            return obj.has("method") ? obj.get("method").getAsString() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
