package org.YanPl.manager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CLIManager KNOWN_TOOLS 顺序测试")
class CLIManagerToolExtractionTest {

    /** 读取 CLIManager 的私有静态 KNOWN_TOOLS 常量，验证前缀冲突工具的长名在前。 */
    @SuppressWarnings("unchecked")
    private List<String> knownTools() throws Exception {
        Field f = CLIManager.class.getDeclaredField("KNOWN_TOOLS");
        f.setAccessible(true);
        return (List<String>) f.get(null);
    }

    @Test
    @DisplayName("#edit_memory 必须排在 #edit 之前")
    void testEditMemoryBeforeEdit() throws Exception {
        List<String> tools = knownTools();
        int editMemory = tools.indexOf("#edit_memory");
        int edit = tools.indexOf("#edit");
        assertTrue(editMemory != -1, "#edit_memory 应存在于列表中");
        assertTrue(edit != -1, "#edit 应存在于列表中");
        assertTrue(editMemory < edit, "#edit_memory 应排在 #edit 之前（否则被前缀劫持）");
    }

    @Test
    @DisplayName("#edit_global 排在 #edit 之前")
    void testEditGlobalBeforeEdit() throws Exception {
        List<String> tools = knownTools();
        assertTrue(tools.indexOf("#edit_global") < tools.indexOf("#edit"));
    }

    @Test
    @DisplayName("#remember_global / #forget_global 排在短名前")
    void testGlobalBeforeShort() throws Exception {
        List<String> tools = knownTools();
        assertTrue(tools.indexOf("#remember_global") < tools.indexOf("#remember"));
        assertTrue(tools.indexOf("#forget_global") < tools.indexOf("#forget"));
    }

    @Test
    @DisplayName("#mcp_tools 排在 #mcp 之前")
    void testMcpToolsBeforeMcp() throws Exception {
        List<String> tools = knownTools();
        assertTrue(tools.indexOf("#mcp_tools") < tools.indexOf("#mcp"));
    }

    @Test
    @DisplayName("前缀冲突长名全部前置：edit_memory 解析不再被 edit 劫持")
    void testEditMemoryNotHijacked() throws Exception {
        List<String> tools = knownTools();
        // 模拟 extractToolCall 的 startsWith 扫描：行首为 #edit_memory: ... 时应命中 #edit_memory
        String line = "#edit_memory: 2|style|concise";
        String matched = null;
        for (String tool : tools) {
            if (line.toLowerCase().startsWith(tool)) {
                matched = tool;
                break;
            }
        }
        assertEquals("#edit_memory", matched, "#edit_memory: 行应命中 #edit_memory 而非 #edit");
    }

    @Test
    @DisplayName("所有 KNOWN_TOOLS 均带 # 前缀且去重")
    void testAllToolsHaveHashAndUnique() throws Exception {
        List<String> tools = knownTools();
        assertFalse(tools.isEmpty());
        for (String t : tools) {
            assertTrue(t.startsWith("#"), "工具应以 # 开头: " + t);
        }
        assertEquals(tools.size(), tools.stream().distinct().count(), "不应有重复工具");
    }
}
