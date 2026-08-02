package org.YanPl.mcp.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JsonRpcHandler 测试")
class JsonRpcHandlerTest {

    @Test
    @DisplayName("buildRequestJson 应包含 jsonrpc/id/method/params")
    void testBuildRequestJson() {
        JsonObject params = new JsonObject();
        params.addProperty("name", "say");
        params.addProperty("args", "hello");

        String json = JsonRpcHandler.buildRequestJson("tools/call", params);
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();

        assertEquals("2.0", obj.get("jsonrpc").getAsString());
        assertTrue(obj.has("id"));
        assertEquals("tools/call", obj.get("method").getAsString());
        assertEquals("say", obj.get("params").getAsJsonObject().get("name").getAsString());
        assertEquals("hello", obj.get("params").getAsJsonObject().get("args").getAsString());
    }

    @Test
    @DisplayName("buildRequestJson 的 id 应自增")
    void testBuildRequestJsonIncrementsId() {
        long first = Long.parseLong(JsonRpcHandler.buildRequestJson("a", new JsonObject())
                .replaceAll("\\D", ""));
        long second = Long.parseLong(JsonRpcHandler.buildRequestJson("b", new JsonObject())
                .replaceAll("\\D", ""));

        assertEquals(first + 1, second);
    }

    @Test
    @DisplayName("params 为 null 时 buildRequestJson 应输出空对象")
    void testBuildRequestJsonNullParams() {
        String json = JsonRpcHandler.buildRequestJson("test", null);
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();

        assertTrue(obj.has("params"));
        assertTrue(obj.get("params").getAsJsonObject().isEmpty());
    }

    @Test
    @DisplayName("buildNotificationJson 不应包含 id")
    void testBuildNotificationJsonHasNoId() {
        JsonObject params = new JsonObject();
        params.addProperty("value", 42);

        String json = JsonRpcHandler.buildNotificationJson("notifications/cancelled", params);
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();

        assertEquals("2.0", obj.get("jsonrpc").getAsString());
        assertFalse(obj.has("id"));
        assertEquals("notifications/cancelled", obj.get("method").getAsString());
        assertEquals(42, obj.get("params").getAsJsonObject().get("value").getAsInt());
    }

    @Test
    @DisplayName("parseParams 应反序列化为目标类型")
    void testParseParams() {
        JsonObject params = new JsonObject();
        params.addProperty("name", "测试");
        params.addProperty("count", 3);

        DummyDto dto = JsonRpcHandler.parseParams(params, DummyDto.class);

        assertNotNull(dto);
        assertEquals("测试", dto.name);
        assertEquals(3, dto.count);
    }

    @Test
    @DisplayName("parseParams 传入 null 应返回 null")
    void testParseParamsNull() {
        assertNull(JsonRpcHandler.parseParams(null, DummyDto.class));
    }

    @Test
    @DisplayName("params(String,String) 应生成单键值对")
    void testParamsTwoArgs() {
        JsonObject obj = JsonRpcHandler.params("key", "value");

        assertEquals("value", obj.get("key").getAsString());
        assertEquals(1, obj.size());
    }

    @Test
    @DisplayName("params(两对键值) 应生成两键值对")
    void testParamsFourArgs() {
        JsonObject obj = JsonRpcHandler.params("k1", "v1", "k2", "v2");

        assertEquals("v1", obj.get("k1").getAsString());
        assertEquals("v2", obj.get("k2").getAsString());
        assertEquals(2, obj.size());
    }

    @Test
    @DisplayName("toolCallParams 应包含 name 与 arguments")
    void testToolCallParams() {
        JsonObject arguments = new JsonObject();
        arguments.addProperty("args", "hello");

        String json = JsonRpcHandler.toolCallParams("run", arguments);
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();

        assertEquals("run", obj.get("name").getAsString());
        assertEquals("hello", obj.get("arguments").getAsJsonObject().get("args").getAsString());
    }

    @Test
    @DisplayName("toolCallParams 的 arguments 为 null 时应输出空对象")
    void testToolCallParamsNullArguments() {
        String json = JsonRpcHandler.toolCallParams("run", null);
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();

        assertEquals("run", obj.get("name").getAsString());
        assertTrue(obj.get("arguments").getAsJsonObject().isEmpty());
    }

    @Test
    @DisplayName("generateId 每次调用自增")
    void testGenerateIdIncrements() {
        long id1 = Long.parseLong(JsonRpcHandler.generateId());
        long id2 = Long.parseLong(JsonRpcHandler.generateId());
        long id3 = Long.parseLong(JsonRpcHandler.generateId());

        assertEquals(id1 + 1, id2);
        assertEquals(id2 + 1, id3);
    }

    @Test
    @DisplayName("JsonRpcMessage 解析 JSON-RPC 2.0 消息")
    void testJsonRpcMessageParse() {
        String requestJson = "{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"method\":\"tools/list\"}";

        assertTrue(JsonRpcMessage.isRequest(requestJson));
        assertFalse(JsonRpcMessage.isResponse(requestJson));
        assertEquals("1", JsonRpcMessage.extractId(requestJson));
        assertEquals("tools/list", JsonRpcMessage.extractMethod(requestJson));
    }

    private static class DummyDto {
        String name;
        int count;
    }
}
