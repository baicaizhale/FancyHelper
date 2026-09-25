package org.YanPl.manager;

import org.YanPl.FancyHelper;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * ProtocolLib 自动引导：ProtocolLib 未安装时，按服务器 MC 版本下载匹配的 ProtocolLib
 * 并以插件形式加载启用，免去管理员手工挑选版本的麻烦。
 *
 * 必须在主线程调用（loadPlugin/enablePlugin 的线程要求），本类只在 onEnable 流程中使用。
 *
 * 版本映射依据（2026-09 查证）：
 * - 各 ProtocolLib Release 说明与其 master 源码的支持门槛常量（MAXIMUM_MINECRAFT_VERSION = "26.2"）
 * - MC 已改用年份制版本号（26.1/26.2/26.3 = 2026 年的更新）
 * - 正式版 5.4.0（2025-08）之后新 MC 版本的支持先落在 dev-build（5.5.0-SNAPSHOT），
 *   故映射表允许指向 dev-build，这也是"仅用正式版"规则唯一的例外。
 * - 下载源：GitHub Releases，每个 tag 下资产固定为 ProtocolLib.jar
 */
public class ProtocolLibBootstrap {

    private static final String GITHUB_ASSET_URL =
            "https://github.com/dmulloy2/ProtocolLib/releases/download/%s/ProtocolLib.jar";
    private static final long MIN_JAR_BYTES = 100 * 1024L;   // 合法 ProtocolLib.jar 远大于此，用于挡 404 页面
    private static final long MAX_JAR_BYTES = 30 * 1024 * 1024L;

    /**
     * MC 版本区间 → ProtocolLib 发布 tag（区间含端点，从高到低排列）。
     * 上游发布新版本后在此表追加一行即可。
     */
    private static final String[][] SUPPORT_RANGES = {
            {"26.1", "26.2", "dev-build"},
            {"1.21.9", "1.21.11", "dev-build"},
            {"1.21.2", "1.21.8", "5.4.0"},
            {"1.21", "1.21.1", "5.3.0"},
            {"1.20.2", "1.20.6", "5.2.0"},
            {"1.20", "1.20.1", "5.1.0"},
            {"1.19", "1.19.4", "5.0.0"},
            {"1.18", "1.18.2", "4.8.0"},
    };

    private final FancyHelper plugin;

    public ProtocolLibBootstrap(FancyHelper plugin) {
        this.plugin = plugin;
    }

    /**
     * 确保 ProtocolLib 已安装并启用。调用前请确认其未启用。
     *
     * @return true 表示 ProtocolLib 现在已启用；false 表示失败（原因已记入日志）
     */
    public boolean ensureInstalled() {
        if (!plugin.getConfig().getBoolean("settings.protocol_lib_auto_install", true)) {
            plugin.getLogger().info("[ProtocolLib-Bootstrap] 已关闭自动下载（settings.protocol_lib_auto_install=false），跳过。");
            return false;
        }

        File target = new File(plugin.getDataFolder().getParentFile(), "ProtocolLib.jar");

        // plugins/ 下已有 jar（管理员手动放入但尚未加载）：直接加载，不下载
        if (!target.isFile()) {
            String tag = resolveTag();
            if (tag == null) {
                return false;
            }
            try {
                plugin.getLogger().info("[ProtocolLib-Bootstrap] 正在下载 ProtocolLib " + tag + " ...");
                download(tag, target);
                plugin.getLogger().info("[ProtocolLib-Bootstrap] 下载完成: " + target.getAbsolutePath());
            } catch (Throwable t) {
                plugin.getLogger().severe("[ProtocolLib-Bootstrap] 下载 ProtocolLib " + tag + " 失败: " + t.getMessage());
                plugin.getLogger().severe("[ProtocolLib-Bootstrap] 可手动下载放入 plugins/ 后重启，或在 config.yml 将 protocol_lib_version 指定版本。");
                target.delete();
                return false;
            }
        }

        try {
            Plugin loaded = Bukkit.getPluginManager().loadPlugin(target);
            if (loaded == null) {
                throw new IllegalStateException("loadPlugin 返回 null");
            }
            Bukkit.getPluginManager().enablePlugin(loaded);
            if (!loaded.isEnabled()) {
                // 常见于 ProtocolLib 自身的 MC 版本门槛拒绝当前服务器
                plugin.getLogger().severe("[ProtocolLib-Bootstrap] ProtocolLib " + loaded.getDescription().getVersion() + " 启用失败，请查看上方日志。");
                return false;
            }
            plugin.getLogger().info("[ProtocolLib-Bootstrap] ProtocolLib " + loaded.getDescription().getVersion() + " 已自动安装并启用。");
            return true;
        } catch (Throwable t) {
            plugin.getLogger().severe("[ProtocolLib-Bootstrap] 加载 ProtocolLib 失败: " + t.getMessage());
            return false;
        }
    }

