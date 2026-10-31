package org.YanPl.manager;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * openai（DeepSeek 端点）→ 独立 deepseek 提供商迁移的单测。
 * 覆盖触发条件与各项排除条件，迁移本体是纯配置操作，不依赖服务端。
 */
class ConfigManagerDeepSeekMigrationTest {

    /** 模拟一份从 4.4.1 升上来的旧配置：openai 路径填了 DeepSeek */
    private YamlConfiguration legacyConfig() {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("version", "4.4.1");
        cfg.set("provider.ai", "openai");
        cfg.set("provider.search", "fancy-tavily");
        cfg.set("provider.jina", "fancy");
        cfg.set("openai.api_url", "https://api.deepseek.com/chat/completions");
        cfg.set("openai.api_key", "sk-real-key-123");
        cfg.set("openai.model", "deepseek-flash");
        cfg.set("deepseek.api_key", "");
        cfg.set("deepseek.model", "deepseek-flash");
        return cfg;
    }

    @Test
    void migratesLegacyOpenAiDeepSeekConfig() {
        YamlConfiguration cfg = legacyConfig();
        assertTrue(ConfigManager.applyOpenAiToDeepSeekMigration(cfg, "4.4.1"));
        assertEquals("deepseek", cfg.getString("provider.ai"));
        assertEquals("sk-real-key-123", cfg.getString("deepseek.api_key"));
        assertEquals("deepseek-flash", cfg.getString("deepseek.model"));
        assertEquals("deepseek", cfg.getString("provider.search"));
        assertEquals("deepseek", cfg.getString("provider.jina"));
        // openai 段保留，不销毁用户数据
        assertEquals("sk-real-key-123", cfg.getString("openai.api_key"));
    }

    @Test
    void skipsWhenConfigVersionCurrent() {
        YamlConfiguration cfg = legacyConfig();
        assertFalse(ConfigManager.applyOpenAiToDeepSeekMigration(cfg, "4.5.0"));
        assertEquals("openai", cfg.getString("provider.ai"));
        assertEquals("fancy-tavily", cfg.getString("provider.search"));
    }

    @Test
    void migratesVersionlessAncientConfig() {
        YamlConfiguration cfg = legacyConfig();
        // 无 version 键视为最老配置，参与迁移
        assertTrue(ConfigManager.applyOpenAiToDeepSeekMigration(cfg, ""));
        assertEquals("deepseek", cfg.getString("provider.ai"));
    }

    @Test
    void skipsWhenEndpointNotDeepSeek() {
        YamlConfiguration cfg = legacyConfig();
        cfg.set("openai.api_url", "https://api.openai.com/v1/chat/completions");
        assertFalse(ConfigManager.applyOpenAiToDeepSeekMigration(cfg, "4.4.1"));
        assertEquals("openai", cfg.getString("provider.ai"));
    }

    @Test
    void skipsWhenProviderNotOpenAi() {
        YamlConfiguration cfg = legacyConfig();
        cfg.set("provider.ai", "fancy");
        assertFalse(ConfigManager.applyOpenAiToDeepSeekMigration(cfg, "4.4.1"));
        assertEquals("fancy-tavily", cfg.getString("provider.search"));
    }

    @Test
    void skipsPlaceholderKey() {
        YamlConfiguration cfg = legacyConfig();
        cfg.set("openai.api_key", "sk-xxxxxxxxxxxxxxxxxxxxxxxxxxxx");
        assertFalse(ConfigManager.applyOpenAiToDeepSeekMigration(cfg, "4.4.1"));
        assertEquals("openai", cfg.getString("provider.ai"));
    }

    @Test
    void skipsEmptyKey() {
        YamlConfiguration cfg = legacyConfig();
        cfg.set("openai.api_key", "");
        assertFalse(ConfigManager.applyOpenAiToDeepSeekMigration(cfg, "4.4.1"));
        assertEquals("openai", cfg.getString("provider.ai"));
    }

    @Test
    void keepsExistingDeepSeekKeyAndEmptyModel() {
        YamlConfiguration cfg = legacyConfig();
        cfg.set("deepseek.api_key", "sk-already-set");
        cfg.set("openai.model", "");
        assertTrue(ConfigManager.applyOpenAiToDeepSeekMigration(cfg, "4.4.1"));
        assertEquals("sk-already-set", cfg.getString("deepseek.api_key"));
        // 模型为空时不覆盖 deepseek 段的默认值
        assertEquals("deepseek-flash", cfg.getString("deepseek.model"));
    }

    @Test
    void shouldMigrateHelperRejectsNulls() {
        assertFalse(ConfigManager.shouldMigrateOpenAiToDeepSeek("4.4.1", null, "https://api.deepseek.com", "sk-x"));
        assertFalse(ConfigManager.shouldMigrateOpenAiToDeepSeek("4.4.1", "openai", null, "sk-x"));
        assertFalse(ConfigManager.shouldMigrateOpenAiToDeepSeek("4.4.1", "openai", "https://api.deepseek.com", null));
    }
}
