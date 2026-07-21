package org.YanPl.manager;

import org.YanPl.FancyHelper;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

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
    private boolean configLoadFailed = false;
    private String configLoadError = "";

    private static final String SKILL_PRIMARY_MIRROR = "https://fancy-skill.baicaizhale.top/";
    private static final String SKILL_REPO_BASE = "https://raw.githubusercontent.com/baicaizhale/FancySkillMarket/main/";
    private static final String SKILL_UPDATE_MIRROR = "https://ghproxy.vip/";

    // 插件更新相关
    private static final String PLUGIN_VERSION_API = "https://fancy-version.baicaizhale.top/api/plugin/latest";
    private static final String PLUGIN_CDN_BASE = "https://fancy.baicaizhale.top/";

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

        // 首次安装：释放 lib JAR 后返回
        if (!configFile.exists()) {
            plugin.extractLibJar();
            return;
        }

        FileConfiguration currentConfig;
        try {
            currentConfig = YamlConfiguration.loadConfiguration(configFile);
        } catch (Exception e) {
            plugin.getLogger().severe("config.yml 格式错误，跳过版本检查: " + e.getMessage());
            return;
        }
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

            plugin.saveDefaultConfig();

            // 3. 把旧配置写入新配置
            FileConfiguration newConfig = YamlConfiguration.loadConfiguration(configFile);
            for (Map.Entry<String, Object> entry : oldValues.entrySet()) {
                if (newConfig.contains(entry.getKey())) {
                    newConfig.set(entry.getKey(), entry.getValue());
                }
            }

            // 迁移旧版 openai.enabled 到新版 provider 字段
            if (oldValues.containsKey("openai.enabled")) {
                boolean openaiWasEnabled = Boolean.parseBoolean(String.valueOf(oldValues.get("openai.enabled")));
                newConfig.set("provider", openaiWasEnabled ? "openai" : "cloudflare");
                plugin.getLogger().info("已迁移旧配置 openai.enabled=" + openaiWasEnabled + " 到 provider=" + (openaiWasEnabled ? "openai" : "cloudflare"));
            }

            // 迁移旧版 token_warning_threshold 到 context_window_warning_threshold
            if (oldValues.containsKey("settings.token_warning_threshold")) {
                Object oldVal = oldValues.get("settings.token_warning_threshold");
                newConfig.set("settings.context_window_warning_threshold", oldVal);
                plugin.getLogger().info("已迁移旧配置 settings.token_warning_threshold=" + oldVal + " 到 settings.context_window_warning_threshold=" + oldVal);
            }

            // 迁移旧版 co-model 结构到 cloudflare.co-model / openai.co-model
            if (oldValues.containsKey("co-model.cloudflare.model")) {
                Object oldCfCo = oldValues.get("co-model.cloudflare.model");
                newConfig.set("cloudflare.co-model", oldCfCo);
                plugin.getLogger().info("已迁移旧配置 co-model.cloudflare.model=" + oldCfCo + " 到 cloudflare.co-model=" + oldCfCo);
            }
            if (oldValues.containsKey("co-model.openai.model")) {
                Object oldOpenAiCo = oldValues.get("co-model.openai.model");
                newConfig.set("openai.co-model", oldOpenAiCo);
                plugin.getLogger().info("已迁移旧配置 co-model.openai.model=" + oldOpenAiCo + " 到 openai.co-model=" + oldOpenAiCo);
            }
            // 清理旧版 co-model 顶层段落
            if (newConfig.contains("co-model")) {
                newConfig.set("co-model", null);
                plugin.getLogger().info("已清除旧版 co-model 配置段落");
            }

            try {
                newConfig.save(configFile);
                // 删除旧的 lib JAR，再释放最新的 ReloadService JAR
            File oldLibJar = new File(plugin.getDataFolder(), "lib/FancyHelperReloadService.jar");
            if (oldLibJar.exists() && !oldLibJar.delete()) {
                // 文件被锁定（ReloadService 仍在运行），延后到它关闭后再操作
                plugin.getLogger().info("无法删除旧 ReloadService JAR（文件被占用），将在延迟后重试...");
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    waitForReloadServiceDisabled();
                    oldLibJar.delete();
                    plugin.extractLibJar();
                    plugin.getLogger().info("ReloadService JAR 已更新");
                }, 80L);
            } else {
                plugin.extractLibJar();
            }
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

    /**
     * 等待 ReloadService 插件完全卸载（最长 15 秒），用于确保其 JAR 文件不再被锁定。
     */
    private void waitForReloadServiceDisabled() {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < 15000) {
            org.bukkit.plugin.Plugin reloadService = plugin.getServer().getPluginManager().getPlugin("FancyHelperReloadService");
            if (reloadService == null || !reloadService.isEnabled()) {
                return;
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        plugin.getLogger().warning("等待 ReloadService 关闭超时，将尝试强制释放 JAR");
    }

    public void loadConfig() {
        configLoadFailed = false;
        configLoadError = "";

        // 确保存在默认配置并读取
        plugin.saveDefaultConfig();
        try {
            plugin.reloadConfig();
        } catch (Exception e) {
            configLoadFailed = true;
            configLoadError = e.getMessage();
            plugin.getLogger().severe("config.yml 格式错误，无法加载配置文件: " + e.getMessage());
            if (config == null) {
                // 首次加载失败时，回退到默认空配置
                config = plugin.getConfig();
            }
            // 保留旧 config 对象不变，避免下游读取到空值产生误导性错误
            return;
        }
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

    public String getCloudflareProxyUrl() {
        return config.getString("cloudflare.proxy_url", "");
    }

    public String getCloudflareModel() {
        return config.getString("cloudflare.model", "@cf/openai/gpt-oss-120b");
    }

    public String getAiModel() {
        return getCloudflareModel();
    }

    /**
     * 获取压缩模型提供商（跟随主模型提供商）
     * @return 压缩模型提供商 (cloudflare 或 openai)
     */
    public String getCompressionModelProvider() {
        return getProvider();
    }

    /**
     * 获取 CloudFlare 压缩模型名称
     * @return CloudFlare 压缩模型名称
     */
    public String getCompressionCloudflareModel() {
        return config.getString("cloudflare.co-model", "@cf/google/gemma-4b-it");
    }

    /**
     * 获取 OpenAI 压缩模型名称
     * @return OpenAI 压缩模型名称
     */
    public String getCompressionOpenAiModel() {
        return config.getString("openai.co-model", "gpt-4o-mini");
    }

    /**
     * 获取 AI 提供商
     * @return AI 提供商名称 (cloudflare 或 openai)
     */
    public String getProvider() {
        return config.getString("provider", "cloudflare");
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

    public int getContextWindowWarningThreshold() {
        return config.getInt("settings.context_window_warning_threshold", 500);
    }

    /**
     * 获取上下文窗口大小上限
     * @return 上下文窗口大小上限（token数）
     */
    public int getContextWindowLimit() {
        return config.getInt("settings.context_window_limit", 12800);
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

    /**
     * 获取 Skill 主下载源
     */
    public String getSkillPrimaryMirror() {
        return SKILL_PRIMARY_MIRROR;
    }

    /**
     * 获取插件版本信息 API 地址
     */
    public String getPluginVersionApi() {
        return PLUGIN_VERSION_API;
    }

    /**
     * 获取插件 CDN 下载基地址
     */
    public String getPluginCdnBase() {
        return PLUGIN_CDN_BASE;
    }

    /**
     * 获取 Skill 远程仓库地址
     */
    public String getSkillRepoBase() {
        return SKILL_REPO_BASE;
    }

    /**
     * 获取 Skill 下载镜像源（旧 ghproxy 留底）
     */
    public String getSkillUpdateMirror() {
        return SKILL_UPDATE_MIRROR;
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
     * 获取是否启用 Tavily 搜索
     * @return 是否启用
     */
    public boolean isTavilyEnabled() {
        return config.getBoolean("tavily.enabled", false);
    }

    /**
     * 获取 Tavily API 密钥
     * @return API 密钥
     */
    public String getTavilyApiKey() {
        return config.getString("tavily.api_key", "");
    }

    /**
     * 获取 Tavily API 代理地址
     * @return 代理地址（如为空则使用官方地址）
     */
    public String getTavilyProxyUrl() {
        return config.getString("tavily.proxy_url", "");
    }

    /**
     * 获取 Tavily 搜索结果数量限制
     * @return 结果数量限制（1-10）
     */
    public int getTavilyMaxResults() {
        return config.getInt("tavily.max_results", 5);
    }

    /**
     * 获取 Tavily 是否包含原始内容
     * @return 是否包含原始内容
     */
    public boolean isTavilyIncludeRawContent() {
        return config.getBoolean("tavily.include_raw_content", false);
    }

    /**
     * 获取是否启用 Metaso AI 搜索
     * @return 是否启用
     */
    public boolean isMetasoEnabled() {
        return config.getBoolean("metaso.enabled", false);
    }

    /**
     * 获取 Metaso API 令牌
     * @return API 令牌
     */
    public String getMetasoApiToken() {
        return config.getString("metaso.api_token", "");
    }

    /**
     * 获取 Metaso 模型名称
     * @return 模型名称（fast 或 pro）
     */
    public String getMetasoModel() {
        return config.getString("metaso.model", "fast");
    }

    /**
     * 获取 Metaso 是否启用简洁摘要模式
     * @return 是否启用简洁摘要
     */
    public boolean isMetasoConciseSnippet() {
        return config.getBoolean("metaso.concise_snippet", true);
    }

    /**
     * 获取公告刷新间隔（分钟）
     * @return 刷新间隔
     */
    public int getNoticeRefreshInterval() {
        return config.getInt("notice.refresh_interval", 5);
    }

    /**
     * 获取补充系统提示词
     * @return 补充系统提示词
     */
    public String getSupplementaryPrompt() {
        return config.getString("settings.supplementary_prompt", "");
    }

    /**
     * 获取是否启用调试模式
     * @return 是否启用调试模式
     */
    public boolean isDebug() {
        return config.getBoolean("settings.debug", false);
    }

    /**
     * 获取日志保留天数
     * @return 日志保留天数
     */
    public int getLogRetentionDays() {
        return config.getInt("settings.log_retention_days", 15);
    }

    /**
     * 获取是否启用猫娘模式
     * @return 是否启用猫娘模式
     */
    public boolean isMeowEnabled() {
        return config.getBoolean("settings.meow", false);
    }

    /**
     * 获取是否启用流式输出（全局配置默认值）
     * @return 是否启用流式输出
     */
    public boolean isStreamingEnabled() {
        return config.getBoolean("settings.streaming", true);
    }

    /**
     * 获取玩家个人的流式输出设置
     * @param player 玩家
     * @return 玩家是否启用了流式输出（如未设置过则返回全局默认值）
     */
    public boolean isPlayerStreamingEnabled(Player player) {
        String path = player.getUniqueId() + ".streaming";
        if (playerData.contains(path)) {
            return playerData.getBoolean(path);
        }
        return isStreamingEnabled();
    }

    /**
     * 设置玩家个人的流式输出
     * @param player 玩家
     * @param enabled 是否启用
     */
    public void setPlayerStreamingEnabled(Player player, boolean enabled) {
        String path = player.getUniqueId() + ".streaming";
        playerData.set(path, enabled);
        savePlayerData();
    }

    /**
     * 获取 SMART 模式的风险阈值
     * @return 风险阈值（0-100）
     */
    public int getSmartRiskThreshold() {
        return config.getInt("settings.smart_risk_threshold", 50);
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
        String uuid = player.getUniqueId().toString();
        String lower = tool.toLowerCase();

        // 映射到 read/write 两大权限组
        String group = switch (lower) {
            case "ls", "read" -> "read";
            case "edit", "diff", "write" -> "write";
            default -> lower;
        };

        // write 权限隐含 read
        if ("read".equals(group) && playerData.getBoolean(uuid + ".write", false)) {
            return true;
        }

        // 检查组权限
        if (playerData.getBoolean(uuid + "." + group, false)) return true;

        // 旧版兼容：检查独立工具条目
        return playerData.getBoolean(uuid + "." + lower, false);
    }

    public void setPlayerToolEnabled(org.bukkit.entity.Player player, String tool, boolean enabled) {
        String uuid = player.getUniqueId().toString();
        String lower = tool.toLowerCase();

        // 映射到 read/write 两大权限组
        String group = switch (lower) {
            case "ls", "read" -> "read";
            case "edit", "diff", "write" -> "write";
            default -> lower;
        };

        if ("write".equals(group) && enabled) {
            // write 同时授予 read
            playerData.set(uuid + ".read", true);
        }

        playerData.set(uuid + "." + group, enabled);
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

    // ========== 声音反馈配置 ==========

    public boolean isSoundEnabled() {
        return config.getBoolean("sounds.enabled", true);
    }

    public String getSoundAiComplete() {
        return config.getString("sounds.ai_complete", "block.note_block.hat");
    }

    public String getSoundAiError() {
        return config.getString("sounds.ai_error", "block.note_block.bass");
    }

    public String getSoundCliEnter() {
        return config.getString("sounds.cli_enter", "block.note_block.chime");
    }

    public String getSoundCliExit() {
        return config.getString("sounds.cli_exit", "block.note_block.bell");
    }

    public String getSoundUserInput() {
        return config.getString("sounds.user_input", "block.wooden_button.click_on");
    }

    public boolean isPlayerSoundDisabled(java.util.UUID uuid) {
        return playerData.getBoolean("sound_disabled." + uuid.toString(), false);
    }

    public void setPlayerSoundDisabled(java.util.UUID uuid, boolean disabled) {
        playerData.set("sound_disabled." + uuid.toString(), disabled);
        savePlayerData();
    }

    // ========== MCP Client 配置 ==========

    public boolean isMcpClientEnabled() {
        return config.getBoolean("mcp.client.enabled", false);
    }

    public int getMcpClientCallTimeout() {
        return config.getInt("mcp.client.call_timeout", 30);
    }

    public int getMcpClientConnectTimeout() {
        return config.getInt("mcp.client.connect_timeout", 10);
    }

    public int getMcpClientReconnectInterval() {
        return config.getInt("mcp.client.reconnect_interval", 5);
    }

    public List<Map<?, ?>> getMcpClientServers() {
        List<?> list = config.getList("mcp.client.servers");
        if (list == null) return null;
        List<Map<?, ?>> result = new java.util.ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map) {
                result.add((Map<?, ?>) item);
            }
        }
        return result;
    }

    public boolean isConfigLoadFailed() {
        return configLoadFailed;
    }

    public String getConfigLoadError() {
        return configLoadError;
    }

    public void save() {
        try {
            config.save(new File(plugin.getDataFolder(), "config.yml"));
        } catch (IOException e) {
            plugin.getLogger().warning("无法保存配置: " + e.getMessage());
        }
    }
}
