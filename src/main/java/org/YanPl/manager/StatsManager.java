package org.YanPl.manager;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.YanPl.FancyHelper;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SingleLineChart;
import org.bukkit.Bukkit;
import org.bukkit.Server;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.concurrent.atomic.AtomicLong;

public class StatsManager {
    private final FancyHelper plugin;
    private final AtomicLong totalInputTokens = new AtomicLong(0);
    private final AtomicLong totalOutputTokens = new AtomicLong(0);
    private final AtomicLong cliEntryCount = new AtomicLong(0);
    private final AtomicLong conversationCount = new AtomicLong(0);
    private final AtomicLong toolSuccessCount = new AtomicLong(0);
    private final AtomicLong toolFailureCount = new AtomicLong(0);
    private final AtomicLong errorCount = new AtomicLong(0);
    private final AtomicLong totalThinkingTimeMs = new AtomicLong(0);
    private final Gson gson = new Gson();
    private final File dataFile;
    private final Object saveLock = new Object();
    private final long pluginStartTime;

    public StatsManager(FancyHelper plugin, Metrics metrics) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "stats/data.json");
        this.pluginStartTime = System.currentTimeMillis();
        load();
        registerCharts(metrics);
        startAutoSave();
        scheduleNextReport();
    }

    // ==================== 持久化 ====================

    private void startAutoSave() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::save, 6000L, 6000L);
    }

    private void load() {
        if (!dataFile.exists()) return;
        try (FileReader reader = new FileReader(dataFile)) {
            JsonObject json = gson.fromJson(reader, JsonObject.class);
            if (json != null) {
                if (json.has("totalInputTokens")) totalInputTokens.set(json.get("totalInputTokens").getAsLong());
                if (json.has("totalOutputTokens")) totalOutputTokens.set(json.get("totalOutputTokens").getAsLong());
                if (json.has("cliEntryCount")) cliEntryCount.set(json.get("cliEntryCount").getAsLong());
                if (json.has("conversationCount")) conversationCount.set(json.get("conversationCount").getAsLong());
                if (json.has("toolSuccessCount")) toolSuccessCount.set(json.get("toolSuccessCount").getAsLong());
                if (json.has("toolFailureCount")) toolFailureCount.set(json.get("toolFailureCount").getAsLong());
                if (json.has("errorCount")) errorCount.set(json.get("errorCount").getAsLong());
                if (json.has("totalThinkingTimeMs")) totalThinkingTimeMs.set(json.get("totalThinkingTimeMs").getAsLong());
            }
        } catch (IOException e) {
            plugin.getLogger().warning("[StatsManager] 加载统计数据失败: " + e.getMessage());
        }
    }

    public void save() {
        synchronized (saveLock) {
            try {
                if (!dataFile.getParentFile().exists()) {
                    dataFile.getParentFile().mkdirs();
                }
                JsonObject json = new JsonObject();
                json.addProperty("totalInputTokens", totalInputTokens.get());
                json.addProperty("totalOutputTokens", totalOutputTokens.get());
                json.addProperty("cliEntryCount", cliEntryCount.get());
                json.addProperty("conversationCount", conversationCount.get());
                json.addProperty("toolSuccessCount", toolSuccessCount.get());
                json.addProperty("toolFailureCount", toolFailureCount.get());
                json.addProperty("errorCount", errorCount.get());
                json.addProperty("totalThinkingTimeMs", totalThinkingTimeMs.get());
                try (FileWriter writer = new FileWriter(dataFile)) {
                    gson.toJson(json, writer);
                }
            } catch (IOException e) {
                plugin.getLogger().warning("[StatsManager] 保存统计数据失败: " + e.getMessage());
            }
        }
    }

    // ==================== bStats ====================

    private void registerCharts(Metrics metrics) {
        metrics.addCustomChart(new SingleLineChart("total_tokens", () -> (int) Math.min(getTotalTokens(), Integer.MAX_VALUE)));
        metrics.addCustomChart(new SingleLineChart("cli_entries", () -> (int) Math.min(cliEntryCount.get(), Integer.MAX_VALUE)));
        metrics.addCustomChart(new SingleLineChart("total_conversations", () -> (int) Math.min(conversationCount.get(), Integer.MAX_VALUE)));
    }

    // ==================== 计数器方法 ====================

    public void addTokens(long input, long output) {
        if (input > 0) totalInputTokens.addAndGet(input);
        if (output > 0) totalOutputTokens.addAndGet(output);
    }

    public void incrementCliEntry() {
        cliEntryCount.incrementAndGet();
    }

    public void incrementConversation() {
        conversationCount.incrementAndGet();
    }

    public void incrementToolSuccess() {
        toolSuccessCount.incrementAndGet();
    }

    public void incrementToolFailure() {
        toolFailureCount.incrementAndGet();
    }

    public void incrementErrorCount() {
        errorCount.incrementAndGet();
    }

    public void addThinkingTime(long ms) {
        if (ms > 0) totalThinkingTimeMs.addAndGet(ms);
    }

    // ==================== 快照 ====================

    /**
     * 构建当前服务器状态的完整快照，用于上报给 FancyConsole。
     * 计数器传的是当前累计值（非增量），服务端自行计算差值。
     */
    public StatsSnapshot buildSnapshot() {
        StatsSnapshot snap = new StatsSnapshot();

        // 插件 & 服务端环境
        snap.pluginVersion = plugin.getDescription().getVersion();
        Server server = Bukkit.getServer();
        snap.serverSoftware = server.getName() + " " + server.getVersion();
        snap.minecraftVersion = server.getBukkitVersion();
        snap.javaVersion = System.getProperty("java.version", "unknown");
        snap.osName = System.getProperty("os.name", "unknown");
        snap.osArch = System.getProperty("os.arch", "unknown");
        snap.availableProcessors = Runtime.getRuntime().availableProcessors();
        snap.maxMemoryMb = (int) (Runtime.getRuntime().maxMemory() / (1024 * 1024));
        snap.onlineMode = server.getOnlineMode();
        snap.aiProvider = plugin.getConfigManager().getProvider();

        // 累计计数器
        snap.totalInputTokens = totalInputTokens.get();
        snap.totalOutputTokens = totalOutputTokens.get();
        snap.cliEntryCount = (int) cliEntryCount.get();
        snap.conversationCount = (int) conversationCount.get();
        snap.toolSuccessCount = (int) toolSuccessCount.get();
        snap.toolFailureCount = (int) toolFailureCount.get();
        snap.errorCount = (int) errorCount.get();
        snap.totalThinkingTimeMs = totalThinkingTimeMs.get();

        // 运行时快照
        snap.onlinePlayers = server.getOnlinePlayers().size();
        snap.activeCliSessions = plugin.getCliManager().getActivePlayersCount();
        snap.loadedSkills = plugin.getSkillManager().getSkillCount();
        snap.indexedCommands = plugin.getWorkspaceIndexer().getIndexedCommands().size();
        snap.uptimeSeconds = (System.currentTimeMillis() - pluginStartTime) / 1000;

        // 各模式玩家数
        snap.modeYoloCount = 0;
        snap.modeSmartCount = 0;
        snap.modeNormalCount = 0;
        snap.modePlanCount = 0;
        plugin.getCliManager().getSessions().forEach((uuid, session) -> {
            switch (session.getMode()) {
                case YOLO -> snap.modeYoloCount++;
                case SMART -> snap.modeSmartCount++;
                case PLAN -> snap.modePlanCount++;
                default -> snap.modeNormalCount++;
            }
        });

        return snap;
    }

    // ==================== 定时上报 ====================

    /**
     * 计算到下一个上报时刻（06:00 / 12:00 / 18:00 / 24:00）的延迟毫秒数
     */
    private long millisToNextReport() {
        LocalDateTime now = LocalDateTime.now();
        int[] reportHours = {6, 12, 18, 24};
        LocalDateTime next = null;

        for (int hour : reportHours) {
            LocalDateTime candidate = now.with(LocalTime.of(hour % 24, 0));
            if (hour == 24) candidate = candidate.plusDays(1).with(LocalTime.MIDNIGHT);
            if (candidate.isAfter(now)) {
                next = candidate;
                break;
            }
        }

        if (next == null) {
            // 所有时刻都已过今天，取明天 06:00
            next = now.plusDays(1).with(LocalTime.of(6, 0));
        }

        return Duration.between(now, next).toMillis();
    }

    /**
     * 调度下一次定时上报
     */
    private void scheduleNextReport() {
        long delayMs = millisToNextReport();
        long delayTicks = Math.max(1, delayMs / 50);

        if (plugin.getConfigManager().isDebug()) {
            plugin.getLogger().info("[StatsManager] 距下次上报 " + (delayMs / 1000) + " 秒");
        }

        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            try {
                doReport();
            } catch (Exception e) {
                plugin.getLogger().warning("[StatsManager] 上报失败: " + e.getMessage());
            }
            // 上报完成后调度下一次
            scheduleNextReport();
        }, delayTicks);
    }

    /**
     * 执行上报
     */
    private void doReport() {
        if (!plugin.getFancyConsoleManager().isReady()) {
            if (plugin.getConfigManager().isDebug()) {
                plugin.getLogger().info("[StatsManager] 跳过上报：未配置 API Key");
            }
            return;
        }

        StatsSnapshot snapshot = buildSnapshot();
        boolean success = plugin.getFancyConsoleManager().reportStats(snapshot);
        // 上报失败不报错（静默后台任务），仅调试模式记录成功
        if (success && plugin.getConfigManager().isDebug()) {
            plugin.getLogger().info("[StatsManager] 上报成功");
        }
    }

    // ==================== getter ====================

    public long getTotalTokens() {
        return totalInputTokens.get() + totalOutputTokens.get();
    }

    public int getTotalTokensInt() {
        return (int) Math.min(getTotalTokens(), Integer.MAX_VALUE);
    }

    public int getCliEntryCount() {
        return (int) Math.min(cliEntryCount.get(), Integer.MAX_VALUE);
    }

    public int getConversationCount() {
        return (int) Math.min(conversationCount.get(), Integer.MAX_VALUE);
    }

    // ==================== 数据类 ====================

    /**
     * 统计数据快照，用于上报给 FancyConsole
     */
    public static class StatsSnapshot {
        // 环境信息
        public String pluginVersion;
        public String serverSoftware;
        public String minecraftVersion;
        public String javaVersion;
        public String osName;
        public String osArch;
        public int availableProcessors;
        public int maxMemoryMb;
        public boolean onlineMode;
        public String aiProvider;

        // 累计计数器
        public long totalInputTokens;
        public long totalOutputTokens;
        public int cliEntryCount;
        public int conversationCount;
        public int toolSuccessCount;
        public int toolFailureCount;
        public int errorCount;
        public long totalThinkingTimeMs;

        // 运行时快照
        public int onlinePlayers;
        public int activeCliSessions;
        public int loadedSkills;
        public int indexedCommands;
        public long uptimeSeconds;

        // 各模式玩家数
        public int modeYoloCount;
        public int modeSmartCount;
        public int modeNormalCount;
        public int modePlanCount;
    }
}
