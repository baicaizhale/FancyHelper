package org.YanPl.manager;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.YanPl.FancyHelper;
import org.YanPl.model.Skill;
import org.YanPl.util.ColorUtil;
import org.bukkit.Bukkit;
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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Skill 更新管理器
 * 负责从远程仓库检查并下载 Skill 更新
 */
public class SkillUpdateManager implements Listener {

    private final FancyHelper plugin;
    private final SkillManager skillManager;
    private final HttpClient httpClient;

    // 待更新的 Skill（skillId -> 最新版本号）
    private final Map<String, String> pendingUpdates = new ConcurrentHashMap<>();

    // 是否正在检查
    private boolean checking = false;
    private boolean hasUpdates = false;

    public SkillUpdateManager(FancyHelper plugin, SkillManager skillManager) {
        this.plugin = plugin;
        this.skillManager = skillManager;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private String msg(String text) {
        return ColorUtil.translateCustomColors("§zFancyHelper §7> §f" + text);
    }

    private String getPrimaryUrl(String path) {
        return plugin.getConfigManager().getSkillPrimaryMirror() + path;
    }

    private String getMirrorUrl(String path) {
        return plugin.getConfigManager().getSkillUpdateMirror()
                + plugin.getConfigManager().getSkillRepoBase() + path;
    }

    private String getDirectUrl(String path) {
        return plugin.getConfigManager().getSkillRepoBase() + path;
    }

    // ==================== 公开方法 ====================

    /**
     * 启动时检查并自动下载更新（仅控制台日志）
     */
    public void checkAndUpdate() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                doCheck(null, true);
            } catch (Exception e) {
                plugin.getLogger().warning("[SkillUpdate] 自动更新失败: " + e.getMessage());
            }
        });
    }

    /**
     * 检查所有可更新 Skill 的版本
     */
    public void checkForUpdates() {
        checkForUpdates(null);
    }

    /**
     * 检查所有可更新 Skill 的版本，并通知指定玩家
     */
    public void checkForUpdates(Player sender) {
        checkForUpdates(sender, false);
    }

    /**
     * 检查所有可更新 Skill 的版本
     * @param sender 接收通知的玩家，可为 null
     * @param silentNoUpdate true=无更新时不发消息（管理员进服等自动场景），false=始终通知（手动执行命令）
     */
    public void checkForUpdates(Player sender, boolean silentNoUpdate) {
        if (checking) {
            if (sender != null) {
                sender.sendMessage(msg("正在检查中，请稍候..."));
            }
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> doCheck(sender, silentNoUpdate));
    }

    /**
     * 下载所有待更新的 Skill
     */
    public void downloadUpdates() {
        downloadUpdates(null);
    }

    /**
     * 下载所有待更新的 Skill，并通知指定玩家
     */
    public void downloadUpdates(Player sender) {
        if (pendingUpdates.isEmpty()) {
            notify(sender, "§e没有待更新的 Skill，请先执行 checkupdate");
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> doDownload(sender));
    }

    // ==================== 核心逻辑 ====================

    /**
     * 执行检查（必须在异步线程调用）
     */
    private void doCheck(Player sender, boolean silentNoUpdate) {
        checking = true;
        pendingUpdates.clear();
        hasUpdates = false;

        try {
            String body = fetchWithFallback(
                    getPrimaryUrl("manifest.json"),
                    getMirrorUrl("manifest.json"),
                    getDirectUrl("manifest.json"));
            if (body == null) {
                notify(sender, "§c获取 Skill 更新清单失败（主源/镜像/直连均不可用）");
                plugin.getLogger().warning("[SkillUpdate] 获取 manifest 失败（主源/镜像/直连均不可用）");
                return;
            }

            JsonObject manifest = JsonParser.parseString(body).getAsJsonObject();
            if (!manifest.has("skills")) {
                notify(sender, "§cSkill 更新清单格式错误");
                return;
            }

            JsonObject skillsObj = manifest.getAsJsonObject("skills");
            List<Skill> localUpdatable = skillManager.getRegistry().getUpdatableSkills();

            int checkedCount = 0;

            for (Skill localSkill : localUpdatable) {
                String id = localSkill.getId();
                if (!skillsObj.has(id)) continue;

                JsonObject remoteSkill = skillsObj.getAsJsonObject(id);
                String remoteVersion = getJsonString(remoteSkill, "version");
                String localVersion = localSkill.getMetadata().getVersion();

                // 只要版本不同就更新，不比较高低
                if (remoteVersion != null && !remoteVersion.equals(localVersion)) {
                    pendingUpdates.put(id, remoteVersion);
                }
                checkedCount++;
            }

            // 本地没有但远端有的技能（新技能）
            for (Map.Entry<String, JsonElement> entry : skillsObj.entrySet()) {
                String id = entry.getKey();
                if (!skillManager.hasSkill(id)) {
                    JsonObject remoteSkill = entry.getValue().getAsJsonObject();
                    String remoteVersion = getJsonString(remoteSkill, "version");
                    pendingUpdates.put(id, remoteVersion != null ? remoteVersion : "1.0.0");
                }
            }

            hasUpdates = !pendingUpdates.isEmpty();
            int updateCount = pendingUpdates.size();

            if (sender != null) {
                if (hasUpdates) {
                    sender.sendMessage(ColorUtil.translateCustomColors("§zFancyHelper §7> §a发现 §e" + updateCount + " §a个 Skill 更新，正在自动下载..."));
                    for (Map.Entry<String, String> entry : pendingUpdates.entrySet()) {
                        Skill local = skillManager.getSkill(entry.getKey());
                        String localVer = local != null ? local.getMetadata().getVersion() : "未安装";
                        sender.sendMessage(" §7- §b" + entry.getKey() + " §7" + localVer + " §7→ §a" + entry.getValue());
                    }
                } else if (!silentNoUpdate) {
                    sender.sendMessage(ColorUtil.translateCustomColors("§zFancyHelper §7> §a所有 Skill 已是最新版本"));
                }
            }

            if (hasUpdates) {
                plugin.getLogger().info("[SkillUpdate] 发现 " + updateCount + " 个 Skill 可更新，开始自动下载...");
                doDownload(sender);
            } else {
                plugin.getLogger().info("[SkillUpdate] 所有 Skill 已是最新 (" + checkedCount + " 个检查)");
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[SkillUpdate] 检查更新异常", e);
            notify(sender, "§c检查更新时发生错误: " + e.getMessage());
        } finally {
            checking = false;
        }
    }

    /**
     * 执行下载（必须在异步线程调用）
     */
    private void doDownload(Player sender) {
        int success = 0;
        int failed = 0;
        int total = pendingUpdates.size();

        notify(sender, "§f开始下载 §e" + total + " §f个 Skill 更新...");

        for (Map.Entry<String, String> entry : pendingUpdates.entrySet()) {
            String skillId = entry.getKey();

            if (downloadSkillFile(skillId)) {
                success++;
                plugin.getLogger().info("[SkillUpdate] 已更新 Skill: " + skillId + " -> v" + entry.getValue());
            } else {
                failed++;
                plugin.getLogger().warning("[SkillUpdate] 更新失败: " + skillId);
            }
        }

        final int finalSuccess = success;
        final int finalFailed = failed;
        Bukkit.getScheduler().runTask(plugin, () -> {
            skillManager.reloadSkills();
            pendingUpdates.clear();
            hasUpdates = false;

            notify(sender, "§aSkill 更新完成！成功: §e" + finalSuccess + "§a, 失败: §c" + finalFailed);
            plugin.getLogger().info("[SkillUpdate] 更新完成: " + finalSuccess + " 成功, " + finalFailed + " 失败");
        });
    }

    /**
     * 下载单个 Skill 文件到 skills 目录
     */
    private boolean downloadSkillFile(String skillId) {
        String path = skillId + "/skill.md";
        SkillLoader loader = skillManager.getLoader();

        try {
            // 三级级联：主源 → 镜像(ghproxy) → 直连(GitHub)
            HttpResponse<InputStream> response = fetchInputStreamWithFallback(
                    getPrimaryUrl(path),
                    getMirrorUrl(path),
                    getDirectUrl(path));
            if (response == null || response.statusCode() != 200) {
                int code = response != null ? response.statusCode() : 0;
                plugin.getLogger().warning("[SkillUpdate] 下载 " + skillId + " 失败: HTTP " + code);
                return false;
            }

            String sanitizedId = loader.sanitizeFileName(skillId);
            File skillDir = new File(loader.getSkillsDir(), sanitizedId);
            if (!skillDir.exists()) {
                skillDir.mkdirs();
            }
            File targetFile = new File(skillDir, "skill.md");

            try (InputStream is = response.body()) {
                Files.copy(is, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }

            return true;

        } catch (IOException e) {
            plugin.getLogger().warning("[SkillUpdate] 下载 " + skillId + " 失败: " + e.getMessage());
            return false;
        }
    }

    // ==================== 状态查询 ====================

    public boolean hasUpdates() {
        return hasUpdates;
    }

    public Map<String, String> getPendingUpdates() {
        return Collections.unmodifiableMap(pendingUpdates);
    }

    // ==================== 工具方法 ====================

    private void notify(Player player, String text) {
        if (player != null) {
            player.sendMessage(ColorUtil.translateCustomColors("§zFancyHelper §7> §f" + text));
        }
    }

    private String getJsonString(JsonObject obj, String key) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            return obj.get(key).getAsString();
        }
        return null;
    }

    /**
     * 三级级联请求：主源 → 镜像(ghproxy) → 直连(GitHub)
     * @return 响应体，全部失败返回 null
     */
    private String fetchWithFallback(String primaryUrl, String mirrorUrl, String directUrl) {
        // 第一层：主源
        String body = tryFetchString(primaryUrl);
        if (body != null) return body;
        plugin.getLogger().info("[SkillUpdate] 主源不可用，尝试镜像源...");

        // 第二层：镜像(ghproxy)
        body = tryFetchString(mirrorUrl);
        if (body != null) return body;
        plugin.getLogger().info("[SkillUpdate] 镜像源不可用，尝试直连...");

        // 第三层：直连(GitHub)
        body = tryFetchString(directUrl);
        if (body == null) {
            plugin.getLogger().warning("[SkillUpdate] 直连也失败");
        }
        return body;
    }

    private String tryFetchString(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "FancyHelper-SkillUpdater")
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) return response.body();
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
        }
        return null;
    }

    /**
     * 三级级联请求输入流：主源 → 镜像(ghproxy) → 直连(GitHub)
     * @return 响应，全部失败返回 null
     */
    private HttpResponse<InputStream> fetchInputStreamWithFallback(String primaryUrl, String mirrorUrl, String directUrl) {
        // 第一层：主源
        HttpResponse<InputStream> response = tryFetchStream(primaryUrl);
        if (response != null && response.statusCode() == 200) return response;
        plugin.getLogger().info("[SkillUpdate] 主源不可用，尝试镜像源下载...");

        // 第二层：镜像(ghproxy)
        response = tryFetchStream(mirrorUrl);
        if (response != null && response.statusCode() == 200) return response;
        plugin.getLogger().info("[SkillUpdate] 镜像源不可用，尝试直连下载...");

        // 第三层：直连(GitHub)
        response = tryFetchStream(directUrl);
        if (response == null || response.statusCode() != 200) {
            int code = response != null ? response.statusCode() : 0;
            plugin.getLogger().warning("[SkillUpdate] 直连下载返回 HTTP " + code);
        }
        return response;
    }

    private HttpResponse<InputStream> tryFetchStream(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "FancyHelper-SkillUpdater")
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();
            return httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
        }
        return null;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!plugin.getConfigManager().isOpUpdateNotify()) return;
        Player player = event.getPlayer();
        if (!player.isOp()) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> checkForUpdates(player, true), 60L);
    }
}
