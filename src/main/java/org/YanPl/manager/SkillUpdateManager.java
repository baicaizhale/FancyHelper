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

    private String prefix() {
        return "§zFancyHelper §7> §f";
    }

    private String msg(String text) {
        return ColorUtil.translateCustomColors(prefix() + text);
    }

    /**
     * 获取仓库 manifest 的基础 URL（含镜像前缀）
     */
    private String getManifestUrl() {
        String mirror = plugin.getConfigManager().getSkillUpdateMirror();
        String base = plugin.getConfigManager().getSkillRepoBase();
        return mirror + base + "manifest.json";
    }

    /**
     * 获取单个 Skill 文件的下载 URL
     */
    private String getSkillFileUrl(String skillId) {
        String mirror = plugin.getConfigManager().getSkillUpdateMirror();
        String base = plugin.getConfigManager().getSkillRepoBase();
        return mirror + base + skillId + "/skill.md";
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
        if (checking) {
            if (sender != null) {
                sender.sendMessage(msg("正在检查中，请稍候..."));
            }
            return;
        }

        checking = true;
        pendingUpdates.clear();
        hasUpdates = false;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // 获取 manifest
                String manifestUrl = getManifestUrl();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(manifestUrl))
                        .header("User-Agent", "FancyHelper-SkillUpdater")
                        .timeout(Duration.ofSeconds(15))
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    notify(sender, "§c获取 Skill 更新清单失败 (HTTP " + response.statusCode() + ")");
                    plugin.getLogger().warning("[SkillUpdate] 获取 manifest 失败: HTTP " + response.statusCode());
                    return;
                }

                JsonObject manifest = JsonParser.parseString(response.body()).getAsJsonObject();
                if (!manifest.has("skills")) {
                    notify(sender, "§cSkill 更新清单格式错误");
                    return;
                }

                JsonObject skills = manifest.getAsJsonObject("skills");
                List<Skill> localUpdatable = skillManager.getRegistry().getUpdatableSkills();

                // 对比版本
                int checkedCount = 0;
                int updateCount = 0;

                for (Skill localSkill : localUpdatable) {
                    String id = localSkill.getId();
                    if (!skills.has(id)) continue;

                    JsonObject remoteSkill = skills.getAsJsonObject(id);
                    String remoteVersion = getJsonString(remoteSkill, "version");

                    if (remoteVersion != null && isNewerVersion(localSkill.getMetadata().getVersion(), remoteVersion)) {
                        pendingUpdates.put(id, remoteVersion);
                    }
                    checkedCount++;
                }

                // 也检查本地没有但远端有的技能（新技能）
                for (Map.Entry<String, JsonElement> entry : skills.entrySet()) {
                    String id = entry.getKey();
                    if (!skillManager.hasSkill(id)) {
                        JsonObject remoteSkill = entry.getValue().getAsJsonObject();
                        String remoteVersion = getJsonString(remoteSkill, "version");
                        pendingUpdates.put(id, remoteVersion != null ? remoteVersion : "1.0.0");
                    }
                }

                hasUpdates = !pendingUpdates.isEmpty();
                updateCount = pendingUpdates.size();

                if (sender != null) {
                    if (hasUpdates) {
                        sender.sendMessage(ColorUtil.translateCustomColors("§zFancyHelper §7> §a发现 §e" + updateCount + " §a个 Skill 可更新"));
                        for (Map.Entry<String, String> entry : pendingUpdates.entrySet()) {
                            Skill local = skillManager.getSkill(entry.getKey());
                            String localVer = local != null ? local.getMetadata().getVersion() : "未安装";
                            sender.sendMessage(" §7- §b" + entry.getKey() + " §7" + localVer + " §7→ §a" + entry.getValue());
                        }
                    } else {
                        sender.sendMessage(msg("所有 Skill 已是最新版本"));
                    }
                }

                // 控制台日志
                if (hasUpdates) {
                    plugin.getLogger().info("[SkillUpdate] 发现 " + updateCount + " 个 Skill 可更新");
                } else {
                    plugin.getLogger().info("[SkillUpdate] 所有 Skill 已是最新 (" + checkedCount + " 个检查)");
                }

            } catch (IOException e) {
                plugin.getLogger().warning("[SkillUpdate] 检查更新失败: " + e.getMessage());
                notify(sender, "§c检查 Skill 更新失败: " + e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                plugin.getLogger().warning("[SkillUpdate] 检查更新被中断");
                notify(sender, "§c检查 Skill 更新被中断");
            } finally {
                checking = false;
            }
        });
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

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
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

            // 全部下载完后重新加载 Skill
            final int finalSuccess = success;
            final int finalFailed = failed;
            Bukkit.getScheduler().runTask(plugin, () -> {
                skillManager.reloadSkills();
                pendingUpdates.clear();
                hasUpdates = false;

                notify(sender, "§aSkill 更新完成！成功: §e" + finalSuccess + "§a, 失败: §c" + finalFailed);
                plugin.getLogger().info("[SkillUpdate] 更新完成: " + finalSuccess + " 成功, " + finalFailed + " 失败");
            });
        });
    }

    /**
     * 下载单个 Skill 文件到 skills 目录
     */
    private boolean downloadSkillFile(String skillId) {
        String fileUrl = getSkillFileUrl(skillId);
        SkillLoader loader = skillManager.getLoader();

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(fileUrl))
                    .header("User-Agent", "FancyHelper-SkillUpdater")
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();

            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() != 200) {
                plugin.getLogger().warning("[SkillUpdate] 下载 " + skillId + " 失败: HTTP " + response.statusCode());
                return false;
            }

            // 保存到 skills/<id>/skill.md
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

        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            plugin.getLogger().warning("[SkillUpdate] 下载 " + skillId + " 失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 是否有待更新的 Skill
     */
    public boolean hasUpdates() {
        return hasUpdates;
    }

    /**
     * 获取待更新列表（只读）
     */
    public Map<String, String> getPendingUpdates() {
        return Collections.unmodifiableMap(pendingUpdates);
    }

    /**
     * 将消息发送给玩家和控制台
     */
    private void notify(Player player, String text) {
        if (player != null) {
            player.sendMessage(ColorUtil.translateCustomColors("§zFancyHelper §7> §f" + text));
        }
    }

    /**
     * 比较版本号
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

    private String getJsonString(JsonObject obj, String key) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            return obj.get(key).getAsString();
        }
        return null;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!plugin.getConfigManager().isOpUpdateNotify()) return;

        Player player = event.getPlayer();
        if (!player.isOp()) return;

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            checkForUpdates(player);
        }, 60L);
    }
}
