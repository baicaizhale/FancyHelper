package org.YanPl.manager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ToolExecutor.extractWebFetchUrl 测试")
class ToolExecutorWebFetchUrlTest {

    @Test
    @DisplayName("JSON 参数应提取 url 键")
    void testJsonArgs() {
        assertEquals("https://example.com/page",
                ToolExecutor.extractWebFetchUrl("{\"url\": \"https://example.com/page\"}"));
    }

    @Test
    @DisplayName("无空格紧凑 JSON 也应提取 url 键")
    void testCompactJsonArgs() {
        assertEquals("https://example.com",
                ToolExecutor.extractWebFetchUrl("{\"url\":\"https://example.com\"}"));
    }

    @Test
    @DisplayName("arguments 包裹层 JSON 也应提取 url 键")
    void testWrappedJsonArgs() {
        assertEquals("https://example.com",
                ToolExecutor.extractWebFetchUrl("{\"name\":\"webfetch\",\"arguments\":{\"url\":\"https://example.com\"}}"));
    }

    @Test
    @DisplayName("JSON 中带引号/括号包装的 URL 应清理")
    void testJsonUrlWithWrappers() {
        assertEquals("https://example.com/page",
                ToolExecutor.extractWebFetchUrl("{\"url\":\"`https://example.com/page`\"}"));
    }

    @Test
    @DisplayName("裸 URL 原样保留")
    void testBareUrl() {
        assertEquals("https://example.com/page?q=1", ToolExecutor.extractWebFetchUrl("https://example.com/page?q=1"));
    }

    @Test
    @DisplayName("裸 URL 带 Markdown 包装应清理")
    void testBareUrlWithWrappers() {
        assertEquals("https://example.com/page", ToolExecutor.extractWebFetchUrl("`https://example.com/page`"));
        assertEquals("https://example.com/page", ToolExecutor.extractWebFetchUrl("(https://example.com/page)"));
        assertEquals("https://example.com/page", ToolExecutor.extractWebFetchUrl("\"https://example.com/page\""));
    }

    @Test
    @DisplayName("非法 JSON 回退裸 URL 清理")
    void testInvalidJsonFallsBack() {
        assertEquals("{oops: not json", ToolExecutor.extractWebFetchUrl("{oops: not json"));
    }

    @Test
    @DisplayName("JSON 缺 url 键回退裸 URL 清理")
    void testJsonMissingUrlFallsBack() {
        assertEquals("{\"mode\":\"fast\"}", ToolExecutor.extractWebFetchUrl("{\"mode\":\"fast\"}"));
    }

    @Test
    @DisplayName("JSON 的 url 值为空时应返回空串（由调用方给出缺参反馈）")
    void testJsonEmptyUrl() {
        assertEquals("", ToolExecutor.extractWebFetchUrl("{\"url\":\"\"}"));
    }

    @Test
    @DisplayName("null 与空白参数返回空串")
    void testNullOrEmpty() {
        assertEquals("", ToolExecutor.extractWebFetchUrl(null));
        assertEquals("", ToolExecutor.extractWebFetchUrl("   "));
    }
}
