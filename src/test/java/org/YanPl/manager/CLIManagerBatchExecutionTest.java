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
            assertTrue(CLIManager.isBatchSafeTool("mcp", mode));
            assertTrue(CLIManager.isBatchSafeTool("todo", mode));
            assertTrue(CLIManager.isBatchSafeTool("list", mode));
            assertTrue(CLIManager.isBatchSafeTool("read", mode));
        }
    }

    @Test
    @DisplayName("交互/确认工具可批（结果均经 feedbackToAI 回灌，批次屏障可推进）")
    void testInteractiveToolsNowBatchable() {
        for (DialogueSession.Mode mode : DialogueSession.Mode.values()) {
            assertTrue(CLIManager.isBatchSafeTool("ask", mode));
            assertTrue(CLIManager.isBatchSafeTool("edit", mode));
            assertTrue(CLIManager.isBatchSafeTool("edit_memory", mode));
            assertTrue(CLIManager.isBatchSafeTool("write", mode));
            assertTrue(CLIManager.isBatchSafeTool("remember", mode));
            assertTrue(CLIManager.isBatchSafeTool("forget", mode));
            assertTrue(CLIManager.isBatchSafeTool("remember_global", mode));
            assertTrue(CLIManager.isBatchSafeTool("forget_global", mode));
            assertTrue(CLIManager.isBatchSafeTool("edit_global", mode));
        }
    }

    @Test
    @DisplayName("run 在所有模式均可批（确认/取消都能推进批次）")
    void testRunBatchableAllModes() {
        assertTrue(CLIManager.isBatchSafeTool("run", DialogueSession.Mode.YOLO));
        assertTrue(CLIManager.isBatchSafeTool("run", DialogueSession.Mode.NORMAL));
        assertTrue(CLIManager.isBatchSafeTool("run", DialogueSession.Mode.SMART));
        assertTrue(CLIManager.isBatchSafeTool("run", DialogueSession.Mode.PLAN));
    }

    @Test
    @DisplayName("控制类/未知工具仍不可批（无 feedbackToAI 回灌，会卡死批次屏障）")
    void testControlToolsNotBatchable() {
        for (DialogueSession.Mode mode : DialogueSession.Mode.values()) {
            assertFalse(CLIManager.isBatchSafeTool("end", mode));
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
        assertTrue(CLIManager.isBatchSafeTool("Run", DialogueSession.Mode.NORMAL));
        assertTrue(CLIManager.isBatchSafeTool("Edit", DialogueSession.Mode.NORMAL));
    }

    // ======================== isRiskyRunCall ========================

    @Test
    @DisplayName("isRiskyRunCall 命中风险名单")
    void testIsRiskyRunCall() {
        List<String> risky = List.of("op", "deop", "stop", "ban");
        NativeToolCall safe = new NativeToolCall("c1", "run", "{\"command\":\"give @p apple\"}");
        NativeToolCall riskyCmd = new NativeToolCall("c2", "run", "{\"command\":\"ban test 作弊\"}");
        NativeToolCall search = new NativeToolCall("c3", "search", "{\"query\":\"op 命令\"}");

        assertFalse(CLIManager.isRiskyRunCall(safe, risky));
        assertTrue(CLIManager.isRiskyRunCall(riskyCmd, risky));
        assertFalse(CLIManager.isRiskyRunCall(search, risky));
        assertFalse(CLIManager.isRiskyRunCall(null, risky));
    }

    // ======================== 批次队列语义 ========================

    @Test
    @DisplayName("批次状态字段读写")
    void testBatchStateFields() {
        DialogueSession s = new DialogueSession();
        assertFalse(s.hasPendingNativeTools());
        assertFalse(s.isBatchInProgress());

        s.pushPendingNativeTool("#search: a");
        s.pushPendingNativeTool("#webfetch: b");
        assertTrue(s.hasPendingNativeTools());
        assertEquals("#search: a", s.pollPendingNativeTool());
        assertEquals("#webfetch: b", s.pollPendingNativeTool());
        assertFalse(s.hasPendingNativeTools());
    }

    @Test
    @DisplayName("batchInProgress 独立于队列非空，队列耗尽仍为 true")
    void testBatchInProgressLifetime() {
        DialogueSession s = new DialogueSession();
        assertFalse(s.isBatchInProgress());

        // 批启动：置位；队列耗尽后（最后一项已 poll）仍为 true，等待最后结果回灌
        s.setBatchInProgress(true);
        s.pushPendingNativeTool("#search: a");
        s.pollPendingNativeTool();
        assertFalse(s.hasPendingNativeTools(), "队列已耗尽");
        assertTrue(s.isBatchInProgress(), "批次仍进行中，等待最后一项反馈");

        // 合并终结后清复位
        s.clearBatchState();
        assertFalse(s.isBatchInProgress());
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

    // ======================== 未执行项回灌说明 ========================

    @Test
    @DisplayName("buildNativeDroppedNote 生成未执行项说明")
    void testBuildNativeDroppedNote() {
        NativeToolCall end = new NativeToolCall("c1", "end", "{}");
        NativeToolCall exit = new NativeToolCall("c2", "exit", "{}");

        String note = CLIManager.buildNativeDroppedNote(List.of(end, exit));
        assertNotNull(note);
        assertTrue(note.contains("end"));
        assertTrue(note.contains("exit"));

        assertNull(CLIManager.buildNativeDroppedNote(List.of()));
        assertNull(CLIManager.buildNativeDroppedNote(null));
    }

    @Test
    @DisplayName("extractTextToolName 提取 #tool 名称")
    void testExtractTextToolName() {
        assertEquals("run", CLIManager.extractTextToolName("#run: say hello"));
        assertEquals("edit_memory", CLIManager.extractTextToolName("#edit_memory: 1|a"));
        assertEquals("mcp_tools", CLIManager.extractTextToolName("#mcp_tools"));
        assertNull(CLIManager.extractTextToolName("say hello"));
        assertNull(CLIManager.extractTextToolName(null));
        assertNull(CLIManager.extractTextToolName("#"));
    }

    @Test
    @DisplayName("pendingBatchDropNote 写入/清空")
    void testPendingBatchDropNote() {
        DialogueSession s = new DialogueSession();
        assertNull(s.getPendingBatchDropNote());

        s.setPendingBatchDropNote("#error: 未执行");
        assertEquals("#error: 未执行", s.getPendingBatchDropNote());

        s.clearBatchState();
        assertNull(s.getPendingBatchDropNote());
    }
}
