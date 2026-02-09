package org.YanPl.manager;

import org.YanPl.FancyHelper;
import org.YanPl.util.ResourceUtil;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
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

            // 版本更新时强制更新 EULA 和 License
            if (plugin.getEulaManager() != null) {
                plugin.getEulaManager().forceReplaceFiles();
            }

            Map<String, Object> oldValues = new HashMap<>();
            for (String key : currentConfig.getKeys(true)) {
                if (!key.equals("version")) {
                    oldValues.put(key, currentConfig.get(key));
                }
            }

            configFile.delete();
            File presetDir = new File(plugin.getDataFolder(), "preset");
            if (presetDir.exists()) {
                deleteDirectory(presetDir);
            }

            plugin.saveDefaultConfig();
            ResourceUtil.releaseResources(plugin, "preset/", true, ".txt");

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

    public int getTimeoutMinutes() {
        return config.getInt("settings.timeout_minutes", 10);
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

    public String getUpdateMirror() {
        return config.getString("settings.update_mirror", "https://ghproxy.net/");
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

    private void save() {
        try {
            config.save(new File(plugin.getDataFolder(), "config.yml"));
        } catch (IOException e) {
            plugin.getLogger().warning("无法保存配置文件: " + e.getMessage());
        }
    }
}
