package org.YanPl.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AIResponse 单元测试
 */
@DisplayName("AIResponse 测试")
class AIResponseTest {

    @Test
    @DisplayName("构造函数应该正确设置 content 和 thought")
    void testConstructor() {
        AIResponse response = new AIResponse("响应内容", "思考过程");
        assertEquals("响应内容", response.getContent());
        assertEquals("思考过程", response.getThought());
    }

    @Test
    @DisplayName("hasThought 应该对非空思考返回 true")
    void testHasThoughtWithContent() {
        AIResponse response = new AIResponse("内容", "这是思考过程");
        assertTrue(response.hasThought());
    }

    @Test
    @DisplayName("hasThought 应该对 null 思考返回 false")
    void testHasThoughtWithNull() {
        AIResponse response = new AIResponse("内容", null);
        assertFalse(response.hasThought());
    }

    @Test
    @DisplayName("hasThought 应该对空字符串思考返回 false")
    void testHasThoughtWithEmpty() {
        AIResponse response = new AIResponse("内容", "");
        assertFalse(response.hasThought());
    }

    @Test
    @DisplayName("content 可以为 null")
    void testNullContent() {
        AIResponse response = new AIResponse(null, "思考");
        assertNull(response.getContent());
        assertTrue(response.hasThought());
    }

    @Test
    @DisplayName("content 和 thought 都可以为 null")
    void testBothNull() {
        AIResponse response = new AIResponse(null, null);
        assertNull(response.getContent());
        assertNull(response.getThought());
        assertFalse(response.hasThought());
    }
}
