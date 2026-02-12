package org.YanPl.manager;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.YanPl.FancyHelper;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 公告管理器：负责从远程获取公告并显示给管理员和玩家
 */
public class NoticeManager {
    private static final String NOTICE_URL = "https://zip8919.github.io/FancyHelper-notice/v1/notice.json";
    private final FancyHelper plugin;
    private final HttpClient httpClient;
    private BukkitTask fetchTask;
    private NoticeData currentNotice;

    public NoticeManager(FancyHelper plugin) {
        this.plugin = plugin;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        startPeriodicFetch();
    }

    /**
     * 启动定期拉取公告任务
     */
    public void startPeriodicFetch() {
        if (fetchTask != null) {
            fetchTask.cancel();
        }

        int interval = plugin.getConfigManager().getNoticeRefreshInterval();
        // 转换为 tick (1分钟 = 1200 ticks)
        long ticks = (long) interval * 1200;

        fetchTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            fetchNoticeAsync();
        }, 0L, ticks);
    }

    /**
     * 检查并更新公告内容
     *
     * @param newData 新的公告数据
     */
    private void checkAndUpdateNotice(NoticeData newData) {
        FileConfiguration playerData = plugin.getConfigManager().getPlayerData();
        String localNoticeText = playerData.getString("notice.last_content", "");

        if (!newData.text.equals(localNoticeText)) {
            // 公告内容已更新
            playerData.set("notice.last_content", newData.text);
            // 清空已读列表
            playerData.set("notice.read_players", new ArrayList<String>());
            plugin.getConfigManager().savePlayerData();
            plugin.getLogger().info("检测到新公告，已更新本地存储并重置已读列表。");
            showNoticeToConsole(newData);
        } else if (currentNotice == null) {
            // 首次启动且内容未变，也显示一次
            showNoticeToConsole(newData);
        }
    }

    /**
     * 将公告标记为已读
     *
     * @param player 玩家
     */
    public void markAsRead(Player player) {
        FileConfiguration playerData = plugin.getConfigManager().getPlayerData();
        List<String> readPlayers = playerData.getStringList("notice.read_players");
        String uuid = player.getUniqueId().toString();

        if (!readPlayers.contains(uuid)) {
            readPlayers.add(uuid);
            playerData.set("notice.read_players", readPlayers);
            plugin.getConfigManager().savePlayerData();
            player.sendMessage("§a[FancyHelper] 公告已标记为已读。");
        } else {
            player.sendMessage("§e[FancyHelper] 该公告你已经读过了。");
        }
    }

    /**
     * 检查玩家是否已阅读当前公告
     *
     * @param player 玩家
     * @return 是否已读
     */
    public boolean hasRead(Player player) {
        FileConfiguration playerData = plugin.getConfigManager().getPlayerData();
        List<String> readPlayers = playerData.getStringList("notice.read_players");
        return readPlayers.contains(player.getUniqueId().toString());
    }

    /**
     * 获取当前已缓存的公告数据
     *
     * @return 公告数据
     */
    public NoticeData getCurrentNotice() {
        return currentNotice;
    }

    /**
     * 异步获取公告
     */
    public CompletableFuture<NoticeData> fetchNoticeAsync() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                NoticeData data = fetchNotice();
                if (data != null) {
                    this.currentNotice = data;
                    checkAndUpdateNotice(data);
                }
                return data;
            } catch (IOException e) {
                plugin.getLogger().warning("获取公告失败: " + e.getMessage());
                return null;
            }
        });
    }

    /**
     * 同步获取公告
     */
    public NoticeData fetchNotice() throws IOException {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(NOTICE_URL))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new IOException("获取公告失败: HTTP " + response.statusCode());
            }

            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();

            boolean noticeEnabled = json.has("notice") && json.get("notice").getAsBoolean();
            String text = json.has("text") ? json.get("text").getAsString() : "";

            return new NoticeData(noticeEnabled, text);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("获取公告被中断", e);
        }
    }

    /**
     * 显示公告到控制台
     *
     * @param noticeData 公告数据
     */
    public void showNoticeToConsole(NoticeData noticeData) {
        if (noticeData != null && noticeData.enabled) {
            plugin.getLogger().info("========================================");
            plugin.getLogger().info("【FancyHelper 公告】");
            if (noticeData.text != null && !noticeData.text.isEmpty()) {
                for (String line : noticeData.text.split("\\n")) {
                    plugin.getLogger().info(line);
                }
            }
            plugin.getLogger().info("========================================");
        }
    }

    /**
     * 显示公告给玩家
     *
     * @param player     玩家对象
     * @param noticeData 公告数据
     */
    public void showNoticeToPlayer(org.bukkit.entity.Player player, NoticeData noticeData) {
        if (noticeData != null && noticeData.enabled && player.hasPermission("fancyhelper.notice")) {
            player.sendMessage("========================================");
            player.sendMessage("§e【FancyHelper 公告】");
            if (noticeData.text != null && !noticeData.text.isEmpty()) {
                for (String line : noticeData.text.split("\\n")) {
                    player.sendMessage("§f" + line);
                }
            }
            
            // 如果玩家还没读过，显示“标为已读”按钮
            if (!hasRead(player)) {
                TextComponent readButton = new TextComponent("§7[ §a✔ 标为已读 §7]");
                readButton.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("§7点击将此公告标记为已读")));
                readButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/fancyhelper notice read"));
                
                player.spigot().sendMessage(readButton);
            }
            
            player.sendMessage("========================================");
        }
    }

    /**
     * 公告数据模型
     */
    public static class NoticeData {
        public final boolean enabled;
        public final String text;

        public NoticeData(boolean enabled, String text) {
            this.enabled = enabled;
            this.text = text;
        }
    }

    public void shutdown() {
        // HttpClient 无需显式关闭
    }
}