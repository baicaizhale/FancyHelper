package org.YanPl.api;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.YanPl.model.AIResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ResponseParser 测试")
class ResponseParserTest {

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

    // ======================== OpenAI 兼容格式 ========================

    @Test
    @DisplayName("OpenAI choices.message.content")
    void testParseOpenAiFormat() {
        AIResponse response = parser.parseResponse(parse("""
            {"choices": [{"message": {"content": "这是回复内容"}}]}
            """));

        assertNotNull(response);
        assertEquals("这是回复内容", response.getContent());
    }

    @Test
    @DisplayName("OpenAI reasoning_content 应解析为思考")
    void testParseOpenAiReasoningContent() {
        AIResponse response = parser.parseResponse(parse("""
            {"choices": [{"message": {"content": "回复内容", "reasoning_content": "思考过程"}}]}
            """));

        assertNotNull(response);
        assertEquals("回复内容", response.getContent());
        assertEquals("思考过程", response.getThought());
    }

    @Test
    @DisplayName("content 为空时 reasoning_content 应作为正文")
    void testParseOpenAiReasoningContentFallsBackToContent() {
        AIResponse response = parser.parseResponse(parse("""
            {"choices": [{"message": {"reasoning_content": "只有思考"}}]}
            """));

        assertNotNull(response);
        assertEquals("只有思考", response.getContent());
    }

    @Test
    @DisplayName("OpenAI reasoning 字段（Kimi 格式）应解析为思考")
    void testParseOpenAiReasoningField() {
        AIResponse response = parser.parseResponse(parse("""
            {"choices": [{"message": {"content": "回复", "reasoning": "推理过程"}}]}
            """));

        assertNotNull(response);
        assertEquals("回复", response.getContent());
        assertEquals("推理过程", response.getThought());
    }

    @Test
    @DisplayName("content 为空时 reasoning 字段应作为正文")
    void testParseOpenAiReasoningFieldFallsBackToContent() {
        AIResponse response = parser.parseResponse(parse("""
            {"choices": [{"message": {"reasoning": "工具调用文本"}}]}
            """));

        assertNotNull(response);
        assertEquals("工具调用文本", response.getContent());
    }

    // ======================== Cloudflare output 格式 ========================

    @Test
    @DisplayName("Cloudflare output.message.content[].output_text")
    void testParseCloudflareOutputFormat() {
        AIResponse response = parser.parseResponse(parse("""
            {"output": [{"type": "message", "content": [{"type": "output_text", "text": "Cloudflare 回复"}]}]}
            """));

        assertNotNull(response);
        assertEquals("Cloudflare 回复", response.getContent());
    }

    @Test
    @DisplayName("Cloudflare output 的 thought 类型应解析为思考")
    void testParseCloudflareOutputThought() {
        AIResponse response = parser.parseResponse(parse("""
            {"output": [{"type": "message", "content": [
                {"type": "output_text", "text": "回复内容"},
                {"type": "thought", "text": "思考内容"}
            ]}]}
            """));

        assertNotNull(response);
        assertEquals("回复内容", response.getContent());
        assertEquals("思考内容", response.getThought());
    }

    @Test
    @DisplayName("reasoning 项的 summary 数组应拼接为思考")
    void testParseCloudflareReasoningSummary() {
        AIResponse response = parser.parseResponse(parse("""
            {"output": [
                {"type": "message", "content": [{"type": "output_text", "text": "回复内容"}]},
                {"type": "reasoning", "summary": [{"text": "总结1"}, {"text": "总结2"}]}
            ]}
            """));

        assertNotNull(response);
        assertEquals("回复内容", response.getContent());
        assertTrue(response.getThought().contains("总结1"));
        assertTrue(response.getThought().contains("总结2"));
    }

    @Test
    @DisplayName("reasoning 项的 content[].reasoning_text 应解析为思考")
    void testParseCloudflareReasoningContent() {
        AIResponse response = parser.parseResponse(parse("""
            {"output": [
                {"type": "message", "content": [{"type": "output_text", "text": "回复内容"}]},
                {"type": "reasoning", "content": [{"type": "reasoning_text", "text": "推理内容"}]}
            ]}
            """));

        assertNotNull(response);
        assertEquals("推理内容", response.getThought());
    }

    @Test
    @DisplayName("summary 为字符串时直接作为思考")
    void testParseCloudflareReasoningSummaryString() {
        AIResponse response = parser.parseResponse(parse("""
            {"output": [
                {"type": "message", "content": [{"type": "output_text", "text": "回复"}]},
                {"type": "reasoning", "summary": "字符串形式的思考"}
            ]}
            """));

        assertNotNull(response);
        assertEquals("字符串形式的思考", response.getThought());
    }

    @Test
    @DisplayName("message 中已有思考时 reasoning 项应被跳过")
    void testParseReasoningSkippedWhenThoughtExists() {
        AIResponse response = parser.parseResponse(parse("""
            {"output": [
                {"type": "message", "content": [
                    {"type": "output_text", "text": "回复内容"},
                    {"type": "thought", "text": "已有思考"}
                ]},
                {"type": "reasoning", "summary": "新思考"}
            ]}
            """));

        assertNotNull(response);
        assertEquals("已有思考", response.getThought());
    }

    // ======================== Cloudflare result 格式 ========================

