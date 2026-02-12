package org.YanPl.manager;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.YanPl.FancyHelper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * 公告管理器：负责从远程获取公告并显示给管理员和玩家
 */
public class NoticeManager {
    private static final String NOTICE_URL = "https://zip8919.github.io/FancyHelper-notice/v1/notice.json";
    private final FancyHelper plugin;
    private final HttpClient httpClient;

    public NoticeManager(FancyHelper plugin) {
        this.plugin = plugin;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * 异步获取公告
     */
    public CompletableFuture<NoticeData> fetchNoticeAsync() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return fetchNotice();
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
     */
    public void showNoticeToConsole(NoticeData noticeData) {
        if (noticeData != null && noticeData.enabled) {
            plugin.getLogger().info("========================================");
            plugin.getLogger().info("【FancyHelper 公告】");
            plugin.getLogger().info(noticeData.text);
            plugin.getLogger().info("========================================");
        }
    }

    /**
     * 显示公告给玩家
     */
    public void showNoticeToPlayer(org.bukkit.entity.Player player, NoticeData noticeData) {
        if (noticeData != null && noticeData.enabled && player.hasPermission("fancyhelper.notice")) {
            player.sendMessage("========================================");
            player.sendMessage("§e【FancyHelper 公告】");
            player.sendMessage("§f" + noticeData.text);
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