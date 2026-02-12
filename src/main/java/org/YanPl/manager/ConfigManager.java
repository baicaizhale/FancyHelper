package org.YanPl.manager;

import org.YanPl.FancyHelper;
import org.YanPl.util.ResourceUtil;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConfigManager {
    /**
     * 配置管理器：负责检测配置版本、更新默认配置、加载配置并提供便捷的 getter。
     */
    private final FancyHelper plugin;
    private FileConfiguration config;
    private FileConfiguration playerData;
    private File playerDataFile;

    public ConfigManager(FancyHelper plugin) {
        this.plugin = plugin;
        checkAndUpdateConfig();
        loadConfig();
        loadPlayerData();
    }

    /**
     * 加载玩家数据配置文件
     */
    public void loadPlayerData() {
        playerDataFile = new File(plugin.getDataFolder(), "playerdata.yml");
        if (!playerDataFile.exists()) {
            try {
                playerDataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("无法创建 playerdata.yml: " + e.getMessage());
            }
        }
        playerData = YamlConfiguration.loadConfiguration(playerDataFile);
    }

    /**
     * 保存玩家数据配置文件
     */
    public void savePlayerData() {
        try {
            playerData.save(playerDataFile);
        } catch (IOException e) {
            plugin.getLogger().warning("无法保存玩家数据: " + e.getMessage());
        }
    }

    /**
     * 检查并更新配置文件版本。
     * 当插件版本与配置版本不一致时，备份旧配置并迁移配置项。
     */
    private void checkAndUpdateConfig() {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        // 如果尚无配置文件则直接返回（后续 saveDefaultConfig 会生成）
        if (!configFile.exists()) {
            return;
        }

        FileConfiguration currentConfig = YamlConfiguration.loadConfiguration(configFile);
        String configVersion = currentConfig.getString("version", "");
        String pluginVersion = plugin.getDescription().getVersion();

        if (!configVersion.equals(pluginVersion)) {
            plugin.getLogger().info("检测到版本更新 (" + configVersion + " -> " + pluginVersion + ")，正在更新配置...");

            // 1. 保存为 config.yml.old
            File oldConfigFile = new File(plugin.getDataFolder(), "config.yml.old");
            try {
                java.nio.file.Files.copy(configFile.toPath(), oldConfigFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                plugin.getLogger().warning("备份旧配置文件失败: " + e.getMessage());
            }

            // 版本更新时强制更新 EULA 和 License
            if (plugin.getEulaManager() != null) {
                plugin.getEulaManager().forceReplaceFiles();
            }

            Map<String, Object> oldValues = new HashMap<>();
            for (String key : currentConfig.getKeys(true)) {
                // 仅迁移具体的配置项值，避免覆盖整个配置节导致新版本增加的默认项丢失
                if (!key.equals("version") && !currentConfig.isConfigurationSection(key)) {
                    oldValues.put(key, currentConfig.get(key));
                }
            }

            // 2. 释放新配置
            configFile.delete();
            File presetDir = new File(plugin.getDataFolder(), "preset");
            if (presetDir.exists()) {
                deleteDirectory(presetDir);
            }

            plugin.saveDefaultConfig();
            ResourceUtil.releaseResources(plugin, "preset/", true, ".txt");

            // 3. 把旧配置写入新配置
            FileConfiguration newConfig = YamlConfiguration.loadConfiguration(configFile);
            for (Map.Entry<String, Object> entry : oldValues.entrySet()) {
                if (newConfig.contains(entry.getKey())) {
                    newConfig.set(entry.getKey(), entry.getValue());
                }
            }
            
            try {
                newConfig.save(configFile);
                plugin.getLogger().info("配置文件更新完成！");
            } catch (IOException e) {
                plugin.getLogger().severe("保存新配置文件时出错: " + e.getMessage());
                plugin.getCloudErrorReport().report(e);
            }

            // 4. 把 config.yml.old 丢进 plugins\FancyHelper\old
            File oldDir = new File(plugin.getDataFolder(), "old");
            if (!oldDir.exists()) {
                oldDir.mkdirs();
            }
            File finalOldFile = new File(oldDir, "config.yml.old");
            if (oldConfigFile.exists()) {
                if (finalOldFile.exists()) {
                    finalOldFile.delete();
                }
                if (oldConfigFile.renameTo(finalOldFile)) {
                    plugin.getLogger().info("旧配置文件已移动至 " + finalOldFile.getPath());
                } else {
                    plugin.getLogger().warning("无法移动旧配置文件到 old 目录。");
                }
            }
        }
    }

    private void deleteDirectory(File directory) {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }
        // 删除目录自身
        directory.delete();
    }

    public void loadConfig() {
        // 确保存在默认配置并读取
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        this.config = plugin.getConfig();

        // 清理 config.yml 中可能存在的旧玩家数据（迁移到 playerdata.yml 后）
        if (config.contains("player_tools")) {
            config.set("player_tools", null);
            save();
            plugin.getLogger().info("已从 config.yml 中移除旧的玩家工具权限数据。");
        }
    }

    public String getCloudflareCfKey() {
        return config.getString("cloudflare.cf_key", "");
    }

    public String getCloudflareModel() {
        return config.getString("cloudflare.model", "@cf/openai/gpt-oss-120b");
    }

    public String getAiModel() {
        return getCloudflareModel();
    }

    /**
     * 获取是否启用 OpenAI 模式
     * @return 是否启用 OpenAI 模式
     */
    public boolean isOpenAiEnabled() {
        return config.getBoolean("openai.enabled", false);
    }

    /**
     * 获取 OpenAI API URL
     * @return OpenAI API URL
     */
    public String getOpenAiApiUrl() {
        return config.getString("openai.api_url", "https://api.openai.com/v1/chat/completions");
    }

    /**
     * 获取 OpenAI API 密钥
     * @return OpenAI API 密钥
     */
    public String getOpenAiApiKey() {
        return config.getString("openai.api_key", "");
    }

    /**
     * 获取 OpenAI 模型名称
     * @return OpenAI 模型名称
     */
    public String getOpenAiModel() {
        return config.getString("openai.model", "gpt-4o");
    }

    public int getTimeoutMinutes() {
        return config.getInt("settings.timeout_minutes", 10);
    }

    /**
     * 获取 AI API 请求超时时间（秒）
     * @return 超时时间（秒）
     */
    public int getApiTimeoutSeconds() {
        return config.getInt("settings.api_timeout_seconds", 120);
    }

    public int getTokenWarningThreshold() {
        return config.getInt("settings.token_warning_threshold", 500);
    }

    public boolean isAutoReportEnabled() {
        return config.getBoolean("settings.auto_report", true);
    }

    public boolean isCheckUpdate() {
        return config.getBoolean("settings.check_update", true);
    }

    public boolean isOpUpdateNotify() {
        return config.getBoolean("settings.op_update_notify", true);
    }

    /**
     * 获取是否开启自动升级
     * @return 是否开启自动升级
     */
    public boolean isAutoUpgrade() {
        return config.getBoolean("settings.auto_upgrade", false);
    }

    public String getUpdateMirror() {
        return config.getString("settings.update_mirror", "https://ghproxy.net/");
    }

    /**
     * 获取防循环检测的连续相似调用阈值
     * @return 连续相似调用阈值
     */
    public int getAntiLoopThresholdCount() {
        return config.getInt("settings.anti_loop.threshold_count", 3);
    }

    /**
     * 获取防循环检测的相似度阈值
     * @return 相似度阈值 (0.0 - 1.0)
     */
    public double getAntiLoopSimilarityThreshold() {
        return config.getDouble("settings.anti_loop.similarity_threshold", 0.8);
    }

    /**
     * 获取单次对话中连续调用工具的最大次数
     * @return 最大连续调用次数
     */
    public int getAntiLoopMaxChainCount() {
        return config.getInt("settings.anti_loop.max_chain_count", 10);
    }

    public List<String> getYoloRiskCommands() {
        return config.getStringList("settings.yolo_risk_commands");
    }

    /**
     * 获取是否在玩家加入时显示公告
     * @return 是否在玩家加入时显示公告
     */
    /**
     * 获取公告刷新间隔（分钟）
     * @return 刷新间隔
     */
    public int getNoticeRefreshInterval() {
        return config.getInt("notice.refresh_interval", 5);
    }

    /**
     * 获取公告是否在加入时显示
     * @return 是否显示
     */
    public boolean isNoticeShowOnJoin() {
        return config.getBoolean("notice.show_on_join", true);
    }

    /**
     * 获取补充系统提示词
     * @return 补充系统提示词
     */
    public String getSupplementaryPrompt() {
        return config.getString("settings.supplementary_prompt", "");
    }

    /**
     * 获取玩家数据配置对象
     * @return FileConfiguration
     */
    public FileConfiguration getPlayerData() {
        return playerData;
    }

    public boolean isToolEnabled(String tool) {
        return config.getBoolean("tools." + tool, false);
    }

    public void setToolEnabled(String tool, boolean enabled) {
        config.set("tools." + tool, enabled);
        save();
    }

    public boolean isPlayerToolEnabled(org.bukkit.entity.Player player, String tool) {
        String path = player.getUniqueId() + "." + tool;
        return playerData.getBoolean(path, false);
    }

    public void setPlayerToolEnabled(org.bukkit.entity.Player player, String tool, boolean enabled) {
        String path = player.getUniqueId() + "." + tool;
        playerData.set(path, enabled);
        savePlayerData();
    }

    public String getPlayerDisplayPosition(org.bukkit.entity.Player player) {
        String path = player.getUniqueId() + ".display_position";
        return playerData.getString(path, "actionbar");
    }

    public void setPlayerDisplayPosition(org.bukkit.entity.Player player, String position) {
        String path = player.getUniqueId() + ".display_position";
        playerData.set(path, position);
        savePlayerData();
    }

    public void save() {
        try {
            config.save(new File(plugin.getDataFolder(), "config.yml"));
        } catch (IOException e) {
            plugin.getLogger().warning("无法保存配置: " + e.getMessage());
        }
    }
}
