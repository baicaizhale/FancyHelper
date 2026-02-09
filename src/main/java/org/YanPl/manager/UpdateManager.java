package org.YanPl.manager;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okio.BufferedSink;
import okio.Okio;
import org.YanPl.FancyHelper;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.TimeUnit;

/**
 * 更新管理器：负责从 GitHub 获取最新版本并提醒管理员。
 */
public class UpdateManager implements Listener {
    private final FancyHelper plugin;
    private final String repoUrl = "https://api.github.com/repos/baicaizhale/FancyHelper/releases/latest";
    private String latestVersion = null;
    private String downloadUrl = null;
    private String latestFileName = null;
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
     * @param sender 通知对象（可为 null，仅输出到控制台）
     */
    public void checkForUpdates(Player sender) {
        if (!plugin.getConfigManager().isCheckUpdate() && sender == null) {
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .build();

            Request request = new Request.Builder()
                    .url(repoUrl)
                    .header("User-Agent", "FancyHelper-Updater")
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String jsonResponse = response.body().string();
                    JsonObject jsonObject = JsonParser.parseString(jsonResponse).getAsJsonObject();
                    
                    latestVersion = jsonObject.get("tag_name").getAsString().replace("v", "");
                    
                    // 获取第一个 .jar 文件的下载地址和文件名
                    JsonArray assets = jsonObject.getAsJsonArray("assets");
                    for (JsonElement assetElement : assets) {
                        JsonObject asset = assetElement.getAsJsonObject();
                        String name = asset.get("name").getAsString();
                        if (name.endsWith(".jar")) {
                            downloadUrl = asset.get("browser_download_url").getAsString();
                            latestFileName = name;
                            break;
                        }
                    }

                    if (downloadUrl == null) {
                        downloadUrl = jsonObject.get("html_url").getAsString();
                        latestFileName = "FancyHelper-v" + latestVersion + ".jar";
                    }
                    
                    String currentVersion = plugin.getDescription().getVersion();
                    if (isNewerVersion(currentVersion, latestVersion)) {
                        hasUpdate = true;
                        Bukkit.getConsoleSender().sendMessage(ChatColor.GOLD + "[FancyHelper] " + ChatColor.YELLOW + "检测到新版本: v" + latestVersion);
                        Bukkit.getConsoleSender().sendMessage(ChatColor.GOLD + "[FancyHelper] " + ChatColor.YELLOW + "下载地址: " + downloadUrl);
                        
                        if (sender != null) {
                            sender.sendMessage(ChatColor.GOLD + "[FancyHelper] " + ChatColor.YELLOW + "检测到新版本: " + ChatColor.WHITE + "v" + latestVersion);
                            sender.sendMessage(ChatColor.GOLD + "[FancyHelper] " + ChatColor.YELLOW + "使用 " + ChatColor.AQUA + "/fancy upgrade" + ChatColor.YELLOW + " 自动下载并更新。");
                        }
                    } else {
                        hasUpdate = false;
                        if (sender != null) {
                            sender.sendMessage(ChatColor.GOLD + "[FancyHelper] " + ChatColor.GREEN + "当前已是最新版本 (v" + currentVersion + ")");
                        }
                    }
                } else {
                    if (sender != null) {
                        sender.sendMessage(ChatColor.GOLD + "[FancyHelper] " + ChatColor.RED + "检查更新失败：服务器响应异常。");
                    }
                }
            } catch (IOException e) {
                plugin.getLogger().warning("检查更新失败: " + e.getMessage());
                if (sender != null) {
                    sender.sendMessage(ChatColor.GOLD + "[FancyHelper] " + ChatColor.RED + "检查更新失败: " + e.getMessage());
                }
            }
        });
    }

    /**
     * 下载并安装更新。
     * @param sender 发起更新的玩家（可为 null）
     */
    public void downloadAndInstall(Player sender) {
        downloadAndInstall(sender, false);
    }

    /**
     * 下载并安装更新。
     * @param sender 发起更新的玩家（可为 null）
     * @param autoReload 是否在下载完成后自动重载
     */
    public void downloadAndInstall(Player sender, boolean autoReload) {
        if (!hasUpdate || downloadUrl == null) {
            if (sender != null) sender.sendMessage(ChatColor.RED + "当前没有可用的更新。");
            return;
        }

        if (sender != null) sender.sendMessage(ChatColor.GOLD + "[FancyHelper] " + ChatColor.YELLOW + "开始下载更新...");

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String mirror = plugin.getConfigManager().getUpdateMirror();
            String finalUrl = mirror + downloadUrl;

            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .build();

            Request request = new Request.Builder()
                    .url(finalUrl)
                    .header("User-Agent", "FancyHelper-Updater")
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    throw new IOException("下载失败: " + response.code());
                }

                // 准备保存新版本
                File pluginsDir = plugin.getDataFolder().getParentFile();
                String newJarName = latestFileName;
                File newJarFile = new File(pluginsDir, newJarName);

                try (BufferedSink sink = Okio.buffer(Okio.sink(newJarFile))) {
                    sink.writeAll(response.body().source());
                }

                // 尝试移动旧版本到 plugins/FancyHelper/old 目录
                File oldDir = new File(plugin.getDataFolder(), "old");
                if (!oldDir.exists()) oldDir.mkdirs();

                boolean moved = false;
                String moveError = "";
                
                // 直接遍历 plugins 目录寻找旧版文件
                File[] files = pluginsDir.listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (file.isFile() && file.getName().toLowerCase().endsWith(".jar")) {
                            String fileName = file.getName();
                            // 匹配逻辑：文件名包含 fancyhelper (不区分大小写) 且不是刚刚下载的新文件
                            if (fileName.toLowerCase().contains("fancyhelper") && !fileName.equals(newJarName)) {
                                try {
                                    File destOldJar = new File(oldDir, fileName);
                                    Files.move(file.toPath(), destOldJar.toPath(), StandardCopyOption.REPLACE_EXISTING);
                                    moved = true;
                                    plugin.getLogger().info("已将旧版文件 [" + fileName + "] 移动至 plugins/FancyHelper/old/");
                                } catch (IOException e) {
                                    moveError = e.getMessage();
                                    plugin.getLogger().warning("无法移动旧版文件 [" + fileName + "]: " + moveError);
                                }
                            }
                        }
                    }
                }

                if (sender != null) {
                    sender.sendMessage(ChatColor.GOLD + "[FancyHelper] " + ChatColor.GREEN + "更新下载完成！");
                    if (moved) {
                        sender.sendMessage(ChatColor.GOLD + "[FancyHelper] " + ChatColor.YELLOW + "旧版本已成功移动至 plugins/FancyHelper/old/");
                    } else if (!moveError.isEmpty()) {
                        sender.sendMessage(ChatColor.GOLD + "[FancyHelper] " + ChatColor.RED + "提示：由于系统锁定，部分旧版 JAR 无法自动移动。");
                        sender.sendMessage(ChatColor.GOLD + "[FancyHelper] " + ChatColor.RED + "请在下次重启前手动处理。");
                    }
                    sender.sendMessage(ChatColor.GOLD + "[FancyHelper] " + ChatColor.LIGHT_PURPLE + "新版本已就绪: " + ChatColor.WHITE + newJarName);
                    if (!autoReload) {
                        sender.sendMessage(ChatColor.GOLD + "[FancyHelper] " + ChatColor.LIGHT_PURPLE + "请重启服务器或使用 PlugMan 重载以完成更新。");
                    }
                }

                if (autoReload && sender != null) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        sender.performCommand("fancy reload deeply");
                    });
                }
            } catch (IOException e) {
                plugin.getLogger().severe("更新下载失败: " + e.getMessage());
                if (sender != null) {
                    sender.sendMessage(ChatColor.GOLD + "[FancyHelper] " + ChatColor.RED + "更新下载失败: " + e.getMessage());
                }
            }
        });
    }

    /**
     * 比较版本号。
     * @param current 当前版本
     * @param latest 最新版本
     * @return 如果最新版本大于当前版本则返回 true
     */
    private boolean isNewerVersion(String current, String latest) {
        if (current == null || latest == null) return false;
        
        String[] currentParts = current.split("\\.");
        String[] latestParts = latest.split("\\.");
        
        int length = Math.max(currentParts.length, latestParts.length);
        for (int i = 0; i < length; i++) {
            int curr = i < currentParts.length ? parseVersionPart(currentParts[i]) : 0;
            int late = i < latestParts.length ? parseVersionPart(latestParts[i]) : 0;
            
            if (late > curr) return true;
            if (late < curr) return false;
        }
        return false;
    }

    private int parseVersionPart(String part) {
        try {
            return Integer.parseInt(part.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!plugin.getConfigManager().isOpUpdateNotify()) {
            return;
        }
        Player player = event.getPlayer();
        if (hasUpdate && player.isOp()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                player.sendMessage(ChatColor.GOLD + "[FancyHelper] " + ChatColor.YELLOW + "检测到新版本: " + ChatColor.WHITE + "v" + latestVersion);
                player.sendMessage(ChatColor.GOLD + "[FancyHelper] " + ChatColor.YELLOW + "使用 " + ChatColor.AQUA + "/fancy upgrade" + ChatColor.YELLOW + " 自动下载并更新。");
            }, 40L); // 延迟 2 秒提示
        }
    }
}
