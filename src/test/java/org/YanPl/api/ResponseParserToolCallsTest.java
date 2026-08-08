package org.YanPl.api;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.YanPl.model.AIResponse;
import org.YanPl.model.NativeToolCall;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ResponseParser 原生函数调用解析测试")
class ResponseParserToolCallsTest {

    private ResponseParser parser;
    private Gson gson;

    @BeforeEach
    void setUp() {
        parser = new ResponseParser();
        gson = new Gson();
    }

    private JsonObject parse(String json) {
        return gson.fromJson(json, JsonObject.class);
    }

    @Test
    @DisplayName("OpenAI message.tool_calls 应解析为 NativeToolCall")
    void testParseOpenAiToolCalls() {
        AIResponse response = parser.parseResponse(parse("""
            {"choices": [{"message": {
                "content": "让我查一下",
                "tool_calls": [
                    {"id": "call_abc", "type": "function",
                     "function": {"name": "run", "arguments": "{\\"command\\":\\"give @p apple\\"}"}}
                ]
            }}]}
            """));

        assertNotNull(response);
        List<NativeToolCall> calls = response.getToolCalls();
        assertEquals(1, calls.size());
        assertEquals("call_abc", calls.get(0).id());
        assertEquals("run", calls.get(0).name());
        assertEquals("{\"command\":\"give @p apple\"}", calls.get(0).argumentsJson());
    }

    @Test
    @DisplayName("多个 tool_calls 应全部解析")
    void testParseMultipleToolCalls() {
        AIResponse response = parser.parseResponse(parse("""
            {"choices": [{"message": {
                "content": null,
                "tool_calls": [
                    {"id": "c1", "type": "function", "function": {"name": "search", "arguments": "{\\"query\\":\\"x\\"}"}},
                    {"id": "c2", "type": "function", "function": {"name": "run", "arguments": "{\\"command\\":\\"say hi\\"}"}}
                ]
            }}]}
            """));

        assertNotNull(response);
        assertEquals(2, response.getToolCalls().size());
        assertEquals("search", response.getToolCalls().get(0).name());
        assertEquals("run", response.getToolCalls().get(1).name());
    }

    @Test
    @DisplayName("无 tool_calls 时应返回空列表")
    void testNoToolCallsReturnsEmpty() {
        AIResponse response = parser.parseResponse(parse("""
            {"choices": [{"message": {"content": "普通回复"}}]}
            """));

        assertNotNull(response);
        assertTrue(response.getToolCalls().isEmpty());
    }

    @Test
    @DisplayName("Cloudflare output function_call 项应解析")
    void testParseCloudflareFunctionCall() {
        AIResponse response = parser.parseResponse(parse("""
            {"output": [{"type": "message", "content": [
                {"type": "function_call", "call_id": "fc_1", "name": "run",
                 "arguments": "{\\"command\\":\\"list\\"}"}
            ]}]}
            """));

        assertNotNull(response);
        List<NativeToolCall> calls = response.getToolCalls();
        assertEquals(1, calls.size());
        assertEquals("run", calls.get(0).name());
        assertEquals("fc_1", calls.get(0).id());
    }

    @Test
    @DisplayName("OpenAI tool_calls 与 Cloudflare function_call 优先取前者")
    void testOpenAiPreferredOverCloudflare() {
        AIResponse response = parser.parseResponse(parse("""
            {"choices": [{"message": {
                "tool_calls": [{"id": "c1", "function": {"name": "run", "arguments": "{}"}}]
            }}],
            "output": [{"type": "message", "content": [
                {"type": "function_call", "name": "search", "arguments": "{}"}
            ]}]}
            """));

        assertNotNull(response);
        List<NativeToolCall> calls = response.getToolCalls();
        assertEquals(1, calls.size());
        assertEquals("run", calls.get(0).name());
    }
}
