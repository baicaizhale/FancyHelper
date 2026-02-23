package org.YanPl.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DialogueSession 测试")
class DialogueSessionTest {

    private DialogueSession session;

    @BeforeEach
    void setUp() {
        session = new DialogueSession();
    }

    @Test
    @DisplayName("Total Input/Output Tokens 应该正确累加")
    void testTotalTokens() {
        assertEquals(0, session.getTotalInputTokens());
        assertEquals(0, session.getTotalOutputTokens());
        
        session.addInputTokens(100);
        assertEquals(100, session.getTotalInputTokens());
        
        session.addOutputTokens(50);
        assertEquals(50, session.getTotalOutputTokens());
        
        session.addInputTokens(200);
        assertEquals(300, session.getTotalInputTokens());
        
        session.addOutputTokens(150);
        assertEquals(200, session.getTotalOutputTokens());
    }

    @Test
    @DisplayName("默认构造函数应该初始化正确的默认值")
    void testDefaultConstructor() {
        assertNotNull(session.getHistory());
        assertTrue(session.getHistory().isEmpty());
        assertEquals(DialogueSession.Mode.NORMAL, session.getMode());
        assertFalse(session.isAntiLoopExempted());
        assertEquals(0, session.getToolSuccessCount());
        assertEquals(0, session.getToolFailureCount());
        assertEquals(0, session.getCurrentChainToolCount());
        assertNull(session.getLastThought());
        assertNull(session.getLogFilePath());
    }

    @Test
    @DisplayName("addMessage 应该正确添加消息")
    void testAddMessage() {
        session.addMessage("user", "你好");
        
        assertEquals(1, session.getHistory().size());
        DialogueSession.Message msg = session.getHistory().get(0);
        assertEquals("user", msg.getRole());
        assertEquals("你好", msg.getContent());
    }

    @Test
    @DisplayName("addMessage 带思考内容应该正确添加")
    void testAddMessageWithThought() {
        session.addMessage("assistant", "回复内容", "思考过程");
        
        assertEquals(1, session.getHistory().size());
        DialogueSession.Message msg = session.getHistory().get(0);
        assertEquals("assistant", msg.getRole());
        assertEquals("回复内容", msg.getContent());
        assertEquals("思考过程", msg.getThought());
        assertTrue(msg.hasThought());
    }

    @Test
    @DisplayName("getEstimatedTokens 应该返回正确的 token 数")
    void testGetEstimatedTokens() {
        session.addMessage("user", "Hello World");
        int tokens = session.getEstimatedTokens();
        assertTrue(tokens > 0);
    }

    @Test
    @DisplayName("updateActivity 应该更新活动时间")
    void testUpdateActivity() throws InterruptedException {
        long initialTime = session.getLastActivityTime();
        Thread.sleep(10);
        session.updateActivity();
        assertTrue(session.getLastActivityTime() > initialTime);
    }

    @Test
    @DisplayName("clearHistory 应该清空历史")
    void testClearHistory() {
        session.addMessage("user", "消息1");
        session.addMessage("assistant", "消息2");
        session.addToolCall("tool1");
        
        session.clearHistory();
        
        assertTrue(session.getHistory().isEmpty());
        assertTrue(session.getToolCallHistory().isEmpty());
    }

    @Test
    @DisplayName("工具计数器应该正确工作")
    void testToolCounters() {
        session.incrementToolSuccess();
        session.incrementToolSuccess();
        session.incrementToolFailure();
        
        assertEquals(2, session.getToolSuccessCount());
        assertEquals(1, session.getToolFailureCount());
        assertEquals(3, session.getCurrentChainToolCount());
    }

    @Test
    @DisplayName("resetToolChain 应该重置链计数器")
    void testResetToolChain() {
        session.incrementToolSuccess();
        session.incrementToolSuccess();
        assertEquals(2, session.getCurrentChainToolCount());
        
        session.resetToolChain();
        assertEquals(0, session.getCurrentChainToolCount());
    }

