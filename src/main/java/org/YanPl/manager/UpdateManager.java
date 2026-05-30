package org.YanPl.manager;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.YanPl.FancyHelper;
import org.YanPl.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

/**
 * 更新管理器：从 Cloudflare Worker 获取最新版本，并通过三级级联下载 JAR。
 */
public class UpdateManager implements Listener {
    private final FancyHelper plugin;

    private static final String GITHUB_API_URL = "https://api.github.com/repos/baicaizhale/FancyHelper/releases/latest";
    private static final String GITHUB_DL_BASE = "https://github.com/baicaizhale/FancyHelper/releases/download/";

    private String latestVersion = null;
    private String downloadUrl = null;
    private String latestFileName = null;
    private String releaseOverview = null;
    private boolean hasUpdate = false;

    public UpdateManager(FancyHelper plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /**
     * 检查更新。
     */
    public void checkForUpdates() {
        checkForUpdates(null);
    }

    /**
     * 检查更新并通知特定玩家。
     */
    public void checkForUpdates(Player sender) {
        if (!plugin.getConfigManager().isCheckUpdate() && sender == null) {
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // 第一层：GitHub API（ghproxy + 直连）
                boolean fetched = fetchFromGitHubApi(sender);

                // 第二层：回退到 Cloudflare Worker
                if (!fetched) {
                    fetched = fetchFromWorkerApi(sender);
                }

                if (!fetched) {
                    if (sender != null) {
                        sender.sendMessage(ColorUtil.translateCustomColors("§zFancyHelper§b§r §7> §f检查更新失败（GitHub/Worker 均不可用）。"));
                    }
                    return;
                }

                String currentVersion = plugin.getDescription().getVersion();
                if (isNewerVersion(currentVersion, latestVersion)) {
                    hasUpdate = true;
                    Bukkit.getConsoleSender().sendMessage(ColorUtil.translateCustomColors("§zFancyHelper§b§r §7> §f检测到新版本: v" + latestVersion));
                    Bukkit.getConsoleSender().sendMessage(ColorUtil.translateCustomColors("§zFancyHelper§b§r §7> §f下载地址: " + downloadUrl));

                    if (releaseOverview != null && !releaseOverview.isEmpty()) {
                        Bukkit.getConsoleSender().sendMessage(ColorUtil.translateCustomColors("§zFancyHelper§b§r §7> §f更新内容:"));
                        for (String line : releaseOverview.split("\\r?\\n")) {
                            String trimmedLine = line.trim();
                            if (!trimmedLine.isEmpty()) {
                                trimmedLine = trimmedLine.replaceFirst("^[*\\-\\d.]+\\s+", "");
                                Bukkit.getConsoleSender().sendMessage(" §b§l> §r" + trimmedLine);
                            }
                        }
                    }

                    if (plugin.getConfigManager().isAutoUpgrade()) {
                        Bukkit.getConsoleSender().sendMessage(ColorUtil.translateCustomColors("§zFancyHelper§b§r §7> §f检测到自动升级已开启，正在后台下载更新..."));
                        downloadAndInstall(null, true, true);
                    } else {
                        Bukkit.getConsoleSender().sendMessage(ColorUtil.translateCustomColors("§zFancyHelper§b§r §7> §f如需自动下载更新，请将 config.yml 中的 auto_upgrade 设置为 true"));
                    }

                    if (sender != null) {
                        sender.sendMessage(ColorUtil.translateCustomColors("§zFancyHelper§b§r §7> §f检测到新版本: " + ChatColor.WHITE + "v" + latestVersion));
                        if (releaseOverview != null && !releaseOverview.isEmpty()) {
                            sender.sendMessage(ColorUtil.translateCustomColors("§zFancyHelper§b§r §7> §f更新内容:"));
                            for (String line : releaseOverview.split("\\r?\\n")) {
                                String trimmedLine = line.trim();
                                if (!trimmedLine.isEmpty()) {
                                    trimmedLine = trimmedLine.replaceFirst("^[*\\-\\d.]+\\s+", "");
                                    sender.sendMessage(" §b§l> §r" + trimmedLine);
                                }
                            }
                        }
                        if (plugin.getConfigManager().isAutoUpgrade()) {
                            sender.sendMessage(ColorUtil.translateCustomColors("§zFancyHelper§b§r §7> §f正在执行自动更新..."));
                        } else {
                            sender.sendMessage(ColorUtil.translateCustomColors("§zFancyHelper§b§r §7> §f使用 " + ChatColor.AQUA + "/fancy upgrade" + ChatColor.WHITE + " 自动下载并更新。"));
                        }
                    }
                } else {
                    hasUpdate = false;
                    String message = ColorUtil.translateCustomColors("§zFancyHelper§b§r §7> §f当前已是最新版本 (v" + currentVersion + ")");
                    if (sender != null) {
                        sender.sendMessage(message);
                    } else {
                        Bukkit.getConsoleSender().sendMessage(message);
                    }
                }

            } catch (Exception e) {
                plugin.getLogger().warning("检查更新失败: " + e.getMessage());
                if (sender != null) {
                    sender.sendMessage(ColorUtil.translateCustomColors("§zFancyHelper§b§r §7> §f检查更新失败: " + e.getMessage()));
                }
            }
        });
    }

    /**
     * 从 Cloudflare Worker API 获取版本信息
     */
    private boolean fetchFromWorkerApi(Player sender) {
        try {
            String apiUrl = plugin.getConfigManager().getPluginVersionApi();
            String body = tryFetchString(apiUrl);

            if (body == null) {
                plugin.getLogger().info("[Update] Worker API 不可用，尝试 GitHub API...");
                return false;
            }

            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            if (!json.has("version")) {
                plugin.getLogger().warning("[Update] Worker API 返回格式异常");
                return false;
            }

            latestVersion = json.get("version").getAsString().replace("v", "");
            // 下载走 fancy.baicaizhale.top/latest，GitHub URL 作为留底
            latestFileName = "FancyHelper-v" + latestVersion + ".jar";
            downloadUrl = GITHUB_DL_BASE + "v" + latestVersion + "/" + latestFileName;
            releaseOverview = json.has("changelog") && !json.get("changelog").isJsonNull()
                    ? json.get("changelog").getAsString() : null;

            plugin.getLogger().info("[Update] 从 Worker 获取版本信息成功: v" + latestVersion);
            return true;

        } catch (Exception e) {
            plugin.getLogger().info("[Update] Worker API 请求异常: " + e.getMessage());
            return false;
        }
    }

    /**
     * 从 GitHub API 获取版本信息（ghproxy + 直连回退）
     */
    private boolean fetchFromGitHubApi(Player sender) {
        try {
            String mirror = "https://ghproxy.vip/";
            String body = tryFetchString(mirror + GITHUB_API_URL);

            if (body == null) {
                plugin.getLogger().info("[Update] GitHub API 镜像不可用，尝试直连...");
                body = tryFetchString(GITHUB_API_URL);
            }

            if (body == null) {
                plugin.getLogger().warning("[Update] GitHub API 直连也失败");
                return false;
            }

            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            if (!json.has("tag_name") || json.get("tag_name").isJsonNull()) {
                plugin.getLogger().warning("[Update] GitHub API 响应中缺少 tag_name");
                return false;
            }

            latestVersion = json.get("tag_name").getAsString().replace("v", "");

            if (json.has("body") && !json.get("body").isJsonNull()) {
                releaseOverview = extractOverview(json.get("body").getAsString());
            }

            if (json.has("assets") && !json.get("assets").isJsonNull()) {
                var assets = json.getAsJsonArray("assets");
                for (var element : assets) {
                    var asset = element.getAsJsonObject();
                    String name = asset.get("name").getAsString();
                    if (name.endsWith(".jar")) {
                        downloadUrl = asset.get("browser_download_url").getAsString();
                        latestFileName = name;
                        break;
                    }
                }
            }

            if (downloadUrl == null) {
                if (json.has("html_url") && !json.get("html_url").isJsonNull()) {
                    downloadUrl = json.get("html_url").getAsString();
                    latestFileName = "FancyHelper-v" + latestVersion + ".jar";
                } else {
                    return false;
                }
            }

            plugin.getLogger().info("[Update] 从 GitHub API 获取版本信息成功: v" + latestVersion);
            return true;

        } catch (Exception e) {
            plugin.getLogger().warning("[Update] GitHub API 请求异常: " + e.getMessage());
            return false;
        }
    }

    /**
     * 下载并安装更新。
     */
    public void downloadAndInstall(Player sender) {
        downloadAndInstall(sender, false);
    }

    /**
     * 下载并安装更新。
     */
    public void downloadAndInstall(Player sender, boolean autoReload) {
        downloadAndInstall(sender, autoReload, false);
    }

    /**
     * 下载并安装更新（三级级联：Worker CDN → ghproxy → GitHub 直连）。
     */
    public void downloadAndInstall(Player sender, boolean autoReload, boolean alreadyAsync) {
        if (!hasUpdate || downloadUrl == null) {
            String msg = ColorUtil.translateCustomColors("§zFancyHelper§b§r §7> §f当前没有可用的更新。");
            if (sender != null) sender.sendMessage(msg);
            plugin.getLogger().warning("无法下载更新：hasUpdate=" + hasUpdate + ", downloadUrl=" + downloadUrl);
            return;
        }

        if (sender != null) sender.sendMessage(ColorUtil.translateCustomColors("§zFancyHelper§b§r §7> §f开始下载更新..."));

        Runnable downloadTask = () -> {
            try {
                // 第一层：ghproxy
                String mirrorUrl = "https://ghproxy.vip/" + downloadUrl;

                // 第二层：直连
                String directUrl = downloadUrl;

                // 第三层：CDN 留底（fancy.baicaizhale.top/latest）
                String primaryUrl = plugin.getConfigManager().getPluginCdnBase() + "latest";

                HttpResponse<InputStream> response = fetchInputStreamWithFallback(mirrorUrl, directUrl, primaryUrl);

                if (response == null || response.statusCode() != 200) {
                    int code = response != null ? response.statusCode() : 0;
                    throw new IOException("下载失败: HTTP " + code);
                }

                File pluginsDir = plugin.getDataFolder().getParentFile();
                String newJarName = latestFileName;
                File newJarFile = new File(pluginsDir, newJarName);

                try (InputStream inputStream = response.body()) {
                    Files.copy(inputStream, newJarFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }

                plugin.getLogger().info("文件下载完成，大小: " + newJarFile.length() + " 字节");

                String successMsg = ColorUtil.translateCustomColors("§zFancyHelper§b§r §7> §f更新下载完成！");
                String versionMsg = ColorUtil.translateCustomColors("§zFancyHelper§b§r §7> §f新版本已就绪: " + newJarName);
                if (sender != null) {
                    sender.sendMessage(successMsg);
                    sender.sendMessage(versionMsg);
                } else {
                    Bukkit.getConsoleSender().sendMessage(successMsg);
                    Bukkit.getConsoleSender().sendMessage(versionMsg);
                }

                if (autoReload) {
                    if (!plugin.isEnabled()) return;
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (plugin.signalReloadService("UPDATE", newJarName)) {
                            Bukkit.getPluginManager().disablePlugin(plugin);
                        } else {
                            plugin.getLogger().severe("无法连接 ReloadService，更新无法自动完成。请重启服务器。");
                        }
                    });
                }

            } catch (IOException e) {
                plugin.getLogger().severe("更新下载失败: " + e.getMessage());
                if (sender != null) {
                    sender.sendMessage(ColorUtil.translateCustomColors("§zFancyHelper§b§r §7> §f更新下载失败: " + e.getMessage()));
                }
            }
        };

        if (alreadyAsync) {
            downloadTask.run();
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, downloadTask);
        }
    }

    // ==================== 三级级联 HTTP 工具方法 ====================

    private String tryFetchString(String url) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "FancyHelper-Updater")
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) return response.body();
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
        }
        return null;
    }

    private HttpResponse<InputStream> fetchInputStreamWithFallback(String mirrorUrl, String directUrl, String cdnUrl) {
        // 第一层：ghproxy
        HttpResponse<InputStream> response = tryFetchStream(mirrorUrl);
        if (response != null && response.statusCode() == 200) return response;
        plugin.getLogger().info("[Update] ghproxy 不可用，尝试直连...");

        // 第二层：直连
        response = tryFetchStream(directUrl);
        if (response != null && response.statusCode() == 200) return response;
        plugin.getLogger().info("[Update] 直连不可用，尝试 CDN 留底...");

        // 第三层：CDN 留底
        response = tryFetchStream(cdnUrl);
        if (response == null || response.statusCode() != 200) {
            int code = response != null ? response.statusCode() : 0;
            plugin.getLogger().warning("[Update] CDN 留底下载返回 HTTP " + code);
        }
        return response;
    }

    private HttpResponse<InputStream> tryFetchStream(String url) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(30))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "FancyHelper-Updater")
                    .timeout(Duration.ofSeconds(60))
                    .GET()
                    .build();
            return client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
        }
        return null;
    }

    // ==================== 事件监听 ====================

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!plugin.getConfigManager().isOpUpdateNotify()) return;
        Player player = event.getPlayer();
        if (hasUpdate && player.isOp()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                player.sendMessage(ColorUtil.translateCustomColors("§zFancyHelper§b§r §7> §f检测到新版本: §a" + latestVersion));
                if (releaseOverview != null && !releaseOverview.isEmpty()) {
                    player.sendMessage(ColorUtil.translateCustomColors("§zFancyHelper§b§r §7> §f更新内容:"));
                    for (String line : releaseOverview.split("\\r?\\n")) {
                        String trimmedLine = line.trim();
                        if (!trimmedLine.isEmpty()) {
                            trimmedLine = trimmedLine.replaceFirst("^[*\\-\\d.]+\\s+", "");
                            player.sendMessage(" §b§l- §r" + trimmedLine);
                        }
                    }
                }
                player.sendMessage(ColorUtil.translateCustomColors("§zFancyHelper§b§r §7> §f使用 §e/fancy upgrade §f自动下载并更新。"));
            }, 40L);
        }
    }

    // ==================== 版本比较 ====================

    private boolean isNewerVersion(String current, String latest) {
        if (current == null || latest == null) return false;
        return compareVersion(current, latest) < 0;
    }

    private int compareVersion(String v1, String v2) {
        String[] p1 = v1.trim().split("\\.");
        String[] p2 = v2.trim().split("\\.");
        int len = Math.max(p1.length, p2.length);
        for (int i = 0; i < len; i++) {
            int n1 = i < p1.length ? toInt(p1[i]) : 0;
            int n2 = i < p2.length ? toInt(p2[i]) : 0;
            if (n1 != n2) return n1 - n2;
        }
        return 0;
    }

    private int toInt(String s) {
        if (s == null || s.isEmpty()) return 0;
        int num = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '0' && c <= '9') {
                num = num * 10 + (c - '0');
            } else if (i == 0) {
                continue;
            } else {
                break;
            }
        }
        return num;
    }

    /**
     * 从 Release body 中提取 Overview 部分
     */
    private String extractOverview(String body) {
        if (body == null || body.isEmpty()) return null;

        int overviewStart = body.indexOf("## Overview");
        if (overviewStart == -1) overviewStart = body.indexOf("## 🚀 版本概述");
        if (overviewStart == -1) overviewStart = body.indexOf("## 版本概述");
        if (overviewStart == -1) overviewStart = body.indexOf("## 🚀");
        if (overviewStart == -1) overviewStart = 0;

        int overviewEnd = body.indexOf("## What's Changed");
        if (overviewEnd == -1) overviewEnd = body.indexOf("## **Full Changelog**");
        if (overviewEnd == -1) overviewEnd = body.indexOf("## Full Changelog");
        if (overviewEnd == -1) overviewEnd = Math.min(body.length(), 500);

        String overview = body.substring(overviewStart, overviewEnd).trim();
        overview = overview.replace("## Overview", "")
                           .replace("## 🚀 版本概述", "")
                           .replace("## 版本概述", "")
                           .replace("## 🚀", "")
                           .trim();
        return overview;
    }

    // ==================== 状态查询 ====================

    public boolean hasUpdate() {
        return hasUpdate;
    }

    public String getLatestVersion() {
        return latestVersion;
    }

    public String getReleaseOverview() {
        return releaseOverview;
    }
}
