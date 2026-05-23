package org.YanPl.manager;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.YanPl.FancyHelper;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SingleLineChart;
import org.bukkit.Bukkit;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;

public class StatsManager {
    private final FancyHelper plugin;
    private final AtomicLong totalTokens = new AtomicLong(0);
    private final AtomicLong cliEntryCount = new AtomicLong(0);
    private final AtomicLong conversationCount = new AtomicLong(0);
    private final Gson gson = new Gson();
    private final File dataFile;
    private final Object saveLock = new Object();

    public StatsManager(FancyHelper plugin, Metrics metrics) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "temp/stats.json");
        load();
        registerCharts(metrics);
        startAutoSave();
    }

    private void startAutoSave() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::save, 6000L, 6000L);
    }

    private void load() {
        if (!dataFile.exists()) return;
        try (FileReader reader = new FileReader(dataFile)) {
            JsonObject json = gson.fromJson(reader, JsonObject.class);
            if (json != null) {
                if (json.has("totalTokens")) totalTokens.set(json.get("totalTokens").getAsLong());
                if (json.has("cliEntryCount")) cliEntryCount.set(json.get("cliEntryCount").getAsLong());
                if (json.has("conversationCount")) conversationCount.set(json.get("conversationCount").getAsLong());
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
                json.addProperty("totalTokens", totalTokens.get());
                json.addProperty("cliEntryCount", cliEntryCount.get());
                json.addProperty("conversationCount", conversationCount.get());
                try (FileWriter writer = new FileWriter(dataFile)) {
                    gson.toJson(json, writer);
                }
            } catch (IOException e) {
                plugin.getLogger().warning("[StatsManager] 保存统计数据失败: " + e.getMessage());
            }
        }
    }

    private void registerCharts(Metrics metrics) {
        metrics.addCustomChart(new SingleLineChart("total_tokens", this::getTotalTokens));
        metrics.addCustomChart(new SingleLineChart("cli_entries", this::getCliEntryCount));
        metrics.addCustomChart(new SingleLineChart("total_conversations", this::getConversationCount));
    }

    public void addTokens(long tokens) {
        totalTokens.addAndGet(tokens);
    }

    public void incrementCliEntry() {
        cliEntryCount.incrementAndGet();
    }

    public void incrementConversation() {
        conversationCount.incrementAndGet();
    }

    public int getTotalTokens() {
        long val = totalTokens.get();
        return val > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) val;
    }

    public int getCliEntryCount() {
        long val = cliEntryCount.get();
        return val > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) val;
    }

    public int getConversationCount() {
        long val = conversationCount.get();
        return val > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) val;
    }
}