    @Test
    @DisplayName("antiLoopExempted 应该正确设置和获取")
    void testAntiLoopExempted() {
        assertFalse(session.isAntiLoopExempted());
        
        session.setAntiLoopExempted(true);
        assertTrue(session.isAntiLoopExempted());
        
        session.setAntiLoopExempted(false);
        assertFalse(session.isAntiLoopExempted());
    }

    @Test
    @DisplayName("Mode 应该正确设置和获取")
    void testMode() {
        assertEquals(DialogueSession.Mode.NORMAL, session.getMode());
        
        session.setMode(DialogueSession.Mode.YOLO);
        assertEquals(DialogueSession.Mode.YOLO, session.getMode());
    }

    @Test
    @DisplayName("lastThought 应该正确设置和获取")
    void testLastThought() {
        assertNull(session.getLastThought());
        
        session.setLastThought("最后的思考");
        assertEquals("最后的思考", session.getLastThought());
    }

    @Test
    @DisplayName("logFilePath 应该正确设置和获取")
    void testLogFilePath() {
        assertNull(session.getLogFilePath());
        
        session.setLogFilePath("/path/to/log.txt");
        assertEquals("/path/to/log.txt", session.getLogFilePath());
    }

    @Test
    @DisplayName("addToolCall 应该正确添加工具调用")
    void testAddToolCall() {
        session.addToolCall("tool_call_1");
        session.addToolCall("tool_call_2");
        
        assertEquals(2, session.getToolCallHistory().size());
        assertTrue(session.getToolCallHistory().contains("tool_call_1"));
        assertTrue(session.getToolCallHistory().contains("tool_call_2"));
    }

    @Test
    @DisplayName("工具调用历史应该限制在10条")
    void testToolCallHistoryLimit() {
        for (int i = 0; i < 15; i++) {
            session.addToolCall("tool_" + i);
        }
        
        assertEquals(10, session.getToolCallHistory().size());
    }

    @Test
    @DisplayName("removeLastMessage 应该移除最后一条消息")
    void testRemoveLastMessage() {
        session.addMessage("user", "消息1");
        session.addMessage("assistant", "消息2");
        
        session.removeLastMessage();
        
        assertEquals(1, session.getHistory().size());
        assertEquals("消息1", session.getHistory().get(0).getContent());
    }

    @Test
    @DisplayName("removeLastMessage 在空历史时不应报错")
    void testRemoveLastMessageOnEmptyHistory() {
        assertDoesNotThrow(() -> session.removeLastMessage());
    }

    @Test
    @DisplayName("thoughtTokens 应该正确累加")
    void testThoughtTokens() {
        assertEquals(0, session.getThoughtTokens());
        
        session.addThoughtTokens(100);
        assertEquals(100, session.getThoughtTokens());
        
        session.addThoughtTokens(50);
        assertEquals(150, session.getThoughtTokens());
    }

    @Test
    @DisplayName("thinkingTime 应该正确累加")
    void testThinkingTime() {
        assertEquals(0, session.getTotalThinkingTimeMs());
        assertEquals(0, session.getLastThinkingTimeMs());
        
        session.addThinkingTime(1000);
        assertEquals(1000, session.getTotalThinkingTimeMs());
        assertEquals(1000, session.getLastThinkingTimeMs());
        
        session.addThinkingTime(500);
        assertEquals(1500, session.getTotalThinkingTimeMs());
        assertEquals(500, session.getLastThinkingTimeMs());
    }

    @Test
    @DisplayName("calculateTokens 静态方法应该正确计算")
    void testCalculateTokens() {
        int tokens = DialogueSession.calculateTokens("Hello World");
        assertTrue(tokens > 0);
    }

    @Test
    @DisplayName("findMessageById 应该找到对应消息")
    void testFindMessageById() {
        session.addMessage("user", "消息1");
        session.addMessage("assistant", "消息2");
        
        DialogueSession.Message msg = session.findMessageById(0);
        assertNotNull(msg);
        assertEquals("消息1", msg.getContent());
    }

    @Test
    @DisplayName("findMessageById 不存在时应该返回 null")
    void testFindMessageByIdNotFound() {
        session.addMessage("user", "消息1");
        
        DialogueSession.Message msg = session.findMessageById(999);
        assertNull(msg);
    }

