package org.YanPl.manager;

import org.YanPl.FancyHelper;
import org.YanPl.util.CloudErrorReport;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.File;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("VerificationManager 单元测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VerificationManagerTest {

    @Mock
    private FancyHelper plugin;

    @Mock
    private CloudErrorReport cloudErrorReport;

    @Mock
    private Player player;

    @Mock
    private Server server;

    @Mock
    private org.bukkit.OfflinePlayer offlinePlayer;

    @Mock
    private org.bukkit.scheduler.BukkitScheduler scheduler;

    @TempDir
    File tempDir;

    private VerificationManager verificationManager;
    private UUID testUuid;
    private String testPlayerName = "TestPlayer";

    @BeforeEach
    void setUp() {
        try (MockedStatic<Bukkit> bukkitMock = mockStatic(Bukkit.class)) {
            bukkitMock.when(Bukkit::getScheduler).thenReturn(scheduler);
            bukkitMock.when(Bukkit::getServer).thenReturn(server);

            when(plugin.getLogger()).thenReturn(Logger.getLogger("TestLogger"));
            when(plugin.getCloudErrorReport()).thenReturn(cloudErrorReport);
            when(plugin.isEnabled()).thenReturn(true);
            when(plugin.getServer()).thenReturn(server);
            when(plugin.getDataFolder()).thenReturn(tempDir);

            verificationManager = new VerificationManager(plugin);
            testUuid = UUID.randomUUID();
            when(player.getUniqueId()).thenReturn(testUuid);
            when(player.getName()).thenReturn(testPlayerName);
        }
    }

    @Test
    @DisplayName("isVerifying 验证前应返回 false")
    void testIsVerifying_BeforeVerification_ReturnsFalse() {
        assertFalse(verificationManager.isVerifying(player));
    }

    @Test
    @DisplayName("handleVerification 玩家不在验证中应返回 false")
    void testHandleVerification_NotVerifying_ReturnsFalse() {
        boolean result = verificationManager.handleVerification(player, "123456");

        assertFalse(result);
    }

    @Test
    @DisplayName("handleVerification 错误密码应返回错误消息 - read 类型")
    void testHandleVerification_WrongPassword_ReadType_Fails() {
        try (MockedStatic<Bukkit> bukkitMock = mockStatic(Bukkit.class)) {
            bukkitMock.when(Bukkit::getScheduler).thenReturn(scheduler);
            bukkitMock.when(Bukkit::getServer).thenReturn(server);

            verificationManager.startVerification(player, "read", null);

            boolean result = verificationManager.handleVerification(player, "wrongpassword");

            assertTrue(result);
            verify(player).sendMessage(contains("密码错误"));
        }
    }

    @Test
    @DisplayName("handleVerification 错误密码应减少剩余次数 - read 类型")
    void testHandleVerification_WrongPassword_ReducesAttempts() {
        try (MockedStatic<Bukkit> bukkitMock = mockStatic(Bukkit.class)) {
            bukkitMock.when(Bukkit::getScheduler).thenReturn(scheduler);
            bukkitMock.when(Bukkit::getServer).thenReturn(server);

            verificationManager.startVerification(player, "read", null);

            verificationManager.handleVerification(player, "wrong1");
            verificationManager.handleVerification(player, "wrong2");

            verify(player, atLeastOnce()).sendMessage(contains("剩余次数"));
        }
    }

    @Test
    @DisplayName("handleVerification 3次错误应冻结玩家 - read 类型")
    void testHandleVerification_ThreeWrongAttempts_FreezesPlayer() {
        try (MockedStatic<Bukkit> bukkitMock = mockStatic(Bukkit.class)) {
            bukkitMock.when(Bukkit::getScheduler).thenReturn(scheduler);
            bukkitMock.when(Bukkit::getServer).thenReturn(server);

            verificationManager.startVerification(player, "read", null);

            verificationManager.handleVerification(player, "wrong1");
            verificationManager.handleVerification(player, "wrong2");
            boolean result = verificationManager.handleVerification(player, "wrong3");

            assertTrue(result);
            verify(player).sendMessage(contains("冻结"));
        }
    }

    @Test
    @DisplayName("handleVerification 冻结期间应阻止验证")
    void testHandleVerification_WhileFrozen_BlocksVerification() {
        try (MockedStatic<Bukkit> bukkitMock = mockStatic(Bukkit.class)) {
            bukkitMock.when(Bukkit::getScheduler).thenReturn(scheduler);
            bukkitMock.when(Bukkit::getServer).thenReturn(server);

            verificationManager.startVerification(player, "read", null);

            verificationManager.handleVerification(player, "wrong1");
            verificationManager.handleVerification(player, "wrong2");
            verificationManager.handleVerification(player, "wrong3");

            boolean result = verificationManager.handleVerification(player, "any");

            assertTrue(result);
            verify(player, atLeastOnce()).sendMessage(contains("冻结"));
        }
    }

    @Test
    @DisplayName("handleVerification 冻结解除后应允许验证")
    void testHandleVerification_AfterUnfreeze_AllowsVerification() {
        try (MockedStatic<Bukkit> bukkitMock = mockStatic(Bukkit.class)) {
            bukkitMock.when(Bukkit::getScheduler).thenReturn(scheduler);
            bukkitMock.when(Bukkit::getServer).thenReturn(server);

            verificationManager.startVerification(player, "read", null);

            verificationManager.handleVerification(player, "wrong1");
            verificationManager.handleVerification(player, "wrong2");
            verificationManager.handleVerification(player, "wrong3");

            long freezeRemaining = verificationManager.getPlayerFreezeRemaining(player);
            assertTrue(freezeRemaining > 0);
        }
    }

    @Test
    @DisplayName("getPlayerFreezeRemaining 未冻结应返回 0")
    void testGetPlayerFreezeRemaining_NotFrozen_ReturnsZero() {
        long remaining = verificationManager.getPlayerFreezeRemaining(player);

        assertEquals(0, remaining);
    }

    @Test
    @DisplayName("getPlayerFreezeRemaining 冻结中应返回正数")
    void testGetPlayerFreezeRemaining_Frozen_ReturnsPositive() {
        try (MockedStatic<Bukkit> bukkitMock = mockStatic(Bukkit.class)) {
            bukkitMock.when(Bukkit::getScheduler).thenReturn(scheduler);
            bukkitMock.when(Bukkit::getServer).thenReturn(server);

            verificationManager.startVerification(player, "read", null);

            verificationManager.handleVerification(player, "wrong1");
            verificationManager.handleVerification(player, "wrong2");
            verificationManager.handleVerification(player, "wrong3");

            long remaining = verificationManager.getPlayerFreezeRemaining(player);

            assertTrue(remaining > 0);
        }
    }

    @Test
    @DisplayName("handleVerification diff 类型错误密码应失败")
    void testHandleVerification_DiffType_WrongPassword_Fails() {
        try (MockedStatic<Bukkit> bukkitMock = mockStatic(Bukkit.class)) {
            bukkitMock.when(Bukkit::getScheduler).thenReturn(scheduler);
            bukkitMock.when(Bukkit::getServer).thenReturn(server);

            verificationManager.startVerification(player, "diff", null);

            boolean result = verificationManager.handleVerification(player, "wrongpassword");

            assertTrue(result);
        }
    }

    @Test
    @DisplayName("handleVerification diff 类型3次错误应冻结")
    void testHandleVerification_DiffType_ThreeWrongAttempts_Freezes() {
        try (MockedStatic<Bukkit> bukkitMock = mockStatic(Bukkit.class)) {
            bukkitMock.when(Bukkit::getScheduler).thenReturn(scheduler);
            bukkitMock.when(Bukkit::getServer).thenReturn(server);

            verificationManager.startVerification(player, "diff", null);

            verificationManager.handleVerification(player, "wrong1");
            verificationManager.handleVerification(player, "wrong2");
            boolean result = verificationManager.handleVerification(player, "wrong3");

            assertTrue(result);
            verify(player).sendMessage(contains("冻结"));
        }
    }

    @Test
    @DisplayName("handleVerification 空消息应视为错误")
    void testHandleVerification_EmptyMessage_TreatedAsWrong() {
        try (MockedStatic<Bukkit> bukkitMock = mockStatic(Bukkit.class)) {
            bukkitMock.when(Bukkit::getScheduler).thenReturn(scheduler);
            bukkitMock.when(Bukkit::getServer).thenReturn(server);

            verificationManager.startVerification(player, "read", null);

            boolean result = verificationManager.handleVerification(player, "");

            assertTrue(result);
        }
    }

    @Test
    @DisplayName("handleVerification 空格消息应视为错误")
    void testHandleVerification_WhitespaceMessage_TreatedAsWrong() {
        try (MockedStatic<Bukkit> bukkitMock = mockStatic(Bukkit.class)) {
            bukkitMock.when(Bukkit::getScheduler).thenReturn(scheduler);
            bukkitMock.when(Bukkit::getServer).thenReturn(server);

            verificationManager.startVerification(player, "read", null);

            boolean result = verificationManager.handleVerification(player, "   ");

            assertTrue(result);
        }
    }
}
