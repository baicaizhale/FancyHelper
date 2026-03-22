package org.YanPl.manager;

import org.YanPl.FancyHelper;
import org.YanPl.model.DialogueSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("ToolExecutor 单元测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ToolExecutorTest {

    @Mock
    private FancyHelper plugin;

    @Mock
    private CLIManager cliManager;

    @Mock
    private org.bukkit.entity.Player player;

    @Mock
    private ConfigManager configManager;

    private ToolExecutor toolExecutor;
    private UUID testUuid;

    @BeforeEach
    void setUp() {
        when(plugin.getLogger()).thenReturn(Logger.getLogger("TestLogger"));
        when(plugin.getConfigManager()).thenReturn(configManager);
        when(configManager.isDebug()).thenReturn(true);
        when(configManager.getApiTimeoutSeconds()).thenReturn(120);
        when(configManager.getSmartRiskThreshold()).thenReturn(50);
        
        toolExecutor = new ToolExecutor(plugin, cliManager);
        testUuid = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(testUuid);
        when(player.getName()).thenReturn("TestPlayer");
    }

    @Test
    @DisplayName("parseToolCall 带冒号分隔应正确解析")
    void testParseToolCall_WithColonSeparator_ParsesCorrectly() {
        ToolExecutor.ToolParseResult result = toolExecutor.parseToolCall("#run:say hello");

        assertEquals("#run", result.toolName);
        assertEquals("say hello", result.args);
    }

    @Test
    @DisplayName("parseToolCall 带空格分隔应正确解析")
    void testParseToolCall_WithSpaceSeparator_ParsesCorrectly() {
        ToolExecutor.ToolParseResult result = toolExecutor.parseToolCall("#run say hello");

        assertEquals("#run", result.toolName);
        assertEquals("say hello", result.args);
    }

    @Test
    @DisplayName("parseToolCall 冒号和空格都有应取最小索引")
    void testParseToolCall_BothColonAndSpace_UsesMinIndex() {
        ToolExecutor.ToolParseResult result = toolExecutor.parseToolCall("#run:say hello world");

        assertEquals("#run", result.toolName);
        assertEquals("say hello world", result.args);
    }

    @Test
    @DisplayName("parseToolCall 无分隔符应返回原字符串")
    void testParseToolCall_NoSeparator_ReturnsOriginal() {
        ToolExecutor.ToolParseResult result = toolExecutor.parseToolCall("#end");

        assertEquals("#end", result.toolName);
        assertEquals("", result.args);
    }

    @Test
    @DisplayName("parseToolCall 空字符串应返回空")
    void testParseToolCall_EmptyString_ReturnsEmpty() {
        ToolExecutor.ToolParseResult result = toolExecutor.parseToolCall("");

        assertEquals("", result.toolName);
        assertEquals("", result.args);
    }

    @Test
    @DisplayName("parseToolCall 仅空格应返回空")
    void testParseToolCall_OnlySpaces_ReturnsEmpty() {
        ToolExecutor.ToolParseResult result = toolExecutor.parseToolCall("   ");

        assertEquals("", result.toolName);
        assertEquals("", result.args);
    }

    @Test
    @DisplayName("parseToolCall 冒号在空格前应正确解析")
    void testParseToolCall_ColonBeforeSpace_UsesColonIndex() {
        ToolExecutor.ToolParseResult result = toolExecutor.parseToolCall("#run: say hello");

        assertEquals("#run", result.toolName);
        assertEquals("say hello", result.args);
    }

    @Test
    @DisplayName("parseToolCall 空格在冒号前应优先使用冒号")
    void testParseToolCall_SpaceBeforeColon_UsesColonIndex() {
        ToolExecutor.ToolParseResult result = toolExecutor.parseToolCall("#run say:hello");

        assertEquals("#run say", result.toolName);
        assertEquals("hello", result.args);
    }

    @Test
    @DisplayName("parseToolCall 多层嵌套参数应正确解析")
    void testParseToolCall_MultipleArgs_ParsesCorrectly() {
        ToolExecutor.ToolParseResult result = toolExecutor.parseToolCall("#diff:path/to/file|line1|new line1");

        assertEquals("#diff", result.toolName);
        assertEquals("path/to/file|line1|new line1", result.args);
    }

    @Test
    @DisplayName("executeTool 未知工具应返回 false")
    void testExecuteTool_UnknownTool_ReturnsFalse() {
        DialogueSession session = mock(DialogueSession.class);

        boolean result = toolExecutor.executeTool(player, "#unknown:arg", session);

        assertFalse(result);
        verify(player).sendMessage(contains("未知工具"));
    }

    @Test
    @DisplayName("executeTool #end 工具应正常执行")
    void testExecuteTool_EndTool_Executes() {
        DialogueSession session = mock(DialogueSession.class);

        boolean result = toolExecutor.executeTool(player, "#end", session);

        assertTrue(result);
        verify(cliManager).setGenerating(eq(testUuid), eq(false), eq(CLIManager.GenerationStatus.COMPLETED));
    }

    @Test
    @DisplayName("executeTool #exit 工具应正常执行")
    void testExecuteTool_ExitTool_Executes() {
        DialogueSession session = mock(DialogueSession.class);

        boolean result = toolExecutor.executeTool(player, "#exit", session);

        assertTrue(result);
        verify(cliManager).exitCLI(player);
    }

    @Test
    @DisplayName("executeTool #run 空参数应返回错误")
    void testExecuteTool_RunEmptyArgs_ReturnsError() {
        DialogueSession session = mock(DialogueSession.class);

        boolean result = toolExecutor.executeTool(player, "#run:", session);

        assertFalse(result);
        verify(player).sendMessage(contains("错误"));
    }

    @Test
    @DisplayName("executeTool 带 session 参数应为 null")
    void testExecuteTool_NullSession_HandlesCorrectly() {
        boolean result = toolExecutor.executeTool(player, "#end", null);

        assertTrue(result);
    }
}
