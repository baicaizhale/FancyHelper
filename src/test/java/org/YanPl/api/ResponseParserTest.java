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

    @Test
    @DisplayName("解析 OpenAI 格式响应")
    void testParseOpenAiFormat() {
        String json = """
            {
                "choices": [{
                    "message": {
                        "content": "这是回复内容"
                    }
                }]
            }
            """;
        
        JsonObject responseJson = gson.fromJson(json, JsonObject.class);
        AIResponse response = parser.parseResponse(responseJson);
        
        assertNotNull(response);
        assertEquals("这是回复内容", response.getContent());
    }

    @Test
    @DisplayName("解析 OpenAI 格式带思考内容")
    void testParseOpenAiFormatWithThought() {
        String json = """
            {
                "choices": [{
                    "message": {
                        "content": "回复内容",
                        "reasoning_content": "思考过程"
                    }
                }]
            }
            """;
        
        JsonObject responseJson = gson.fromJson(json, JsonObject.class);
        AIResponse response = parser.parseResponse(responseJson);
        
        assertNotNull(response);
        assertEquals("回复内容", response.getContent());
        assertEquals("思考过程", response.getThought());
    }

    @Test
    @DisplayName("解析 OpenAI 格式空 choices 数组返回 null")
    void testParseOpenAiFormatEmptyChoices() {
        String json = """
            {
                "choices": []
            }
            """;
        
        JsonObject responseJson = gson.fromJson(json, JsonObject.class);
        AIResponse response = parser.parseResponse(responseJson);
        
        assertNull(response);
    }

    @Test
    @DisplayName("解析 Cloudflare output 格式")
    void testParseCloudFlareOutputFormat() {
        String json = """
            {
                "output": [{
                    "type": "message",
                    "content": [{
                        "type": "output_text",
                        "text": "Cloudflare 回复"
                    }]
                }]
            }
            """;
        
        JsonObject responseJson = gson.fromJson(json, JsonObject.class);
        AIResponse response = parser.parseResponse(responseJson);
        
        assertNotNull(response);
        assertEquals("Cloudflare 回复", response.getContent());
    }

    @Test
    @DisplayName("解析 Cloudflare output 格式带思考")
    void testParseCloudFlareOutputFormatWithThought() {
        String json = """
            {
                "output": [{
                    "type": "message",
                    "content": [{
                        "type": "output_text",
                        "text": "回复内容"
                    }, {
                        "type": "thought",
                        "text": "思考内容"
                    }]
                }]
            }
            """;
        
        JsonObject responseJson = gson.fromJson(json, JsonObject.class);
        AIResponse response = parser.parseResponse(responseJson);
        
        assertNotNull(response);
        assertEquals("回复内容", response.getContent());
        assertEquals("思考内容", response.getThought());
    }

    @Test
    @DisplayName("解析 Cloudflare output reasoning 类型")
    void testParseCloudFlareOutputReasoningType() {
        String json = """
            {
                "output": [{
                    "type": "message",
                    "content": [{
                        "type": "output_text",
                        "text": "回复内容"
                    }]
                }, {
                    "type": "reasoning",
                    "summary": [{
                        "text": "思考总结1"
                    }, {
                        "text": "思考总结2"
                    }]
                }]
            }
            """;
        
        JsonObject responseJson = gson.fromJson(json, JsonObject.class);
        AIResponse response = parser.parseResponse(responseJson);
        
        assertNotNull(response);
        assertEquals("回复内容", response.getContent());
        assertTrue(response.getThought().contains("思考总结1"));
        assertTrue(response.getThought().contains("思考总结2"));
    }

    @Test
    @DisplayName("解析 Cloudflare output reasoning 带 content 数组")
    void testParseCloudFlareOutputReasoningContent() {
        String json = """
            {
                "output": [{
                    "type": "message",
                    "content": [{
                        "type": "output_text",
                        "text": "回复内容"
                    }]
                }, {
                    "type": "reasoning",
                    "content": [{
                        "type": "reasoning_text",
                        "text": "推理内容"
                    }]
                }]
            }
            """;
        
        JsonObject responseJson = gson.fromJson(json, JsonObject.class);
        AIResponse response = parser.parseResponse(responseJson);
        
        assertNotNull(response);
        assertEquals("回复内容", response.getContent());
        assertEquals("推理内容", response.getThought());
    }

    @Test
    @DisplayName("解析 Cloudflare result 格式")
    void testParseCloudFlareResultFormat() {
        String json = """
            {
                "result": {
                    "response": "Cloudflare result 回复"
                }
            }
            """;
        
        JsonObject responseJson = gson.fromJson(json, JsonObject.class);
        AIResponse response = parser.parseResponse(responseJson);
        
        assertNotNull(response);
        assertEquals("Cloudflare result 回复", response.getContent());
    }

    @Test
    @DisplayName("解析 Cloudflare result 格式带思考")
    void testParseCloudFlareResultFormatWithThought() {
        String json = """
            {
                "result": {
                    "response": "回复内容",
                    "reasoning": "思考内容"
                }
            }
            """;
        
        JsonObject responseJson = gson.fromJson(json, JsonObject.class);
        AIResponse response = parser.parseResponse(responseJson);
        
        assertNotNull(response);
        assertEquals("回复内容", response.getContent());
        assertEquals("思考内容", response.getThought());
    }

    @Test
    @DisplayName("解析 Cloudflare result 使用 text 字段")
    void testParseCloudFlareResultText() {
        String json = """
            {
                "result": {
                    "text": "使用 text 字段"
                }
            }
            """;
        
        JsonObject responseJson = gson.fromJson(json, JsonObject.class);
        AIResponse response = parser.parseResponse(responseJson);
        
        assertNotNull(response);
        assertEquals("使用 text 字段", response.getContent());
    }

    @Test
    @DisplayName("解析 Cloudflare result 使用 thought 字段")
    void testParseCloudFlareResultThought() {
        String json = """
            {
                "result": {
                    "response": "回复",
                    "thought": "思考"
                }
            }
            """;
        
        JsonObject responseJson = gson.fromJson(json, JsonObject.class);
        AIResponse response = parser.parseResponse(responseJson);
        
        assertNotNull(response);
        assertEquals("思考", response.getThought());
    }

    @Test
    @DisplayName("空 JSON 对象返回 null")
    void testParseEmptyJson() {
        JsonObject responseJson = new JsonObject();
        AIResponse response = parser.parseResponse(responseJson);
        
        assertNull(response);
    }

    @Test
    @DisplayName("null content 处理")
    void testParseNullContent() {
        String json = """
            {
                "choices": [{
                    "message": {
                        "content": null
                    }
                }]
            }
            """;
        
        JsonObject responseJson = gson.fromJson(json, JsonObject.class);
        AIResponse response = parser.parseResponse(responseJson);
        
        assertNull(response);
    }

    @Test
    @DisplayName("OpenAI 格式缺少 message 字段返回 null")
    void testParseOpenAiNoMessage() {
        String json = """
            {
                "choices": [{
                    "index": 0
                }]
            }
            """;
        
        JsonObject responseJson = gson.fromJson(json, JsonObject.class);
        AIResponse response = parser.parseResponse(responseJson);
        
        assertNull(response);
    }

    @Test
    @DisplayName("choices 不是数组返回 null")
    void testParseChoicesNotArray() {
        String json = """
            {
                "choices": "not an array"
            }
            """;
        
        JsonObject responseJson = gson.fromJson(json, JsonObject.class);
        AIResponse response = parser.parseResponse(responseJson);
        
        assertNull(response);
    }

    @Test
    @DisplayName("output 不是数组返回 null")
    void testParseOutputNotArray() {
        String json = """
            {
                "output": "not an array"
            }
            """;
        
        JsonObject responseJson = gson.fromJson(json, JsonObject.class);
        AIResponse response = parser.parseResponse(responseJson);
        
        assertNull(response);
    }

    @Test
    @DisplayName("Cloudflare output message 无 content 数组")
    void testParseCloudFlareOutputNoContentArray() {
        String json = """
            {
                "output": [{
                    "type": "message"
                }]
            }
            """;
        
        JsonObject responseJson = gson.fromJson(json, JsonObject.class);
        AIResponse response = parser.parseResponse(responseJson);
        
        assertNull(response);
    }

    @Test
    @DisplayName("summary 是字符串")
    void testParseSummaryAsString() {
        String json = """
            {
                "output": [{
                    "type": "message",
                    "content": [{
                        "type": "output_text",
                        "text": "回复"
                    }]
                }, {
                    "type": "reasoning",
                    "summary": "字符串形式的思考"
                }]
            }
            """;
        
        JsonObject responseJson = gson.fromJson(json, JsonObject.class);
        AIResponse response = parser.parseResponse(responseJson);
        
        assertNotNull(response);
        assertEquals("字符串形式的思考", response.getThought());
    }

    @Test
    @DisplayName("output_text 的 text 是 null")
    void testParseOutputTextNull() {
        String json = """
            {
                "output": [{
                    "type": "message",
                    "content": [{
                        "type": "output_text",
                        "text": null
                    }]
                }]
            }
            """;
        
        JsonObject responseJson = gson.fromJson(json, JsonObject.class);
        AIResponse response = parser.parseResponse(responseJson);
        
        assertNull(response);
    }

    @Test
    @DisplayName("reasoning_content 是 null")
    void testParseReasoningContentNull() {
        String json = """
            {
                "choices": [{
                    "message": {
                        "content": "回复",
                        "reasoning_content": null
                    }
                }]
            }
            """;
        
        JsonObject responseJson = gson.fromJson(json, JsonObject.class);
        AIResponse response = parser.parseResponse(responseJson);
        
        assertNotNull(response);
        assertEquals("回复", response.getContent());
        assertFalse(response.hasThought());
    }

    @Test
    @DisplayName("result 的 response 和 reasoning 是 null")
    void testParseResultNullFields() {
        String json = """
            {
                "result": {
                    "response": null,
                    "reasoning": null
                }
            }
            """;
        
        JsonObject responseJson = gson.fromJson(json, JsonObject.class);
        AIResponse response = parser.parseResponse(responseJson);
        
        assertNull(response);
    }

    @Test
    @DisplayName("多个 output 项")
    void testParseMultipleOutputItems() {
        String json = """
            {
                "output": [{
                    "type": "message",
                    "content": [{
                        "type": "output_text",
                        "text": "回复1"
                    }]
                }, {
                    "type": "message",
                    "content": [{
                        "type": "output_text",
                        "text": "回复2"
                    }]
                }]
            }
            """;
        
        JsonObject responseJson = gson.fromJson(json, JsonObject.class);
        AIResponse response = parser.parseResponse(responseJson);
        
        assertNotNull(response);
        assertEquals("回复1", response.getContent());
    }

    @Test
    @DisplayName("reasoning 类型已有思考则跳过")
    void testParseReasoningSkipIfHasThought() {
        String json = """
            {
                "output": [{
                    "type": "message",
                    "content": [{
                        "type": "output_text",
                        "text": "回复内容"
                    }, {
                        "type": "thought",
                        "text": "已有思考"
                    }]
                }, {
                    "type": "reasoning",
                    "summary": "新思考"
                }]
            }
            """;
        
        JsonObject responseJson = gson.fromJson(json, JsonObject.class);
        AIResponse response = parser.parseResponse(responseJson);
        
        assertNotNull(response);
        assertEquals("回复内容", response.getContent());
        assertEquals("已有思考", response.getThought());
    }

    @Test
    @DisplayName("reasoning content 多个 reasoning_text")
    void testParseMultipleReasoningText() {
        String json = """
            {
                "output": [{
                    "type": "message",
                    "content": [{
                        "type": "output_text",
                        "text": "回复"
                    }]
                }, {
                    "type": "reasoning",
                    "content": [{
                        "type": "reasoning_text",
                        "text": "推理1"
                    }, {
                        "type": "reasoning_text",
                        "text": "推理2"
                    }]
                }]
            }
            """;
        
        JsonObject responseJson = gson.fromJson(json, JsonObject.class);
        AIResponse response = parser.parseResponse(responseJson);
        
        assertNotNull(response);
        assertTrue(response.getThought().contains("推理1"));
        assertTrue(response.getThought().contains("推理2"));
    }

    @Test
    @DisplayName("output 中有未知类型的项")
    void testParseOutputUnknownType() {
        String json = """
            {
                "output": [{
                    "type": "message",
                    "content": [{
                        "type": "output_text",
                        "text": "回复"
                    }]
                }, {
                    "type": "unknown_type"
                }]
            }
            """;
        
        JsonObject responseJson = gson.fromJson(json, JsonObject.class);
        AIResponse response = parser.parseResponse(responseJson);
        
        assertNotNull(response);
        assertEquals("回复", response.getContent());
    }

    @Test
    @DisplayName("output item 没有 type 字段")
    void testParseOutputNoType() {
        String json = """
            {
                "output": [{
                    "type": "message",
                    "content": [{
                        "type": "output_text",
                        "text": "回复"
                    }]
                }, {
                    "some_field": "some_value"
                }]
            }
            """;
        
        JsonObject responseJson = gson.fromJson(json, JsonObject.class);
        AIResponse response = parser.parseResponse(responseJson);
        
        assertNotNull(response);
        assertEquals("回复", response.getContent());
    }

    @Test
    @DisplayName("reasoning content 中有非 reasoning_text 类型")
    void testParseReasoningContentNonReasoningText() {
        String json = """
            {
                "output": [{
                    "type": "message",
                    "content": [{
                        "type": "output_text",
                        "text": "回复"
                    }]
                }, {
                    "type": "reasoning",
                    "content": [{
                        "type": "other_type",
                        "text": "其他内容"
                    }, {
                        "type": "reasoning_text",
                        "text": "推理内容"
                    }]
                }]
            }
            """;
        
        JsonObject responseJson = gson.fromJson(json, JsonObject.class);
        AIResponse response = parser.parseResponse(responseJson);
        
        assertNotNull(response);
        assertEquals("推理内容", response.getThought());
    }

    @Test
    @DisplayName("reasoning content 中 text 为 null")
    void testParseReasoningContentTextNull() {
        String json = """
            {
                "output": [{
                    "type": "message",
                    "content": [{
                        "type": "output_text",
                        "text": "回复"
                    }]
                }, {
                    "type": "reasoning",
                    "content": [{
                        "type": "reasoning_text",
                        "text": null
                    }]
                }]
            }
            """;
        
        JsonObject responseJson = gson.fromJson(json, JsonObject.class);
        AIResponse response = parser.parseResponse(responseJson);
        
        assertNotNull(response);
        assertEquals("回复", response.getContent());
        assertNull(response.getThought());
    }

    @Test
    @DisplayName("reasoning content 中缺少 text 字段")
    void testParseReasoningContentNoText() {
        String json = """
            {
                "output": [{
                    "type": "message",
                    "content": [{
                        "type": "output_text",
                        "text": "回复"
                    }]
                }, {
                    "type": "reasoning",
                    "content": [{
                        "type": "reasoning_text"
                    }]
                }]
            }
            """;
        
        JsonObject responseJson = gson.fromJson(json, JsonObject.class);
        AIResponse response = parser.parseResponse(responseJson);
        
        assertNotNull(response);
        assertEquals("回复", response.getContent());
    }

    @Test
    @DisplayName("summary 数组中有 text 为 null 的项")
    void testParseSummaryArrayWithNullText() {
        String json = """
            {
                "output": [{
                    "type": "message",
                    "content": [{
                        "type": "output_text",
                        "text": "回复"
                    }]
                }, {
                    "type": "reasoning",
                    "summary": [{
                        "text": "思考1"
                    }, {
                        "text": null
                    }, {
                        "text": "思考2"
                    }]
                }]
            }
            """;
        
        JsonObject responseJson = gson.fromJson(json, JsonObject.class);
        AIResponse response = parser.parseResponse(responseJson);
        
        assertNotNull(response);
        assertTrue(response.getThought().contains("思考1"));
        assertTrue(response.getThought().contains("思考2"));
    }

    @Test
    @DisplayName("reasoning 既没有 summary 也没有 content")
    void testParseReasoningNoSummaryOrContent() {
        String json = """
            {
                "output": [{
                    "type": "message",
                    "content": [{
                        "type": "output_text",
                        "text": "回复"
                    }]
                }, {
                    "type": "reasoning"
                }]
            }
            """;
        
        JsonObject responseJson = gson.fromJson(json, JsonObject.class);
        AIResponse response = parser.parseResponse(responseJson);
        
        assertNotNull(response);
        assertEquals("回复", response.getContent());
    }

    @Test
    @DisplayName("reasoning 有 summary 但不是数组也不是字符串")
    void testParseReasoningSummaryObject() {
        String json = """
            {
                "output": [{
                    "type": "message",
                    "content": [{
                        "type": "output_text",
                        "text": "回复"
                    }]
                }, {
                    "type": "reasoning",
                    "summary": {
                        "nested": "object"
                    }
                }]
            }
            """;
        
        JsonObject responseJson = gson.fromJson(json, JsonObject.class);
        AIResponse response = parser.parseResponse(responseJson);
        
        assertNotNull(response);
        assertEquals("回复", response.getContent());
    }

    @Test
    @DisplayName("Cloudflare output reasoning content 不是数组")
    void testParseReasoningContentNotArray() {
        String json = """
            {
                "output": [{
                    "type": "message",
                    "content": [{
                        "type": "output_text",
                        "text": "回复"
                    }]
                }, {
                    "type": "reasoning",
                    "content": "not an array"
                }]
            }
            """;
        
        JsonObject responseJson = gson.fromJson(json, JsonObject.class);
        AIResponse response = parser.parseResponse(responseJson);
        
        assertNotNull(response);
        assertEquals("回复", response.getContent());
    }

    @Test
    @DisplayName("reasoning content 为空数组")
    void testParseReasoningContentEmptyArray() {
        String json = """
            {
                "output": [{
                    "type": "message",
                    "content": [{
                        "type": "output_text",
                        "text": "回复"
                    }]
                }, {
                    "type": "reasoning",
                    "content": []
                }]
            }
            """;
        
        JsonObject responseJson = gson.fromJson(json, JsonObject.class);
        AIResponse response = parser.parseResponse(responseJson);
        
        assertNotNull(response);
        assertEquals("回复", response.getContent());
    }

    @Test
    @DisplayName("message content 中有 reasoning 类型")
    void testParseMessageContentReasoningType() {
        String json = """
            {
                "output": [{
                    "type": "message",
                    "content": [{
                        "type": "output_text",
                        "text": "回复"
                    }, {
                        "type": "reasoning",
                        "text": "推理内容"
                    }]
                }]
            }
            """;
        
        JsonObject responseJson = gson.fromJson(json, JsonObject.class);
        AIResponse response = parser.parseResponse(responseJson);
        
        assertNotNull(response);
        assertEquals("回复", response.getContent());
        assertEquals("推理内容", response.getThought());
    }

    @Test
    @DisplayName("OpenAI 格式 message 中有 null 字段")
    void testParseOpenAiMessageNullFields() {
        String json = """
            {
                "choices": [{
                    "message": {}
                }]
            }
            """;
        
        JsonObject responseJson = gson.fromJson(json, JsonObject.class);
        AIResponse response = parser.parseResponse(responseJson);
        
        assertNull(response);
    }
}
