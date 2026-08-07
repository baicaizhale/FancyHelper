package org.YanPl.manager;

import org.YanPl.FancyHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("ServerMemoryManager 单元测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ServerMemoryManagerTest {

    @Mock
    private FancyHelper plugin;

    @Mock
    private ConfigManager configManager;

    private ServerMemoryManager serverMemoryManager;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        when(plugin.getLogger()).thenReturn(Logger.getLogger("TestLogger"));
        when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        when(plugin.getConfigManager()).thenReturn(configManager);
        when(configManager.isDebug()).thenReturn(true);
        when(configManager.getServerMemoryMaxEntries()).thenReturn(100);

        serverMemoryManager = new ServerMemoryManager(plugin);
    }

    @Test
    @DisplayName("addMemory 添加成功应返回成功消息")
    void testAddMemory_Success_ReturnsSuccessMessage() {
        String result = serverMemoryManager.addMemory("周五晚高峰20点", "rule", "Admin");

        assertTrue(result.startsWith("success:"));
        assertTrue(result.contains("周五晚高峰20点"));
    }

    @Test
    @DisplayName("addMemory 应写盘到 servermemory.json")
    void testAddMemory_Success_SavesToFile() throws Exception {
        serverMemoryManager.addMemory("测试规则", "rule", "Admin");

        Path file = tempDir.resolve("servermemory.json");
        assertTrue(Files.exists(file));
        String json = new String(Files.readAllBytes(file), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(json.contains("测试规则"));
        assertTrue(json.contains("Admin"));
    }

    @Test
    @DisplayName("addMemory 默认分类应使用 rule")
    void testAddMemory_NullCategory_UsesRule() {
        serverMemoryManager.addMemory("测试规则", null, "Admin");

        assertEquals("rule", serverMemoryManager.getMemories().get(0).getCategory());
    }

    @Test
    @DisplayName("addMemory 空内容应返回错误")
    void testAddMemory_EmptyContent_ReturnsError() {
        String result = serverMemoryManager.addMemory("", "rule", "Admin");

        assertTrue(result.startsWith("error:"));
    }

    @Test
    @DisplayName("addMemory 超上限应淘汰最久未使用的条目而非拒绝")
    void testAddMemory_OverLimit_EvictsLRU() {
        when(configManager.getServerMemoryMaxEntries()).thenReturn(3);
        serverMemoryManager.addMemory("记忆1", "rule", "Admin");
        serverMemoryManager.addMemory("记忆2", "rule", "Admin");
        serverMemoryManager.addMemory("记忆3", "rule", "Admin");

        String result = serverMemoryManager.addMemory("记忆4", "rule", "Admin");

        assertTrue(result.startsWith("success:"));
        assertEquals(3, serverMemoryManager.getMemories().size());
        assertFalse(serverMemoryManager.getMemories().stream().anyMatch(m -> m.getContent().equals("记忆1")));
    }

    @Test
    @DisplayName("addMemory 淘汰应优先淘汰 lastUsed 为 null 的条目")
    void testAddMemory_EvictsNullLastUsedFirst() {
        when(configManager.getServerMemoryMaxEntries()).thenReturn(2);
        serverMemoryManager.addMemory("周五晚高峰注意", "rule", "Admin");
        serverMemoryManager.addMemory("内存优化方案", "rule", "Admin");

        // 记忆1 被注入过（lastUsed 非 null），记忆2 未使用（lastUsed 为 null）
        serverMemoryManager.getMemoriesForPrompt("周五", 1, 1);
        ServerMemoryManager.ServerMemory used = serverMemoryManager.getMemories().get(0);
        assertEquals("周五晚高峰注意", used.getContent());
        assertNotNull(used.getLastUsed());

        String result = serverMemoryManager.addMemory("新规则", "rule", "Admin");

        assertTrue(result.startsWith("success:"));
        // 未使用的记忆2 应被淘汰
        assertTrue(serverMemoryManager.getMemories().stream().anyMatch(m -> m.getContent().equals("周五晚高峰注意")));
        assertTrue(serverMemoryManager.getMemories().stream().anyMatch(m -> m.getContent().equals("新规则")));
        assertFalse(serverMemoryManager.getMemories().stream().anyMatch(m -> m.getContent().equals("内存优化方案")));
    }

    @Test
    @DisplayName("removeMemory 有效序号应删除成功")
    void testRemoveMemory_ValidIndex_Success() {
        serverMemoryManager.addMemory("记忆1", "rule", "Admin");
        serverMemoryManager.addMemory("记忆2", "rule", "Admin");

        String result = serverMemoryManager.removeMemory(1);

        assertTrue(result.startsWith("success:"));
        assertEquals(1, serverMemoryManager.getMemories().size());
        assertEquals("记忆2", serverMemoryManager.getMemories().get(0).getContent());
    }

    @Test
    @DisplayName("removeMemory 越界序号应返回错误")
    void testRemoveMemory_IndexOutOfRange_ReturnsError() {
        serverMemoryManager.addMemory("记忆1", "rule", "Admin");

        String result = serverMemoryManager.removeMemory(5);

        assertTrue(result.startsWith("error:"));
    }

    @Test
    @DisplayName("removeMemory 序号为0应返回错误")
    void testRemoveMemory_ZeroIndex_ReturnsError() {
        serverMemoryManager.addMemory("记忆1", "rule", "Admin");

        String result = serverMemoryManager.removeMemory(0);

        assertTrue(result.startsWith("error:"));
    }

    @Test
    @DisplayName("removeMemory 负数序号应返回错误")
    void testRemoveMemory_NegativeIndex_ReturnsError() {
        serverMemoryManager.addMemory("记忆1", "rule", "Admin");

        String result = serverMemoryManager.removeMemory(-1);

        assertTrue(result.startsWith("error:"));
    }

    @Test
    @DisplayName("updateMemory 有效序号应更新成功并保留作者")
    void testUpdateMemory_ValidIndex_Success() {
        serverMemoryManager.addMemory("旧规则", "rule", "Admin");

        String result = serverMemoryManager.updateMemory(1, "新规则", "rule");

        assertTrue(result.startsWith("success:"));
        ServerMemoryManager.ServerMemory updated = serverMemoryManager.getMemories().get(0);
        assertEquals("新规则", updated.getContent());
        assertEquals("Admin", updated.getAuthor());
    }

    @Test
    @DisplayName("updateMemory 越界序号应返回错误")
    void testUpdateMemory_IndexOutOfRange_ReturnsError() {
        serverMemoryManager.addMemory("记忆1", "rule", "Admin");

        String result = serverMemoryManager.updateMemory(5, "新规则", "rule");

        assertTrue(result.startsWith("error:"));
    }

    @Test
    @DisplayName("updateMemory 空内容应返回错误")
    void testUpdateMemory_EmptyContent_ReturnsError() {
        serverMemoryManager.addMemory("记忆1", "rule", "Admin");

        String result = serverMemoryManager.updateMemory(1, "   ", "rule");

        assertTrue(result.startsWith("error:"));
    }

    @Test
    @DisplayName("clearMemories 应清空所有记忆并删文件")
    void testClearMemories_ClearsAll() throws Exception {
        serverMemoryManager.addMemory("记忆1", "rule", "Admin");
        serverMemoryManager.addMemory("记忆2", "rule", "Admin");

        String result = serverMemoryManager.clearMemories();

        assertTrue(result.startsWith("success:"));
        assertTrue(serverMemoryManager.getMemories().isEmpty());
        assertFalse(Files.exists(tempDir.resolve("servermemory.json")));
    }

    @Test
    @DisplayName("getMemories 应返回独立副本")
    void testGetMemories_ReturnsCopy() {
        serverMemoryManager.addMemory("记忆1", "rule", "Admin");

        List<ServerMemoryManager.ServerMemory> list1 = serverMemoryManager.getMemories();
        List<ServerMemoryManager.ServerMemory> list2 = serverMemoryManager.getMemories();

        assertNotSame(list1, list2);
        assertEquals(list1.size(), list2.size());
    }

    @Test
    @DisplayName("getMemoriesForPrompt topK=0 应返回空")
    void testGetMemoriesForPrompt_TopKZero_ReturnsEmpty() {
        serverMemoryManager.addMemory("周五晚高峰", "rule", "Admin");

        List<ServerMemoryManager.ServerMemory> result = serverMemoryManager.getMemoriesForPrompt("周五", 0, 1);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("getMemoriesForPrompt 相关内容应命中")
    void testGetMemoriesForPrompt_MatchingContent_Hits() {
        serverMemoryManager.addMemory("周五晚高峰注意", "rule", "Admin");
        serverMemoryManager.addMemory("备份目录配置", "config", "Admin");

        List<ServerMemoryManager.ServerMemory> result = serverMemoryManager.getMemoriesForPrompt("帮我处理周五的事", 4, 1);

        assertEquals(1, result.size());
        assertEquals("周五晚高峰注意", result.get(0).getContent());
    }

    @Test
    @DisplayName("getMemoriesForPrompt 分类命中应获得更高分")
    void testGetMemoriesForPrompt_CategoryHit_Outranks() {
        serverMemoryManager.addMemory("注意优化", "memory", "Admin");
        serverMemoryManager.addMemory("memory 优化方案", "rule", "Admin");

        List<ServerMemoryManager.ServerMemory> result = serverMemoryManager.getMemoriesForPrompt("memory", 4, 1);

        // 分类 memory 命中 +3，应排在内容命中 +2 的前面
        assertEquals("注意优化", result.get(0).getContent());
    }

    @Test
    @DisplayName("getMemoriesForPrompt 低于最小相关分应被过滤")
    void testGetMemoriesForPrompt_BelowMinRelevance_Filtered() {
        serverMemoryManager.addMemory("周五晚高峰", "rule", "Admin");

        List<ServerMemoryManager.ServerMemory> result = serverMemoryManager.getMemoriesForPrompt("完全不相关的内容", 4, 1);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("getMemoriesForPrompt 按分数降序取 TopK")
    void testGetMemoriesForPrompt_TopKRespectsRanking() {
        serverMemoryManager.addMemory("周五晚高峰", "rule", "Admin");
        serverMemoryManager.addMemory("周一到周五排班", "rule", "Admin");
        serverMemoryManager.addMemory("周六活动", "rule", "Admin");

        List<ServerMemoryManager.ServerMemory> result = serverMemoryManager.getMemoriesForPrompt("周五", 2, 1);

        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("getMemoriesForPrompt 应更新命中条目的 lastUsed")
    void testGetMemoriesForPrompt_UpdatesLastUsed() {
        serverMemoryManager.addMemory("周五晚高峰", "rule", "Admin");

        serverMemoryManager.getMemoriesForPrompt("周五", 4, 1);

        assertNotNull(serverMemoryManager.getMemories().get(0).getLastUsed());
    }

    @Test
    @DisplayName("持久化 round-trip 应保持一致")
    void testPersistence_RoundTrip() throws Exception {
        serverMemoryManager.addMemory("规则一", "rule", "Admin");
        serverMemoryManager.addMemory("规则二", "config", "OtherAdmin");

        ServerMemoryManager reloaded = new ServerMemoryManager(plugin);

        List<ServerMemoryManager.ServerMemory> memories = reloaded.getMemories();
        assertEquals(2, memories.size());
        assertEquals("规则一", memories.get(0).getContent());
        assertEquals("Admin", memories.get(0).getAuthor());
        assertEquals("规则二", memories.get(1).getContent());
        assertEquals("config", memories.get(1).getCategory());
    }

    @Test
    @DisplayName("listMemories 空列表应返回提示消息")
    void testListMemories_EmptyList_ReturnsPrompt() {
        String result = serverMemoryManager.listMemories();

        assertEquals("当前没有任何服务器记忆", result);
    }

    @Test
    @DisplayName("listMemories 有记忆应返回列表")
    void testListMemories_WithMemories_ReturnsList() {
        serverMemoryManager.addMemory("规则一", "rule", "Admin");

        String result = serverMemoryManager.listMemories();

        assertTrue(result.contains("服务器记忆列表"));
        assertTrue(result.contains("规则一"));
        assertTrue(result.contains("Admin"));
    }

    @Test
    @DisplayName("shutdown 应落盘并清空缓存")
    void testShutdown_SavesAndClears() throws Exception {
        serverMemoryManager.addMemory("规则一", "rule", "Admin");

        serverMemoryManager.shutdown();

        assertTrue(Files.exists(tempDir.resolve("servermemory.json")));
        assertTrue(serverMemoryManager.getMemories().isEmpty());
    }
}
