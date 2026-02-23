package org.YanPl.manager;

import org.YanPl.FancyHelper;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("InstructionManager 单元测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InstructionManagerTest {

    @Mock
    private FancyHelper plugin;

    @Mock
    private Player player;

    @Mock
    private Server server;

    @Mock
    private org.bukkit.OfflinePlayer offlinePlayer;

    @Mock
    private ConfigManager configManager;

    private InstructionManager instructionManager;
    private UUID testUuid;
    private String testPlayerName = "TestPlayer";

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        when(plugin.getLogger()).thenReturn(Logger.getLogger("TestLogger"));
        when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        when(plugin.getServer()).thenReturn(server);
        when(server.getOfflinePlayer(any(UUID.class))).thenReturn(offlinePlayer);
        when(offlinePlayer.getName()).thenReturn(testPlayerName);
        when(plugin.getConfigManager()).thenReturn(configManager);
        when(configManager.isDebug()).thenReturn(true);

        instructionManager = new InstructionManager(plugin);
        testUuid = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(testUuid);
        when(player.getName()).thenReturn(testPlayerName);
    }

    @Test
    @DisplayName("addInstruction 添加成功应返回成功消息")
    void testAddInstruction_Success_ReturnsSuccessMessage() {
        String result = instructionManager.addInstruction(player, "测试记忆", "general");

        assertTrue(result.startsWith("success:"));
        assertTrue(result.contains("测试记忆"));
    }

    @Test
    @DisplayName("addInstruction 添加成功应保存到文件")
    void testAddInstruction_Success_SavesToFile() {
        instructionManager.addInstruction(player, "测试记忆", "general");

        List<InstructionManager.PlayerInstruction> instructions = instructionManager.getInstructions(testUuid);
        assertEquals(1, instructions.size());
        assertEquals("测试记忆", instructions.get(0).getContent());
    }

    @Test
    @DisplayName("addInstruction 达到上限应返回错误消息")
    void testAddInstruction_AtLimit_ReturnsErrorMessage() {
        for (int i = 0; i < 50; i++) {
            instructionManager.addInstruction(player, "记忆" + i, "general");
        }

        String result = instructionManager.addInstruction(player, "第51条记忆", "general");

        assertTrue(result.startsWith("error:"));
        assertTrue(result.contains("50"));
    }

    @Test
    @DisplayName("addInstruction 使用默认分类应使用 general")
    void testAddInstruction_NullCategory_UsesDefaultCategory() {
        instructionManager.addInstruction(player, "测试记忆", null);

        List<InstructionManager.PlayerInstruction> instructions = instructionManager.getInstructions(testUuid);
        assertEquals("general", instructions.get(0).getCategory());
    }

    @Test
    @DisplayName("addInstruction 多个记忆应保存所有")
    void testAddInstruction_MultipleInstructions_AllSaved() {
        instructionManager.addInstruction(player, "记忆1", "general");
        instructionManager.addInstruction(player, "记忆2", "preference");
        instructionManager.addInstruction(player, "记忆3", "general");

        List<InstructionManager.PlayerInstruction> instructions = instructionManager.getInstructions(testUuid);
        assertEquals(3, instructions.size());
    }

    @Test
    @DisplayName("removeInstruction 有效序号应删除成功")
    void testRemoveInstruction_ValidIndex_Success() {
        instructionManager.addInstruction(player, "记忆1", "general");
        instructionManager.addInstruction(player, "记忆2", "general");

        String result = instructionManager.removeInstruction(player, 1);

        assertTrue(result.startsWith("success:"));
        assertEquals(1, instructionManager.getInstructions(testUuid).size());
    }

    @Test
    @DisplayName("removeInstruction 序号超出范围应返回错误")
    void testRemoveInstruction_IndexOutOfRange_ReturnsError() {
        instructionManager.addInstruction(player, "记忆1", "general");

        String result = instructionManager.removeInstruction(player, 5);

        assertTrue(result.startsWith("error:"));
    }

    @Test
    @DisplayName("removeInstruction 序号为0应返回错误")
    void testRemoveInstruction_ZeroIndex_ReturnsError() {
        instructionManager.addInstruction(player, "记忆1", "general");

        String result = instructionManager.removeInstruction(player, 0);

        assertTrue(result.startsWith("error:"));
    }

    @Test
    @DisplayName("removeInstruction 序号为负数应返回错误")
    void testRemoveInstruction_NegativeIndex_ReturnsError() {
        instructionManager.addInstruction(player, "记忆1", "general");

        String result = instructionManager.removeInstruction(player, -1);

        assertTrue(result.startsWith("error:"));
    }

    @Test
    @DisplayName("updateInstruction 有效序号应更新成功")
    void testUpdateInstruction_ValidIndex_Success() {
        instructionManager.addInstruction(player, "旧记忆", "general");

        String result = instructionManager.updateInstruction(player, 1, "新记忆", "preference");

        assertTrue(result.startsWith("success:"));
        assertEquals("新记忆", instructionManager.getInstructions(testUuid).get(0).getContent());
    }

    @Test
    @DisplayName("updateInstruction 序号超出范围应返回错误")
    void testUpdateInstruction_IndexOutOfRange_ReturnsError() {
        instructionManager.addInstruction(player, "记忆1", "general");

        String result = instructionManager.updateInstruction(player, 5, "新记忆", "general");

        assertTrue(result.startsWith("error:"));
    }

    @Test
    @DisplayName("updateInstruction 空内容应返回错误")
    void testUpdateInstruction_EmptyContent_ReturnsError() {
        instructionManager.addInstruction(player, "记忆1", "general");

        String result = instructionManager.updateInstruction(player, 1, "", "general");

        assertTrue(result.startsWith("error:"));
        assertTrue(result.contains("空"));
    }

    @Test
    @DisplayName("updateInstruction 仅空格内容应返回错误")
    void testUpdateInstruction_WhitespaceContent_ReturnsError() {
        instructionManager.addInstruction(player, "记忆1", "general");

        String result = instructionManager.updateInstruction(player, 1, "   ", "general");

        assertTrue(result.startsWith("error:"));
    }

    @Test
    @DisplayName("updateInstruction null 内容应返回错误")
    void testUpdateInstruction_NullContent_ReturnsError() {
        instructionManager.addInstruction(player, "记忆1", "general");

        String result = instructionManager.updateInstruction(player, 1, null, "general");

        assertTrue(result.startsWith("error:"));
    }

    @Test
    @DisplayName("clearInstructions 应清空所有记忆")
    void testClearInstructions_ClearsAllInstructions() {
        instructionManager.addInstruction(player, "记忆1", "general");
        instructionManager.addInstruction(player, "记忆2", "general");

        String result = instructionManager.clearInstructions(player);

        assertTrue(result.startsWith("success:"));
        assertTrue(instructionManager.getInstructions(testUuid).isEmpty());
    }

    @Test
    @DisplayName("clearInstructions 空列表应返回成功")
    void testClearInstructions_EmptyList_Success() {
        String result = instructionManager.clearInstructions(player);

        assertTrue(result.startsWith("success:"));
    }

    @Test
    @DisplayName("listInstructions 空列表应返回提示消息")
    void testListInstructions_EmptyList_ReturnsPrompt() {
        String result = instructionManager.listInstructions(player);

        assertEquals("当前没有任何记忆", result);
    }

    @Test
    @DisplayName("listInstructions 有记忆应返回列表")
    void testListInstructions_WithInstructions_ReturnsList() {
        instructionManager.addInstruction(player, "记忆1", "general");
        instructionManager.addInstruction(player, "记忆2", "preference");

        String result = instructionManager.listInstructions(player);

        assertTrue(result.contains("记忆列表"));
        assertTrue(result.contains("记忆1"));
        assertTrue(result.contains("记忆2"));
    }

    @Test
    @DisplayName("getInstructions 新玩家应返回空列表")
    void testGetInstructions_NewPlayer_ReturnsEmptyList() {
        UUID newUuid = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(newUuid);

        List<InstructionManager.PlayerInstruction> instructions = instructionManager.getInstructions(newUuid);

        assertNotNull(instructions);
        assertTrue(instructions.isEmpty());
    }

    @Test
    @DisplayName("getInstructions 应返回独立副本")
    void testGetInstructions_ReturnsCopy() {
        instructionManager.addInstruction(player, "记忆1", "general");

        List<InstructionManager.PlayerInstruction> instructions1 = instructionManager.getInstructions(testUuid);
        List<InstructionManager.PlayerInstruction> instructions2 = instructionManager.getInstructions(testUuid);

        assertNotSame(instructions1, instructions2);
        assertEquals(instructions1.size(), instructions2.size());
    }

    @Test
    @DisplayName("getInstructionsAsPrompt 空列表应返回 null")
    void testGetInstructionsAsPrompt_EmptyList_ReturnsNull() {
        String prompt = instructionManager.getInstructionsAsPrompt(testUuid);

        assertNull(prompt);
    }

    @Test
    @DisplayName("getInstructionsAsPrompt 有记忆应返回格式化的提示")
    void testGetInstructionsAsPrompt_WithInstructions_ReturnsFormattedPrompt() {
        instructionManager.addInstruction(player, "记忆1", "preference");
        instructionManager.addInstruction(player, "记忆2", "general");

        String prompt = instructionManager.getInstructionsAsPrompt(testUuid);

        assertNotNull(prompt);
        assertTrue(prompt.contains("记忆1"));
        assertTrue(prompt.contains("记忆2"));
        assertTrue(prompt.contains("偏好"));
        assertTrue(prompt.contains("记忆"));
    }

    @Test
    @DisplayName("getInstructions 有缓存应从缓存读取")
    void testGetInstructions_WithCache_UsesCache() {
        instructionManager.addInstruction(player, "记忆1", "general");

        instructionManager.getInstructions(testUuid);
        instructionManager.getInstructions(testUuid);

        List<InstructionManager.PlayerInstruction> instructions = instructionManager.getInstructions(testUuid);
        assertEquals(1, instructions.size());
    }

    @Test
    @DisplayName("shutdown 应清空缓存")
    void testShutdown_ClearsCache() {
        instructionManager.addInstruction(player, "记忆1", "general");
        instructionManager.getInstructions(testUuid);
        
        instructionManager.shutdown();
        
        instructionManager.getInstructions(testUuid);
    }

    @Test
    @DisplayName("addInstruction 应设置正确的时间戳")
    void testAddInstruction_SetsCorrectTimestamp() {
        instructionManager.addInstruction(player, "测试记忆", "general");

        List<InstructionManager.PlayerInstruction> instructions = instructionManager.getInstructions(testUuid);
        assertNotNull(instructions.get(0).getTimestamp());
        assertFalse(instructions.get(0).getTimestamp().isEmpty());
    }

    @Test
    @DisplayName("不同玩家记忆应隔离")
    void testDifferentPlayersSeparateInstructions() {
        UUID uuid2 = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(testUuid);
        
        instructionManager.addInstruction(player, "玩家1记忆", "general");
        
        when(player.getUniqueId()).thenReturn(uuid2);
        instructionManager.addInstruction(player, "玩家2记忆", "preference");
        
        when(player.getUniqueId()).thenReturn(testUuid);
        assertEquals(1, instructionManager.getInstructions(testUuid).size());
        assertEquals("玩家1记忆", instructionManager.getInstructions(testUuid).get(0).getContent());
        
        when(player.getUniqueId()).thenReturn(uuid2);
        assertEquals(1, instructionManager.getInstructions(uuid2).size());
        assertEquals("玩家2记忆", instructionManager.getInstructions(uuid2).get(0).getContent());
    }

    @Test
    @DisplayName("updateInstruction 应更新分类")
    void testUpdateInstruction_UpdatesCategory() {
        instructionManager.addInstruction(player, "记忆1", "general");

        instructionManager.updateInstruction(player, 1, "新记忆", "preference");

        assertEquals("preference", instructionManager.getInstructions(testUuid).get(0).getCategory());
    }

    @Test
    @DisplayName("removeInstruction 应删除指定记忆")
    void testRemoveInstruction_RemovesCorrectItem() {
        instructionManager.addInstruction(player, "记忆1", "general");
        instructionManager.addInstruction(player, "记忆2", "general");
        instructionManager.addInstruction(player, "记忆3", "general");

        instructionManager.removeInstruction(player, 2);

        List<InstructionManager.PlayerInstruction> instructions = instructionManager.getInstructions(testUuid);
        assertEquals(2, instructions.size());
        assertEquals("记忆1", instructions.get(0).getContent());
        assertEquals("记忆3", instructions.get(1).getContent());
    }
}