    /**
     * 解析要下载的发布 tag。
     * 优先级：配置强制指定 → 内置映射表 → 表未命中时取 dev-build（上游对新 MC 的支持总是先出现在 dev-build）。
     *
     * @return tag，null 表示判断为"上游尚不支持当前 MC 版本"
     */
    private String resolveTag() {
        String forced = plugin.getConfig().getString("settings.protocol_lib_version", "").trim();
        String mcVersion = mcVersion();

        if (!forced.isEmpty()) {
            plugin.getLogger().info("[ProtocolLib-Bootstrap] 使用配置指定的版本: " + forced + "（MC " + mcVersion + "）");
            return forced;
        }

        for (String[] range : SUPPORT_RANGES) {
            if (compare(mcVersion, range[0]) >= 0 && compare(mcVersion, range[1]) <= 0) {
                plugin.getLogger().info("[ProtocolLib-Bootstrap] MC " + mcVersion + " → ProtocolLib " + range[2]);
                return range[2];
            }
        }

        // 表未命中：高于表中最大区间的新 MC 版本。dev-build 支持范围最新，交给它自检把关；
        // 低于 1.18 的旧版本同样落到这里，但会被 ProtocolLib 自身的版本门槛拒绝，属预期行为。
        if (compare(mcVersion, "26.1") >= 0) {
            plugin.getLogger().info("[ProtocolLib-Bootstrap] MC " + mcVersion + " 不在映射表中，尝试最新 dev-build（上游对新版本的支持先于正式版）。");
            return "dev-build";
        }
        plugin.getLogger().severe("[ProtocolLib-Bootstrap] MC " + mcVersion + " 尚无受支持的 ProtocolLib 版本（当前 dev-build 支持到 26.2）。");
        return null;
    }

    /** 服务器 MC 版本，如 "1.21.4"（去掉 Bukkit 版本串里的 "-R0.1-SNAPSHOT" 后缀） */
    private String mcVersion() {
        String v = Bukkit.getBukkitVersion();
        int dash = v.indexOf('-');
        return dash > 0 ? v.substring(0, dash) : v;
    }

    /** 按 '.' 分段数值比较版本号，正确处理 1.21.11 > 1.21.9 与 1.21 == 1.21.0 */
    static int compare(String a, String b) {
        String[] sa = a.split("\\.");
        String[] sb = b.split("\\.");
        int len = Math.max(sa.length, sb.length);
        for (int i = 0; i < len; i++) {
            int va = i < sa.length ? Integer.parseInt(sa[i]) : 0;
            int vb = i < sb.length ? Integer.parseInt(sb[i]) : 0;
            if (va != vb) return Integer.compare(va, vb);
        }
        return 0;
    }

    /** 下载发布资产到 target：先写临时文件，校验 ZIP 魔数与大小后再原子改名 */
    private void download(String tag, File target) throws Exception {
        File tmp = new File(target.getParentFile(), target.getName() + ".tmp");
        HttpURLConnection conn = (HttpURLConnection) new URL(String.format(GITHUB_ASSET_URL, tag)).openConnection();
        conn.setRequestProperty("User-Agent", "FancyHelper-ProtocolLib-Bootstrap");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(60000);
        conn.setInstanceFollowRedirects(true);

        int code = conn.getResponseCode();
        if (code != 200) {
            throw new IllegalStateException("HTTP " + code + " from " + conn.getURL());
        }

        try (InputStream in = conn.getInputStream()) {
            Files.copy(in, tmp.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } finally {
            conn.disconnect();
        }

        long size = tmp.length();
        if (size < MIN_JAR_BYTES || size > MAX_JAR_BYTES) {
            tmp.delete();
            throw new IllegalStateException("下载内容大小异常: " + size + " bytes");
        }
        try (InputStream in = Files.newInputStream(tmp.toPath())) {
            if (in.read() != 'P' || in.read() != 'K') {
                tmp.delete();
                throw new IllegalStateException("下载内容不是合法的 jar（缺少 ZIP 魔数）");
            }
        }

        Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }
}
