package org.YanPl.manager;

import org.YanPl.FancyHelper;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("InstructionManager 记忆去重测试")
class InstructionManagerDedupeTest {

    private InstructionManager manager;
    private Player player;
    private Path dir;

    @BeforeEach
    void setUp() throws IOException {
        dir = Files.createTempDirectory("inst-test");
        FancyHelper plugin = Mockito.mock(FancyHelper.class);
        Mockito.when(plugin.getDataFolder()).thenReturn(dir.getParent().toFile());
        ConfigManager configManager = Mockito.mock(ConfigManager.class);
        Mockito.when(configManager.isDebug()).thenReturn(false);
        Mockito.when(plugin.getConfigManager()).thenReturn(configManager);
        // InstructionManager 在 getDataFolder()/instruction 下建目录
        manager = new InstructionManager(plugin) {};
        player = Mockito.mock(Player.class);
        Mockito.when(player.getUniqueId()).thenReturn(java.util.UUID.randomUUID());
        Mockito.when(player.getName()).thenReturn("tester");
    }

    @Test
    @DisplayName("重复内容不追加，返回已存在提示")
    void testDuplicateNotAdded() {
        assertTrue(manager.addInstruction(player, "喜欢金色", "preference").startsWith("success"));
        int count = manager.getInstructions(player.getUniqueId()).size();
        String second = manager.addInstruction(player, "喜欢金色", "preference");
        assertTrue(second.contains("已存在"), "重复写入应返回已存在提示: " + second);
        assertEquals(count, manager.getInstructions(player.getUniqueId()).size(), "重复写入不应增加条数");
    }

    @Test
    @DisplayName("前后空白差异视为同一条记忆")
    void testTrimmedDuplicate() {
        manager.addInstruction(player, "喜欢金色", "preference");
        String second = manager.addInstruction(player, "  喜欢金色  ", "preference");
        assertTrue(second.contains("已存在"));
        assertEquals(1, manager.getInstructions(player.getUniqueId()).size());
    }

    @Test
    @DisplayName("不同内容正常追加")
    void testDifferentContentAdded() {
        manager.addInstruction(player, "喜欢金色", "preference");
        manager.addInstruction(player, "讨厌苦力怕", "preference");
        assertEquals(2, manager.getInstructions(player.getUniqueId()).size());
    }
}