    @Test
    @DisplayName("getThoughtSnapshot 应该返回思考快照")
    void testGetThoughtSnapshot() {
        session.addMessage("assistant", "回复", "思考内容");
        
        DialogueSession.ThoughtSnapshot snapshot = session.getThoughtSnapshot(0);
        assertNotNull(snapshot);
        assertEquals("思考内容", snapshot.getThought());
    }

    @Test
    @DisplayName("历史超过20条时应该自动裁剪")
    void testHistoryAutoTrim() {
        for (int i = 0; i < 25; i++) {
            session.addMessage("user", "消息" + i);
        }
        
        assertTrue(session.getHistory().size() <= 20);
    }

    @Test
    @DisplayName("Message 默认构造函数")
    void testMessageDefaultConstructor() {
        DialogueSession.Message msg = new DialogueSession.Message("user", "内容");
        assertEquals(-1, msg.getId());
        assertEquals("user", msg.getRole());
        assertEquals("内容", msg.getContent());
        assertNull(msg.getThought());
        assertEquals(0, msg.getThinkingTimeMs());
        assertFalse(msg.hasThought());
    }

    @Test
    @DisplayName("Message 带 thought 构造函数")
    void testMessageWithThoughtConstructor() {
        DialogueSession.Message msg = new DialogueSession.Message("assistant", "内容", "思考");
        assertEquals(-1, msg.getId());
        assertEquals("assistant", msg.getRole());
        assertEquals("内容", msg.getContent());
        assertEquals("思考", msg.getThought());
        assertTrue(msg.hasThought());
    }

    @Test
    @DisplayName("Message 处理 null 参数")
    void testMessageNullParameters() {
        DialogueSession.Message msg = new DialogueSession.Message(null, null);
        assertEquals("user", msg.getRole());
        assertEquals("", msg.getContent());
    }

    @Test
    @DisplayName("ThoughtSnapshot 构造函数")
    void testThoughtSnapshot() {
        DialogueSession.ThoughtSnapshot snapshot = new DialogueSession.ThoughtSnapshot("思考", 1000);
        assertEquals("思考", snapshot.getThought());
        assertEquals(1000, snapshot.getThinkingTimeMs());
    }

    @Test
    @DisplayName("Mode 枚举值")
    void testModeEnum() {
        assertEquals(2, DialogueSession.Mode.values().length);
        assertEquals(DialogueSession.Mode.NORMAL, DialogueSession.Mode.valueOf("NORMAL"));
        assertEquals(DialogueSession.Mode.YOLO, DialogueSession.Mode.valueOf("YOLO"));
    }

    @Test
    @DisplayName("getStartTime 应该返回开始时间")
    void testGetStartTime() {
        long startTime = session.getStartTime();
        assertTrue(startTime > 0);
        assertTrue(startTime <= System.currentTimeMillis());
    }

    @Test
    @DisplayName("addMessage 带空思考内容")
    void testAddMessageWithEmptyThought() {
        session.addMessage("assistant", "回复内容", "");
        
        assertEquals(1, session.getHistory().size());
        DialogueSession.Message msg = session.getHistory().get(0);
        assertFalse(msg.hasThought());
    }

    @Test
    @DisplayName("addMessage 带空思考内容")
    void testAddMessageWithNullThought() {
        session.addMessage("assistant", "回复内容", null);
        
        assertEquals(1, session.getHistory().size());
        DialogueSession.Message msg = session.getHistory().get(0);
        assertFalse(msg.hasThought());
    }

    @Test
    @DisplayName("getEstimatedTokens 带模型名称")
    void testGetEstimatedTokensWithModelName() {
        session.addMessage("user", "Hello World");
        int tokens = session.getEstimatedTokens("gpt-4");
        assertTrue(tokens > 0);
    }

    @Test
    @DisplayName("getEstimatedTokens 带 null 模型名称")
    void testGetEstimatedTokensWithNullModelName() {
        session.addMessage("user", "Hello World");
        int tokens = session.getEstimatedTokens(null);
        assertTrue(tokens > 0);
    }

