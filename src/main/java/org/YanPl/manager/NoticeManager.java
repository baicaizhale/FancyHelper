package org.YanPl.manager;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.YanPl.FancyHelper;
import org.YanPl.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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

public class NoticeManager {
    private static final String NOTICE_URL = "https://fcnotice.baicaizhale.top/v2/notice.json";
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

    public void startPeriodicFetch() {
        if (fetchTask != null) {
            fetchTask.cancel();
        }

        int interval = plugin.getConfigManager().getNoticeRefreshInterval();
        long ticks = (long) interval * 1200;

        if (!plugin.isEnabled()) return;
        fetchTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (!plugin.isEnabled()) return;
            fetchNoticeAsync();
        }, 0L, ticks);
    }

    private void checkAndUpdateNotice(NoticeData newData) {
        FileConfiguration playerData = plugin.getConfigManager().getPlayerData();
        String localNoticeText = playerData.getString("notice.last_content", "");

        if (!newData.text.equals(localNoticeText)) {
            playerData.set("notice.last_content", newData.text);
            playerData.set("notice.read_players", new ArrayList<String>());
            plugin.getConfigManager().savePlayerData();
            plugin.getLogger().info("检测到新公告，已更新本地存储并重置已读列表。");
            showNoticeToConsole(newData);
        } else if (currentNotice == null) {
            showNoticeToConsole(newData);
        }
    }

    public void markAsRead(Player player) {
        FileConfiguration playerData = plugin.getConfigManager().getPlayerData();
        List<String> readPlayers = playerData.getStringList("notice.read_players");
        String uuid = player.getUniqueId().toString();

        if (!readPlayers.contains(uuid)) {
            readPlayers.add(uuid);
            playerData.set("notice.read_players", readPlayers);
            plugin.getConfigManager().savePlayerData();
            player.sendMessage(ColorUtil.translateCustomColors("§zFancyHelper§b§r §7> §f已将公告标记为已读"));
        } else {
            player.sendMessage(ColorUtil.translateCustomColors("§zFancyHelper§b§r §7> §f该公告被你标记为已读"));
        }
    }

    public boolean hasRead(Player player) {
        FileConfiguration playerData = plugin.getConfigManager().getPlayerData();
        List<String> readPlayers = playerData.getStringList("notice.read_players");
        return readPlayers.contains(player.getUniqueId().toString());
    }

    public NoticeData getCurrentNotice() {
        return currentNotice;
    }

    public CompletableFuture<NoticeData> fetchNoticeAsync() {
        CompletableFuture<NoticeData> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                NoticeData data = fetchNotice();
                if (data != null) {
                    this.currentNotice = data;
                    checkAndUpdateNotice(data);
                }
                future.complete(data);
            } catch (Exception e) {
                String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                plugin.getLogger().warning("获取公告失败: " + errorMsg);
                future.complete(null);
            }
        });
        return future;
    }

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
            int level = json.has("level") ? json.get("level").getAsInt() : 1;

            return new NoticeData(noticeEnabled, text, level);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("获取公告被中断", e);
        }
    }

    public void showNoticeToConsole(NoticeData noticeData) {
        if (noticeData != null && noticeData.enabled) {
            String colorCode;

            switch (noticeData.level) {
                case 3:
                    colorCode = ChatColor.RED.toString();
                    break;
                case 2:
                    colorCode = ChatColor.YELLOW.toString();
                    break;
                case 1:
                default:
                    colorCode = ChatColor.WHITE.toString();
                    break;
            }

            Bukkit.getConsoleSender().sendMessage(ChatColor.GRAY + "========================================");
            Bukkit.getConsoleSender().sendMessage(ChatColor.GOLD + "【FancyHelper】");
            if (noticeData.text != null && !noticeData.text.isEmpty()) {
                for (String line : noticeData.text.split("\\n")) {
                    Bukkit.getConsoleSender().sendMessage(colorCode + line);
                }
            }
            Bukkit.getConsoleSender().sendMessage(ChatColor.GRAY + "========================================");
        }
    }

    public void showNoticeToPlayer(org.bukkit.entity.Player player, NoticeData noticeData) {
        if (noticeData != null && noticeData.enabled && player.hasPermission("fancyhelper.notice")) {
            String colorCode;
            switch (noticeData.level) {
                case 3:
                    colorCode = "§c";
                    break;
                case 2:
                    colorCode = "§e";
                    break;
                case 1:
                default:
                    colorCode = "§f";
                    break;
            }

            player.sendMessage("§e━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            player.sendMessage("§6  ✦ FancyHelper 公告");

            player.sendMessage("§8  ────────────────────────");
            if (noticeData.text != null && !noticeData.text.isEmpty()) {
                for (String line : noticeData.text.split("\\n")) {
                    player.sendMessage(colorCode + "  " + line);
                }
            }

            if (!hasRead(player)) {
                TextComponent readButton = new TextComponent("§8  [§a✔ 标为已读§8]");
                readButton.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("§7点击将此公告标记为已读")));
                readButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/fancyhelper notice read"));

                player.spigot().sendMessage(readButton);
            }

            player.sendMessage("§e━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        }
    }

    public static class NoticeData {
        public final boolean enabled;
        public final String text;
        public final int level;

        public NoticeData(boolean enabled, String text, int level) {
            this.enabled = enabled;
            this.text = text;
            this.level = level;
        }
    }

    public void shutdown() {
    }
}
