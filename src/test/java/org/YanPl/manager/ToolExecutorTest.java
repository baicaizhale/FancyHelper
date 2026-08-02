package org.YanPl.manager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ToolExecutor.parseToolCall 测试")
class ToolExecutorTest {

    @ParameterizedTest
    @CsvSource({
        "'#run:say hello', #run, say hello",
        "'#run say hello', #run, say hello",
        "'#run:say hello world', #run, say hello world",
        "'#run: say hello', #run, say hello",
        "'#diff:path/to/file|line1|new line1', #diff, path/to/file|line1|new line1"
    })
    @DisplayName("冒号或空格分隔应解析出工具名和参数")
    void testParseToolCall(String input, String expectedTool, String expectedArgs) {
        ToolExecutor.ToolParseResult result = ToolExecutor.parseToolCall(input);

        assertEquals(expectedTool, result.toolName);
        assertEquals(expectedArgs, result.args);
    }

    @Test
    @DisplayName("无分隔符应整体作为工具名，参数为空")
    void testParseToolCall_NoSeparator() {
        ToolExecutor.ToolParseResult result = ToolExecutor.parseToolCall("#end");

        assertEquals("#end", result.toolName);
        assertEquals("", result.args);
    }

    @Test
    @DisplayName("空字符串或纯空格应返回空工具名")
    void testParseToolCall_EmptyOrBlank() {
        ToolExecutor.ToolParseResult empty = ToolExecutor.parseToolCall("");
        assertEquals("", empty.toolName);
        assertEquals("", empty.args);

        ToolExecutor.ToolParseResult blank = ToolExecutor.parseToolCall("   ");
        assertEquals("", blank.toolName);
        assertEquals("", blank.args);
    }

    @Test
    @DisplayName("冒号在空格前应优先用冒号分割")
    void testParseToolCall_ColonPreferredOverSpace() {
        ToolExecutor.ToolParseResult result = ToolExecutor.parseToolCall("#run: say hello");

        assertEquals("#run", result.toolName);
        assertEquals("say hello", result.args);
    }

    @Test
    @DisplayName("工具名内嵌空格时应保留在工具名中")
    void testParseToolCall_SpaceInsideToolName() {
        ToolExecutor.ToolParseResult result = ToolExecutor.parseToolCall("#run say:hello");

        assertEquals("#run say", result.toolName);
        assertEquals("hello", result.args);
    }

    @Test
    @DisplayName("工具名两端空白应被裁剪")
    void testParseToolCall_TrimsWhitespace() {
        ToolExecutor.ToolParseResult result = ToolExecutor.parseToolCall("  #run  :  args  ");

        assertEquals("#run", result.toolName);
        assertEquals("args", result.args);
    }
}
