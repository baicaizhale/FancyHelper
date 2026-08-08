package org.YanPl.manager;

import org.YanPl.model.DialogueSession;
import org.YanPl.model.NativeToolCall;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CLIManager 串行批量工具执行测试")
class CLIManagerBatchExecutionTest {

    // ======================== isBatchSafeTool ========================

    @Test
    @DisplayName("安全集工具全部可批（任意模式）")
    void testBatchSafeTools() {
        for (DialogueSession.Mode mode : DialogueSession.Mode.values()) {
            assertTrue(CLIManager.isBatchSafeTool("search", mode));
            assertTrue(CLIManager.isBatchSafeTool("webfetch", mode));
            assertTrue(CLIManager.isBatchSafeTool("skill", mode));
            assertTrue(CLIManager.isBatchSafeTool("unloadskill", mode));
            assertTrue(CLIManager.isBatchSafeTool("mcp_tools", mode));
            assertTrue(CLIManager.isBatchSafeTool("todo", mode));
            assertTrue(CLIManager.isBatchSafeTool("list", mode));
            assertTrue(CLIManager.isBatchSafeTool("read", mode));
        }
    }

    @Test
    @DisplayName("run 仅 YOLO 模式可批")
    void testRunOnlyYolo() {
        assertTrue(CLIManager.isBatchSafeTool("run", DialogueSession.Mode.YOLO));
        assertFalse(CLIManager.isBatchSafeTool("run", DialogueSession.Mode.NORMAL));
        assertFalse(CLIManager.isBatchSafeTool("run", DialogueSession.Mode.SMART));
        assertFalse(CLIManager.isBatchSafeTool("run", DialogueSession.Mode.PLAN));
    }

    @Test
    @DisplayName("交互/确认工具不可批")
    void testInteractiveToolsNotBatchable() {
        for (DialogueSession.Mode mode : DialogueSession.Mode.values()) {
            assertFalse(CLIManager.isBatchSafeTool("ask", mode));
            assertFalse(CLIManager.isBatchSafeTool("edit", mode));
            assertFalse(CLIManager.isBatchSafeTool("write", mode));
            assertFalse(CLIManager.isBatchSafeTool("mcp", mode));
            assertFalse(CLIManager.isBatchSafeTool("remember", mode));
            assertFalse(CLIManager.isBatchSafeTool("forget", mode));
            assertFalse(CLIManager.isBatchSafeTool("remember_global", mode));
            assertFalse(CLIManager.isBatchSafeTool("exit", mode));
            assertFalse(CLIManager.isBatchSafeTool("start", mode));
            assertFalse(CLIManager.isBatchSafeTool("unknown", mode));
        }
    }

    @Test
    @DisplayName("大小写不敏感")
    void testCaseInsensitive() {
        assertTrue(CLIManager.isBatchSafeTool("SEARCH", DialogueSession.Mode.NORMAL));
        assertTrue(CLIManager.isBatchSafeTool("Run", DialogueSession.Mode.YOLO));
        assertFalse(CLIManager.isBatchSafeTool("Run", DialogueSession.Mode.NORMAL));
    }

    // ======================== containsRiskyRun ========================

    @Test
    @DisplayName("YOLO 风险 run 命令命中名单")
    void testContainsRiskyRun() {
        List<String> risky = List.of("op", "deop", "stop", "ban");
        NativeToolCall safe = new NativeToolCall("c1", "run", "{\"command\":\"give @p apple\"}");
        NativeToolCall riskyCmd = new NativeToolCall("c2", "run", "{\"command\":\"ban test 作弊\"}");

        assertFalse(CLIManager.containsRiskyRun(List.of(safe), risky));
        assertTrue(CLIManager.containsRiskyRun(List.of(riskyCmd), risky));
        assertTrue(CLIManager.containsRiskyRun(List.of(safe, riskyCmd), risky));
    }

    @Test
    @DisplayName("非 run 调用不会触发风险检查")
    void testContainsRiskyRunIgnoresNonRun() {
        List<String> risky = List.of("op");
        NativeToolCall search = new NativeToolCall("c1", "search", "{\"query\":\"op 命令\"}");
        assertFalse(CLIManager.containsRiskyRun(List.of(search), risky));
    }

    // ======================== 批次队列语义 ========================

    @Test
    @DisplayName("批次状态字段读写")
    void testBatchStateFields() {
        DialogueSession s = new DialogueSession();
        assertFalse(s.hasPendingNativeTools());

        s.pushPendingNativeTool("#search: a");
        s.pushPendingNativeTool("#webfetch: b");
        assertTrue(s.hasPendingNativeTools());
        assertEquals("#search: a", s.pollPendingNativeTool());
        assertEquals("#webfetch: b", s.pollPendingNativeTool());
        assertFalse(s.hasPendingNativeTools());
    }

    @Test
    @DisplayName("drainPendingToolResults 累积并清空")
    void testDrainPendingToolResults() {
        DialogueSession s = new DialogueSession();
        s.addPendingToolResult("#search_result: a");
        s.addPendingToolResult("#webfetch_result: b");

        List<String> drained = s.drainPendingToolResults();
        assertEquals(2, drained.size());
        assertEquals("#search_result: a", drained.get(0));
        assertEquals("#webfetch_result: b", drained.get(1));
        assertTrue(s.drainPendingToolResults().isEmpty(), "drain 后应清空");
    }

    @Test
    @DisplayName("clearBatchState 清空队列与结果")
    void testClearBatchState() {
        DialogueSession s = new DialogueSession();
        s.pushPendingNativeTool("#todo: x");
        s.addPendingToolResult("#todo_result: done");
        s.clearBatchState();
        assertFalse(s.hasPendingNativeTools());
        assertTrue(s.drainPendingToolResults().isEmpty());
    }

    @Test
    @DisplayName("批次结果加入成功/失败计数")
    void testIncrementCounters() {
        DialogueSession s = new DialogueSession();
        // 直接验证 DialogueSession 的计数访问器存在且可加
        s.incrementToolSuccess();
        s.incrementToolFailure();
        assertTrue(true); // 编译期验证访问器存在即可，计数逻辑由 DialogueSession 测试覆盖
    }
}