    @Test
    @DisplayName("getEstimatedTokens 带空模型名称")
    void testGetEstimatedTokensWithEmptyModelName() {
        session.addMessage("user", "Hello World");
        int tokens = session.getEstimatedTokens("");
        assertTrue(tokens > 0);
    }

    @Test
    @DisplayName("calculateTokens 带模型名称")
    void testCalculateTokensWithModelName() {
        int tokens = DialogueSession.calculateTokens("Hello World", "gpt-4");
        assertTrue(tokens > 0);
    }

    @Test
    @DisplayName("calculateTokens 带 null 模型名称")
    void testCalculateTokensWithNullModelName() {
        int tokens = DialogueSession.calculateTokens("Hello World", null);
        assertTrue(tokens > 0);
    }

    @Test
    @DisplayName("getThoughtSnapshot 不存在时返回 null")
    void testGetThoughtSnapshotNotFound() {
        DialogueSession.ThoughtSnapshot snapshot = session.getThoughtSnapshot(999);
        assertNull(snapshot);
    }

    @Test
    @DisplayName("Message 带 thought 构造函数 - 空 thought")
    void testMessageWithEmptyThought() {
        DialogueSession.Message msg = new DialogueSession.Message("assistant", "内容", "");
        assertFalse(msg.hasThought());
    }

    @Test
    @DisplayName("Message 带 thought 构造函数 - null thought")
    void testMessageWithNullThoughtInConstructor() {
        DialogueSession.Message msg = new DialogueSession.Message("assistant", "内容", (String) null);
        assertFalse(msg.hasThought());
    }

    @Test
    @DisplayName("Message 完整构造函数")
    void testMessageFullConstructor() {
        DialogueSession.Message msg = new DialogueSession.Message(123L, "assistant", "内容", "思考", 500L);
        assertEquals(123L, msg.getId());
        assertEquals("assistant", msg.getRole());
        assertEquals("内容", msg.getContent());
        assertEquals("思考", msg.getThought());
        assertEquals(500L, msg.getThinkingTimeMs());
        assertTrue(msg.hasThought());
    }

    @Test
    @DisplayName("calculateTokens 带不同模型名称")
    void testCalculateTokensWithDifferentModels() {
        int tokens1 = DialogueSession.calculateTokens("Hello", "gpt-4");
        int tokens2 = DialogueSession.calculateTokens("Hello", "openai-gpt");
        int tokens3 = DialogueSession.calculateTokens("Hello", "unknown-model");
        
        assertTrue(tokens1 > 0);
        assertTrue(tokens2 > 0);
        assertTrue(tokens3 > 0);
    }

    @Test
    @DisplayName("thoughtSnapshots 限制在 50 条")
    void testThoughtSnapshotsLimit() {
        for (int i = 0; i < 60; i++) {
            session.addMessage("assistant", "消息" + i, "思考" + i);
        }
        
        int snapshotCount = 0;
        for (int i = 0; i < 60; i++) {
            if (session.getThoughtSnapshot(i) != null) {
                snapshotCount++;
            }
        }
        
        assertTrue(snapshotCount <= 50);
    }

    @Test
    @DisplayName("getEstimatedTokens 多次调用缓存模型编码")
    void testGetEstimatedTokensCaching() {
        session.addMessage("user", "Hello World");
        
        int tokens1 = session.getEstimatedTokens("gpt-4o");
        int tokens2 = session.getEstimatedTokens("gpt-4o");
        
        assertEquals(tokens1, tokens2);
    }

    @Test
    @DisplayName("addMessage 只有 content 没有 thought 时不记录 snapshot")
    void testAddMessageNoThoughtSnapshot() {
        session.addMessage("user", "普通消息");
        
        assertNull(session.getThoughtSnapshot(0));
    }

    @Test
    @DisplayName("getToolCallHistory 返回副本")
    void testGetToolCallHistoryReturnsCopy() {
        session.addToolCall("tool1");
        List<String> history = session.getToolCallHistory();
        history.add("tool2");
        
        assertEquals(1, session.getToolCallHistory().size());
    }
}
