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

    @Test
    @DisplayName("应提取异常链中最深层（根因）的真实报错")
    void testExtractRootCauseMessage_DeepestCause() {
        // 模拟 VanillaCommandWrapper 包装：外层 CommandException，内层 CommandSyntaxException
        Throwable root = new RuntimeException("Incorrect argument for command at position 9: gamerule <--[HERE]");
        Throwable wrapped = new RuntimeException("Unhandled exception executing 'gamerule keepInventory true' in VanillaCommandWrapper(gamerule)", root);

        assertEquals("Incorrect argument for command at position 9: gamerule <--[HERE]",
                ToolExecutor.extractRootCauseMessage(wrapped));
    }

    @Test
    @DisplayName("无 cause 时返回自身消息")
    void testExtractRootCauseMessage_SingleException() {
        Throwable t = new RuntimeException("命令语法错误");

        assertEquals("命令语法错误", ToolExecutor.extractRootCauseMessage(t));
    }

    @Test
    @DisplayName("根因消息为空时回退到最外层消息，避免返回 null")
    void testExtractRootCauseMessage_NullRootMessage() {
        Throwable root = new RuntimeException(); // 无消息
        Throwable wrapped = new RuntimeException("Unhandled exception executing ...", root);

        assertEquals("Unhandled exception executing ...", ToolExecutor.extractRootCauseMessage(wrapped));
    }

    @Test
    @DisplayName("控制台兜底：应抓到 issued server command 的下一句反馈")
    void testExtractConsoleFeedback_GrabsNextLine() {
        java.util.List<String> lines = java.util.List.of(
            "[01:57:20 INFO]: baicaizhale issued server command: /gamerule keep_inventory true",
            "[01:57:20 INFO]: [baicaizhale: Gamerule keep_inventory is now set to: true]"
        );

        assertEquals("[baicaizhale: Gamerule keep_inventory is now set to: true]",
                ToolExecutor.extractConsoleFeedback(lines, "baicaizhale", "gamerule keep_inventory true"));
    }

    @Test
    @DisplayName("控制台兜底：命令带前导斜杠时归一化后仍能匹配")
    void testExtractConsoleFeedback_CommandWithSlash() {
        java.util.List<String> lines = java.util.List.of(
            "[01:57:20 INFO]: baicaizhale issued server command: /gamerule keep_inventory true",
            "[01:57:20 INFO]: [baicaizhale: Gamerule keep_inventory is now set to: true]"
        );

        assertEquals("[baicaizhale: Gamerule keep_inventory is now set to: true]",
                ToolExecutor.extractConsoleFeedback(lines, "baicaizhale", "/gamerule keep_inventory true"));
    }

    @Test
    @DisplayName("控制台兜底：命令是更长命令的前缀时不应误匹配")
    void testExtractConsoleFeedback_PrefixCommandNoFalseMatch() {
        java.util.List<String> lines = java.util.List.of(
            "[01:57:20 INFO]: baicaizhale issued server command: /say hello world",
            "[01:57:20 INFO]: baicaizhale: Hello world"
        );

        // say hello 是 say hello world 的前缀，必须不匹配
        assertEquals("", ToolExecutor.extractConsoleFeedback(lines, "baicaizhale", "say hello"));
    }

    @Test
    @DisplayName("控制台兜底：找不到匹配的命令应返回空串")
    void testExtractConsoleFeedback_NoMatch() {
        java.util.List<String> lines = java.util.List.of(
            "[01:57:20 INFO]: baicaizhale issued server command: /say hello",
            "[01:57:20 INFO]: baicaizhale: Hello"
        );

        assertEquals("", ToolExecutor.extractConsoleFeedback(lines, "baicaizhale", "gamerule keep_inventory true"));
    }

    @Test
    @DisplayName("控制台兜底：其他玩家执行同一条命令不应匹配")
    void testExtractConsoleFeedback_OtherPlayer() {
        java.util.List<String> lines = java.util.List.of(
            "[01:57:20 INFO]: steve issued server command: /gamerule keep_inventory true",
            "[01:57:20 INFO]: [steve: Gamerule keep_inventory is now set to: true]"
        );

        assertEquals("", ToolExecutor.extractConsoleFeedback(lines, "baicaizhale", "gamerule keep_inventory true"));
    }

    @Test
    @DisplayName("控制台兜底：应取最后一次匹配，且多条反馈按顺序拼接")
    void testExtractConsoleFeedback_LatestMatchAndMultipleLines() {
        java.util.List<String> lines = java.util.List.of(
            "[01:50:00 INFO]: baicaizhale issued server command: /gamerule keep_inventory true",
            "[01:50:00 INFO]: [baicaizhale: Gamerule keep_inventory is now set to: true]",
            "[01:57:20 INFO]: baicaizhale issued server command: /gamerule keep_inventory true",
            "[01:57:20 INFO]: [baicaizhale: Gamerule keep_inventory is now set to: false]",
            "[01:57:21 INFO]: [baicaizhale: 另一条后续反馈]"
        );

        assertEquals("[baicaizhale: Gamerule keep_inventory is now set to: false]\n[baicaizhale: 另一条后续反馈]",
                ToolExecutor.extractConsoleFeedback(lines, "baicaizhale", "gamerule keep_inventory true"));
    }

    @Test
    @DisplayName("控制台兜底：遇到下一条 issued server command 应停止抓取")
    void testExtractConsoleFeedback_StopsAtNextCommand() {
        java.util.List<String> lines = java.util.List.of(
            "[01:57:20 INFO]: baicaizhale issued server command: /gamerule keep_inventory true",
            "[01:57:20 INFO]: [baicaizhale: Gamerule keep_inventory is now set to: true]",
            "[01:57:21 INFO]: steve issued server command: /say hi"
        );

        assertEquals("[baicaizhale: Gamerule keep_inventory is now set to: true]",
                ToolExecutor.extractConsoleFeedback(lines, "baicaizhale", "gamerule keep_inventory true"));
    }

    @Test
    @DisplayName("控制台兜底：空日志或空参数应返回空串")
    void testExtractConsoleFeedback_EmptyInput() {
        assertEquals("", ToolExecutor.extractConsoleFeedback(java.util.List.of(), "baicaizhale", "gamerule keep_inventory true"));
        assertEquals("", ToolExecutor.extractConsoleFeedback(null, "baicaizhale", "gamerule keep_inventory true"));
    }

    @Test
    @DisplayName("控制台兜底：stripLogPrefix 应去掉 [时间 级别]: 前缀")
    void testStripLogPrefix() {
        assertEquals("baicaizhale issued server command: /say hi",
                ToolExecutor.stripLogPrefix("[01:57:20 INFO]: baicaizhale issued server command: /say hi"));
        assertEquals("[baicaizhale: Gamerule keep_inventory is now set to: true]",
                ToolExecutor.stripLogPrefix("[01:57:20 INFO]: [baicaizhale: Gamerule keep_inventory is now set to: true]"));
        assertEquals("", ToolExecutor.stripLogPrefix(""));
        assertEquals("", ToolExecutor.stripLogPrefix(null));
        assertEquals("无前缀的文本", ToolExecutor.stripLogPrefix("无前缀的文本"));
    }

    @Test
    @DisplayName("gamerule 自动转换：camelCase 规则名应转为 snake_case")
    void testConvertGameruleCommand_CamelToSnake() {
        assertEquals("gamerule keep_inventory true",
                ToolExecutor.convertGameruleCommand("gamerule keepInventory true"));
        assertEquals("gamerule do_fire_tick false",
                ToolExecutor.convertGameruleCommand("gamerule doFireTick false"));
        assertEquals("gamerule send_command_feedback false",
                ToolExecutor.convertGameruleCommand("gamerule sendCommandFeedback false"));
    }

    @Test
    @DisplayName("gamerule 自动转换：带前导斜杠也应转换")
    void testConvertGameruleCommand_WithLeadingSlash() {
        assertEquals("/gamerule keep_inventory true",
                ToolExecutor.convertGameruleCommand("/gamerule keepInventory true"));
    }

    @Test
    @DisplayName("gamerule 自动转换：已是 snake_case 保持不变；无数值参数也转换规则名")
    void testConvertGameruleCommand_AlreadySnakeOrNoValue() {
        assertEquals("gamerule keep_inventory true",
                ToolExecutor.convertGameruleCommand("gamerule keep_inventory true"));
        // 已是 snake_case：原样返回
        assertEquals("gamerule keep_inventory",
                ToolExecutor.convertGameruleCommand("gamerule keep_inventory"));
        // 无数值参数：规则名仍应转换
        assertEquals("gamerule keep_inventory",
                ToolExecutor.convertGameruleCommand("gamerule keepInventory"));
    }

    @Test
    @DisplayName("gamerule 自动转换：非 gamerule 命令不应被改动")
    void testConvertGameruleCommand_NonGameruleUnchanged() {
        assertEquals("say hello", ToolExecutor.convertGameruleCommand("say hello"));
        assertEquals("/say hello", ToolExecutor.convertGameruleCommand("/say hello"));
        assertEquals("minecraft:time set day", ToolExecutor.convertGameruleCommand("minecraft:time set day"));
        assertEquals("", ToolExecutor.convertGameruleCommand(""));
        assertEquals(null, ToolExecutor.convertGameruleCommand(null));
    }

    @Test
    @DisplayName("gamerule 自动转换：camelToSnake 纯转换逻辑")
    void testCamelToSnake() {
        assertEquals("keep_inventory", ToolExecutor.camelToSnake("keepInventory"));
        assertEquals("do_fire_tick", ToolExecutor.camelToSnake("doFireTick"));
        assertEquals("random_tick_speed", ToolExecutor.camelToSnake("randomTickSpeed"));
        assertEquals("already_snake", ToolExecutor.camelToSnake("already_snake"));
        assertEquals("alllower", ToolExecutor.camelToSnake("alllower"));
        assertEquals("", ToolExecutor.camelToSnake(""));
        assertEquals("", ToolExecutor.camelToSnake(null));
    }

    @Test
    @DisplayName("版本比较：1.21.11 是 gamerule 改名阈值")
    void testCompareVersions() {
        // 等于阈值：应启用转换
        assertEquals(0, ToolExecutor.compareVersions("1.21.11", "1.21.11"));
        // 高于阈值：应启用转换
        assertTrue(ToolExecutor.compareVersions("1.21.12", "1.21.11") > 0);
        assertTrue(ToolExecutor.compareVersions("26.1", "1.21.11") > 0);
        assertTrue(ToolExecutor.compareVersions("1.22.0", "1.21.11") > 0);
        // 低于阈值：不应启用转换
        assertTrue(ToolExecutor.compareVersions("1.21.10", "1.21.11") < 0);
        assertTrue(ToolExecutor.compareVersions("1.20.4", "1.21.11") < 0);
        // 空串视为 0
        assertTrue(ToolExecutor.compareVersions("", "1.21.11") < 0);
    }
}
