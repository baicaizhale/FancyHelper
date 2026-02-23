package org.YanPl.manager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ToolExecutor.ToolParseResult 测试")
class ToolParseResultTest {

    @Test
    @DisplayName("构造函数应该正确设置属性")
    void testConstructor() {
        ToolExecutor.ToolParseResult result = new ToolExecutor.ToolParseResult("read", "/path/to/file");
        
        assertEquals("read", result.toolName);
        assertEquals("/path/to/file", result.args);
    }

    @Test
    @DisplayName("工具名和参数都可以为空")
    void testEmptyStrings() {
        ToolExecutor.ToolParseResult result = new ToolExecutor.ToolParseResult("", "");
        
        assertEquals("", result.toolName);
        assertEquals("", result.args);
    }

    @Test
    @DisplayName("工具名和参数都可以为 null")
    void testNullStrings() {
        ToolExecutor.ToolParseResult result = new ToolExecutor.ToolParseResult(null, null);
        
        assertNull(result.toolName);
        assertNull(result.args);
    }

    @Test
    @DisplayName("复杂参数字符串")
    void testComplexArgs() {
        String complexArgs = "{\"path\":\"/some/path\",\"recursive\":true}";
        ToolExecutor.ToolParseResult result = new ToolExecutor.ToolParseResult("ls", complexArgs);
        
        assertEquals("ls", result.toolName);
        assertEquals(complexArgs, result.args);
    }
}