    @Test
    @DisplayName("result.response 应解析为正文")
    void testParseCloudflareResultResponse() {
        AIResponse response = parser.parseResponse(parse("""
            {"result": {"response": "Cloudflare result 回复"}}
            """));

        assertNotNull(response);
        assertEquals("Cloudflare result 回复", response.getContent());
    }

    @Test
    @DisplayName("result 无 response 时回退到 text 字段")
    void testParseCloudflareResultText() {
        AIResponse response = parser.parseResponse(parse("""
            {"result": {"text": "使用 text 字段"}}
            """));

        assertNotNull(response);
        assertEquals("使用 text 字段", response.getContent());
    }

    @Test
    @DisplayName("result.reasoning / result.thought 应解析为思考")
    void testParseCloudflareResultThought() {
        AIResponse reasoning = parser.parseResponse(parse("""
            {"result": {"response": "回复", "reasoning": "思考"}}
            """));
        assertEquals("思考", reasoning.getThought());

        AIResponse thought = parser.parseResponse(parse("""
            {"result": {"response": "回复", "thought": "思考2"}}
            """));
        assertEquals("思考2", thought.getThought());
    }

    // ======================== finish_reason / 截断 ========================

    @Test
    @DisplayName("finish_reason=length 且有内容应标记截断")
    void testParseTruncatedWithContent() {
        AIResponse response = parser.parseResponse(parse("""
            {"choices": [{"message": {"content": "部分内容"}, "finish_reason": "length"}]}
            """));

        assertNotNull(response);
        assertTrue(response.isTruncated());
        assertEquals("部分内容", response.getContent());
    }

    @Test
    @DisplayName("finish_reason=length 且无内容应返回空串并标记截断")
    void testParseTruncatedWithoutContent() {
        AIResponse response = parser.parseResponse(parse("""
            {"choices": [{"message": {}, "finish_reason": "length"}]}
            """));

        assertNotNull(response);
        assertTrue(response.isTruncated());
        assertEquals("", response.getContent());
    }

    @Test
    @DisplayName("finish_reason=stop 但无内容应返回空串（内容过滤场景）")
    void testParseStopWithoutContent() {
        AIResponse response = parser.parseResponse(parse("""
            {"choices": [{"message": {}, "finish_reason": "stop"}]}
            """));

        assertNotNull(response);
        assertEquals("", response.getContent());
        assertFalse(response.isTruncated());
    }

    // ======================== usage token 解析 ========================

    @Test
    @DisplayName("OpenAI usage 应解析 token 数")
    void testParseOpenAiUsage() {
        AIResponse response = parser.parseResponse(parse("""
            {"choices": [{"message": {"content": "内容"}}], "usage": {"prompt_tokens": 10, "completion_tokens": 20}}
            """));

        assertNotNull(response);
        assertEquals(10, response.getPromptTokens());
        assertEquals(20, response.getCompletionTokens());
    }

    @Test
    @DisplayName("result.usage 应解析 token 数")
    void testParseCloudflareResultUsage() {
        AIResponse response = parser.parseResponse(parse("""
            {"result": {"response": "内容", "usage": {"prompt_tokens": 30, "completion_tokens": 40}}}
            """));

        assertNotNull(response);
        assertEquals(30, response.getPromptTokens());
        assertEquals(40, response.getCompletionTokens());
    }

    // ======================== 失败场景 ========================

    @Test
    @DisplayName("空 JSON 对象应返回 null")
    void testParseEmptyJson() {
        assertNull(parser.parseResponse(new JsonObject()));
    }

    @Test
    @DisplayName("choices 为空数组应返回 null")
    void testParseEmptyChoices() {
        assertNull(parser.parseResponse(parse("{\"choices\": []}")));
    }

    @Test
    @DisplayName("message.content 为 null 且无其他内容应返回 null")
    void testParseNullContent() {
        assertNull(parser.parseResponse(parse("{\"choices\": [{\"message\": {\"content\": null}}]}")));
    }

    @Test
    @DisplayName("choice 缺少 message 应返回 null")
    void testParseOpenAiNoMessage() {
        assertNull(parser.parseResponse(parse("{\"choices\": [{\"index\": 0}]}")));
    }

    @Test
    @DisplayName("choices/output 不是数组应返回 null")
    void testParseNonArray() {
        assertNull(parser.parseResponse(parse("{\"choices\": \"not an array\"}")));
        assertNull(parser.parseResponse(parse("{\"output\": \"not an array\"}")));
    }

    @Test
    @DisplayName("output message 无 content 数组应返回 null")
    void testParseOutputMessageNoContent() {
        assertNull(parser.parseResponse(parse("{\"output\": [{\"type\": \"message\"}]}")));
    }

    @Test
    @DisplayName("result 的 response/reasoning 均为 null 应返回 null")
    void testParseResultNullFields() {
        assertNull(parser.parseResponse(parse("{\"result\": {\"response\": null, \"reasoning\": null}}")));
    }

    @Test
    @DisplayName("多个 output 项应取第一个 message 的内容")
    void testParseMultipleOutputItems() {
        AIResponse response = parser.parseResponse(parse("""
            {"output": [
                {"type": "message", "content": [{"type": "output_text", "text": "回复1"}]},
                {"type": "message", "content": [{"type": "output_text", "text": "回复2"}]}
            ]}
            """));

        assertNotNull(response);
        assertEquals("回复1", response.getContent());
    }
}
