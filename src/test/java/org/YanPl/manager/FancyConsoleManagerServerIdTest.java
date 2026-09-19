package org.YanPl.manager;

import org.YanPl.FancyHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * server-id 是统计上报的唯一身份（上报不要求注册），构造函数必须保证它存在。
 */
@DisplayName("FancyConsoleManager server-id 生成测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FancyConsoleManagerServerIdTest {

    @Mock
    private FancyHelper plugin;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        when(plugin.getLogger()).thenReturn(Logger.getLogger("TestLogger"));
        when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
    }

    private Path configFile() {
        return tempDir.resolve("client-fancy.yml");
    }

    @Test
    @DisplayName("首次启动应生成合法 UUID 的 server-id")
    void testServerIdGeneratedOnFirstRun() {
        FancyConsoleManager manager = new FancyConsoleManager(plugin);

        assertDoesNotThrow(() -> UUID.fromString(manager.getServerId()));
        assertTrue(Files.exists(configFile()));
    }

    @Test
    @DisplayName("server-id 缺失时应补齐而不是留空")
    void testServerIdHealedWhenMissing() throws Exception {
        Files.writeString(configFile(), "api-key: \"\"\n");

        FancyConsoleManager manager = new FancyConsoleManager(plugin);

        assertDoesNotThrow(() -> UUID.fromString(manager.getServerId()));
        assertTrue(Files.readString(configFile()).contains("server-id"));
    }

    @Test
    @DisplayName("已有 server-id 时应原样保留，不覆盖")
    void testExistingServerIdPreserved() throws Exception {
        String existing = "11111111-2222-3333-4444-555555555555";
        Files.writeString(configFile(), "server-id: " + existing + "\napi-key: \"\"\n");

        FancyConsoleManager manager = new FancyConsoleManager(plugin);

        assertEquals(existing, manager.getServerId());
    }

    @Test
    @DisplayName("reload 后 server-id 被手工删除也应补齐")
    void testServerIdHealedOnReload() throws Exception {
        FancyConsoleManager manager = new FancyConsoleManager(plugin);
        String original = manager.getServerId();
        Files.writeString(configFile(), "api-key: \"\"\n");

        manager.reload();

        assertDoesNotThrow(() -> UUID.fromString(manager.getServerId()));
        assertNotEquals(original, manager.getServerId());
    }

    @Test
    @DisplayName("API Key 为空时 isReady 为 false，但不影响 server-id 存在")
    void testServerIdIndependentOfApiKey() {
        FancyConsoleManager manager = new FancyConsoleManager(plugin);

        assertFalse(manager.isReady());
        assertFalse(manager.hasApiKey());
        assertNotNull(manager.getServerId());
    }
}
