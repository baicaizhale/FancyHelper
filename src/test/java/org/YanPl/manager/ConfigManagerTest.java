package org.YanPl.manager;

import org.YanPl.FancyHelper;
import org.YanPl.util.CloudErrorReport;
import org.bukkit.configuration.file.FileConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("ConfigManager 单元测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ConfigManagerTest {

    @Mock
    private FancyHelper plugin;

    @Mock
    private CloudErrorReport cloudErrorReport;

    @Mock
    private FileConfiguration config;

    @Mock
    private FileConfiguration playerData;

    private ConfigManager configManager;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        when(plugin.getLogger()).thenReturn(Logger.getLogger("TestLogger"));
        when(plugin.getCloudErrorReport()).thenReturn(cloudErrorReport);
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.isEnabled()).thenReturn(true);
        
        File dataFolder = tempDir.toFile();
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        
        Files.createDirectories(dataFolder.toPath());

        configManager = new ConfigManager(plugin);
    }

    @Test
    @DisplayName("getCloudflareCfKey 应返回配置值")
    void testGetCloudflareCfKey_ReturnsConfigValue() {
        when(config.getString("cloudflare.cf_key", "")).thenReturn("test-key");

        String result = configManager.getCloudflareCfKey();

        assertEquals("test-key", result);
    }

    @Test
    @DisplayName("getCloudflareModel 应返回配置值")
    void testGetCloudflareModel_ReturnsConfigValue() {
        when(config.getString("cloudflare.model", "@cf/openai/gpt-oss-120b")).thenReturn("test-model");

        String result = configManager.getCloudflareModel();

        assertEquals("test-model", result);
    }

    @Test
    @DisplayName("getAiModel 应返回 getCloudflareModel 的值")
    void testGetAiModel_ReturnsCloudflareModel() {
        when(config.getString("cloudflare.model", "@cf/openai/gpt-oss-120b")).thenReturn("ai-model");

        String result = configManager.getAiModel();

        assertEquals("ai-model", result);
    }

    @Test
    @DisplayName("isOpenAiEnabled 应返回配置值")
    void testIsOpenAiEnabled_ReturnsConfigValue() {
        when(config.getBoolean("openai.enabled", false)).thenReturn(true);

        boolean result = configManager.isOpenAiEnabled();

        assertTrue(result);
    }

    @Test
    @DisplayName("getOpenAiApiUrl 应返回配置值")
    void testGetOpenAiApiUrl_ReturnsConfigValue() {
        when(config.getString("openai.api_url", "https://api.openai.com/v1/chat/completions"))
            .thenReturn("https://custom.api.com");

        String result = configManager.getOpenAiApiUrl();

        assertEquals("https://custom.api.com", result);
    }

    @Test
    @DisplayName("getOpenAiApiKey 应返回配置值")
    void testGetOpenAiApiKey_ReturnsConfigValue() {
        when(config.getString("openai.api_key", "")).thenReturn("sk-test");

        String result = configManager.getOpenAiApiKey();

        assertEquals("sk-test", result);
    }

    @Test
    @DisplayName("getOpenAiModel 应返回配置值")
    void testGetOpenAiModel_ReturnsConfigValue() {
        when(config.getString("openai.model", "gpt-4o")).thenReturn("gpt-4-turbo");

        String result = configManager.getOpenAiModel();

        assertEquals("gpt-4-turbo", result);
    }

    @Test
    @DisplayName("getTimeoutMinutes 应返回配置值")
    void testGetTimeoutMinutes_ReturnsConfigValue() {
        when(config.getInt("settings.timeout_minutes", 10)).thenReturn(30);

        int result = configManager.getTimeoutMinutes();

        assertEquals(30, result);
    }

    @Test
    @DisplayName("getApiTimeoutSeconds 应返回配置值")
    void testGetApiTimeoutSeconds_ReturnsConfigValue() {
        when(config.getInt("settings.api_timeout_seconds", 120)).thenReturn(180);

        int result = configManager.getApiTimeoutSeconds();

        assertEquals(180, result);
    }

    @Test
    @DisplayName("getTokenWarningThreshold 应返回配置值")
    void testGetTokenWarningThreshold_ReturnsConfigValue() {
        when(config.getInt("settings.token_warning_threshold", 500)).thenReturn(1000);

        int result = configManager.getTokenWarningThreshold();

        assertEquals(1000, result);
    }

    @Test
    @DisplayName("isAutoReportEnabled 应返回配置值")
    void testIsAutoReportEnabled_ReturnsConfigValue() {
        when(config.getBoolean("settings.auto_report", true)).thenReturn(false);

        boolean result = configManager.isAutoReportEnabled();

        assertFalse(result);
    }

    @Test
    @DisplayName("isCheckUpdate 应返回配置值")
    void testIsCheckUpdate_ReturnsConfigValue() {
        when(config.getBoolean("settings.check_update", true)).thenReturn(false);

        boolean result = configManager.isCheckUpdate();

        assertFalse(result);
    }

    @Test
    @DisplayName("isOpUpdateNotify 应返回配置值")
    void testIsOpUpdateNotify_ReturnsConfigValue() {
        when(config.getBoolean("settings.op_update_notify", true)).thenReturn(false);

        boolean result = configManager.isOpUpdateNotify();

        assertFalse(result);
    }

    @Test
    @DisplayName("isAutoUpgrade 应返回配置值")
    void testIsAutoUpgrade_ReturnsConfigValue() {
        when(config.getBoolean("settings.auto_upgrade", false)).thenReturn(true);

        boolean result = configManager.isAutoUpgrade();

        assertTrue(result);
    }

    @Test
    @DisplayName("getUpdateMirror 应返回配置值")
    void testGetUpdateMirror_ReturnsConfigValue() {
        when(config.getString("settings.update_mirror", "https://ghproxy.net/"))
            .thenReturn("https://custom.mirror/");

        String result = configManager.getUpdateMirror();

        assertEquals("https://custom.mirror/", result);
    }

    @Test
    @DisplayName("getAntiLoopThresholdCount 应返回配置值")
    void testGetAntiLoopThresholdCount_ReturnsConfigValue() {
        when(config.getInt("settings.anti_loop.threshold_count", 3)).thenReturn(5);

        int result = configManager.getAntiLoopThresholdCount();

        assertEquals(5, result);
    }

    @Test
    @DisplayName("getAntiLoopSimilarityThreshold 应返回配置值")
    void testGetAntiLoopSimilarityThreshold_ReturnsConfigValue() {
        when(config.getDouble("settings.anti_loop.similarity_threshold", 0.8)).thenReturn(0.9);

        double result = configManager.getAntiLoopSimilarityThreshold();

        assertEquals(0.9, result, 0.001);
    }

    @Test
    @DisplayName("getAntiLoopMaxChainCount 应返回配置值")
    void testGetAntiLoopMaxChainCount_ReturnsConfigValue() {
        when(config.getInt("settings.anti_loop.max_chain_count", 10)).thenReturn(20);

        int result = configManager.getAntiLoopMaxChainCount();

        assertEquals(20, result);
    }

    @Test
    @DisplayName("getYoloRiskCommands 应返回配置值")
    void testGetYoloRiskCommands_ReturnsConfigValue() {
        List<String> expected = Arrays.asList("stop", "restart", "reload");
        when(config.getStringList("settings.yolo_risk_commands")).thenReturn(expected);

        List<String> result = configManager.getYoloRiskCommands();

        assertEquals(expected, result);
    }

    @Test
    @DisplayName("isTavilyEnabled 应返回配置值")
    void testIsTavilyEnabled_ReturnsConfigValue() {
        when(config.getBoolean("tavily.enabled", false)).thenReturn(true);

        boolean result = configManager.isTavilyEnabled();

        assertTrue(result);
    }

    @Test
    @DisplayName("getTavilyApiKey 应返回配置值")
    void testGetTavilyApiKey_ReturnsConfigValue() {
        when(config.getString("tavily.api_key", "")).thenReturn("tavily-key");

        String result = configManager.getTavilyApiKey();

        assertEquals("tavily-key", result);
    }

    @Test
    @DisplayName("getTavilyProxyUrl 应返回配置值")
    void testGetTavilyProxyUrl_ReturnsConfigValue() {
        when(config.getString("tavily.proxy_url", "")).thenReturn("https://proxy.com");

        String result = configManager.getTavilyProxyUrl();

        assertEquals("https://proxy.com", result);
    }

    @Test
    @DisplayName("getTavilyMaxResults 应返回配置值")
    void testGetTavilyMaxResults_ReturnsConfigValue() {
        when(config.getInt("tavily.max_results", 5)).thenReturn(10);

        int result = configManager.getTavilyMaxResults();

        assertEquals(10, result);
    }

    @Test
    @DisplayName("isTavilyIncludeRawContent 应返回配置值")
    void testIsTavilyIncludeRawContent_ReturnsConfigValue() {
        when(config.getBoolean("tavily.include_raw_content", false)).thenReturn(true);

        boolean result = configManager.isTavilyIncludeRawContent();

        assertTrue(result);
    }

    @Test
    @DisplayName("isMetasoEnabled 应返回配置值")
    void testIsMetasoEnabled_ReturnsConfigValue() {
        when(config.getBoolean("metaso.enabled", false)).thenReturn(true);

        boolean result = configManager.isMetasoEnabled();

        assertTrue(result);
    }

    @Test
    @DisplayName("getMetasoApiToken 应返回配置值")
    void testGetMetasoApiToken_ReturnsConfigValue() {
        when(config.getString("metaso.api_token", "")).thenReturn("metaso-token");

        String result = configManager.getMetasoApiToken();

        assertEquals("metaso-token", result);
    }

    @Test
    @DisplayName("getMetasoModel 应返回配置值")
    void testGetMetasoModel_ReturnsConfigValue() {
        when(config.getString("metaso.model", "fast")).thenReturn("pro");

        String result = configManager.getMetasoModel();

        assertEquals("pro", result);
    }

    @Test
    @DisplayName("isMetasoConciseSnippet 应返回配置值")
    void testIsMetasoConciseSnippet_ReturnsConfigValue() {
        when(config.getBoolean("metaso.concise_snippet", true)).thenReturn(false);

        boolean result = configManager.isMetasoConciseSnippet();

        assertFalse(result);
    }

    @Test
    @DisplayName("getNoticeRefreshInterval 应返回配置值")
    void testGetNoticeRefreshInterval_ReturnsConfigValue() {
        when(config.getInt("notice.refresh_interval", 5)).thenReturn(10);

        int result = configManager.getNoticeRefreshInterval();

        assertEquals(10, result);
    }

    @Test
    @DisplayName("isNoticeShowOnJoin 应返回配置值")
    void testIsNoticeShowOnJoin_ReturnsConfigValue() {
        when(config.getBoolean("notice.show_on_join", true)).thenReturn(false);

        boolean result = configManager.isNoticeShowOnJoin();

        assertFalse(result);
    }

    @Test
    @DisplayName("getSupplementaryPrompt 应返回配置值")
    void testGetSupplementaryPrompt_ReturnsConfigValue() {
        when(config.getString("settings.supplementary_prompt", "")).thenReturn("custom prompt");

        String result = configManager.getSupplementaryPrompt();

        assertEquals("custom prompt", result);
    }

    @Test
    @DisplayName("isDebug 应返回配置值")
    void testIsDebug_ReturnsConfigValue() {
        when(config.getBoolean("settings.debug", false)).thenReturn(true);

        boolean result = configManager.isDebug();

        assertTrue(result);
    }

    @Test
    @DisplayName("isMeowEnabled 应返回配置值")
    void testIsMeowEnabled_ReturnsConfigValue() {
        when(config.getBoolean("settings.meow", false)).thenReturn(true);

        boolean result = configManager.isMeowEnabled();

        assertTrue(result);
    }

    @Test
    @DisplayName("getPlayerData 应返回配置对象")
    void testGetPlayerData_ReturnsConfigObject() {
        FileConfiguration result = configManager.getPlayerData();

        assertNotNull(result);
    }

    @Test
    @DisplayName("isToolEnabled 应返回配置值")
    void testIsToolEnabled_ReturnsConfigValue() {
        when(config.getBoolean("tools.test_tool", false)).thenReturn(true);

        boolean result = configManager.isToolEnabled("test_tool");

        assertTrue(result);
    }

    @Test
    @DisplayName("setToolEnabled 应设置配置值")
    void testSetToolEnabled_SetsConfigValue() {
        configManager.setToolEnabled("test_tool", true);

        verify(config).set("tools.test_tool", true);
    }

    @Test
    @DisplayName("isToolEnabled 默认应返回 false")
    void testIsToolEnabled_Default_ReturnsFalse() {
        when(config.getBoolean("tools.unknown", false)).thenReturn(false);

        boolean result = configManager.isToolEnabled("unknown");

        assertFalse(result);
    }

    @Test
    @DisplayName("getUpdateMirror 默认值")
    void testGetUpdateMirror_DefaultValue() {
        when(config.getString("settings.update_mirror", "https://ghproxy.net/"))
            .thenReturn("https://ghproxy.net/");

        String result = configManager.getUpdateMirror();

        assertEquals("https://ghproxy.net/", result);
    }

    @Test
    @DisplayName("getTavilyMaxResults 边界值 1")
    void testGetTavilyMaxResults_BoundaryValue1() {
        when(config.getInt("tavily.max_results", 5)).thenReturn(1);

        int result = configManager.getTavilyMaxResults();

        assertEquals(1, result);
    }

    @Test
    @DisplayName("getTavilyMaxResults 边界值 10")
    void testGetTavilyMaxResults_BoundaryValue10() {
        when(config.getInt("tavily.max_results", 5)).thenReturn(10);

        int result = configManager.getTavilyMaxResults();

        assertEquals(10, result);
    }

    @Test
    @DisplayName("getAntiLoopSimilarityThreshold 边界值 0.0")
    void testGetAntiLoopSimilarityThreshold_Boundary0() {
        when(config.getDouble("settings.anti_loop.similarity_threshold", 0.8)).thenReturn(0.0);

        double result = configManager.getAntiLoopSimilarityThreshold();

        assertEquals(0.0, result, 0.001);
    }

    @Test
    @DisplayName("getAntiLoopSimilarityThreshold 边界值 1.0")
    void testGetAntiLoopSimilarityThreshold_Boundary1() {
        when(config.getDouble("settings.anti_loop.similarity_threshold", 0.8)).thenReturn(1.0);

        double result = configManager.getAntiLoopSimilarityThreshold();

        assertEquals(1.0, result, 0.001);
    }

    @Test
    @DisplayName("save 应保存配置文件")
    void testSave_SavesConfig() throws IOException {
        configManager.save();

        verify(config).save(any(File.class));
    }
}
