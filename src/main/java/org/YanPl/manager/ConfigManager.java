package org.YanPl.manager;

import org.YanPl.MineAgent;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * 配置管理器，负责加载和保存插件配置
 */
public class ConfigManager {
    private final MineAgent plugin;
    private FileConfiguration config;

    public ConfigManager(MineAgent plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    /**
     * 加载配置文件
     */
    public void loadConfig() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        this.config = plugin.getConfig();
    }

    /**
     * 获取 CloudFlare API Token (cf_key)
     */
    public String getCloudflareCfKey() {
        return config.getString("cloudflare.cf_key", "");
    }

    /**
     * 获取 AI 模型名称
     */
    public String getCloudflareModel() {
        return config.getString("cloudflare.model", "@cf/meta/llama-3-8b-instruct");
    }

    /**
     * 获取腾讯云 Secret ID
     */
    public String getTencentSecretId() {
        return config.getString("search.tencent_secret_id", "");
    }

    /**
     * 获取腾讯云 Secret Key
     */
    public String getTencentSecretKey() {
        return config.getString("search.tencent_secret_key", "");
    }

    /**
     * 获取超时分钟数
     */
    public int getTimeoutMinutes() {
        return config.getInt("settings.timeout_minutes", 10);
    }

    /**
     * 获取 Token 警告阈值
     */
    public int getTokenWarningThreshold() {
        return config.getInt("settings.token_warning_threshold", 500);
    }
}
