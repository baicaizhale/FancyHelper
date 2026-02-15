package org.YanPl.util;

import com.google.gson.JsonObject;
import org.YanPl.FancyHelper;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 云端错误上报工具类
 */
public class CloudErrorReport {

    private final JavaPlugin plugin;
    private final String workerUrl = "https://report.2651557041.workers.dev/";
    private final String secretKey = "bug-report"; // 与Worker对应
    private final Set<Integer> reportedHashes = ConcurrentHashMap.newKeySet();

    /**
     * 构造函数
     *
     * @param plugin 插件实例
     */
    public CloudErrorReport(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 手动上报异常
     *
     * @param throwable 抛出的异常
     */
    public void report(Throwable throwable) {
        if (throwable == null) return;

        // 检查配置是否启用自动上报
        if (plugin instanceof FancyHelper) {
            if (!((FancyHelper) plugin).getConfigManager().isAutoReportEnabled()) {
                return;
            }
        }

        // 插件已禁用时，Bukkit 调度器将拒绝注册任务；此时跳过上报以避免二次异常
        if (!plugin.isEnabled()) {
            return;
        }

        // 1. 获取堆栈第一行计算哈希，防止重复上报
        int errorHash = throwable.getStackTrace().length > 0
                ? throwable.getStackTrace()[0].hashCode() + throwable.toString().hashCode()
                : throwable.toString().hashCode();

        if (!reportedHashes.add(errorHash)) return;

        // 2. 异步执行请求
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(workerUrl).openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(5000);

                JsonObject json = new JsonObject();
                json.addProperty("key", secretKey);
                json.addProperty("version", plugin.getDescription().getVersion());
                json.addProperty("mc_version", Bukkit.getBukkitVersion());
                json.addProperty("log", getStackTraceString(throwable));

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(json.toString().getBytes(StandardCharsets.UTF_8));
                }

                if (conn.getResponseCode() == 200) {
                    plugin.getLogger().info("已自动向开发者提交崩溃报告，感谢支持。");
                }
            } catch (Exception ignored) {
            }
        });
    }

    /**
     * 获取异常堆栈字符串
     *
     * @param t 异常
     * @return 堆栈字符串
     */
    private String getStackTraceString(Throwable t) {
        StringBuilder sb = new StringBuilder(t.toString()).append("\n");
        for (StackTraceElement ste : t.getStackTrace()) {
            sb.append("\tat ").append(ste.toString()).append("\n");
        }
        if (t.getCause() != null) {
            sb.append("Caused by: ").append(getStackTraceString(t.getCause()));
        }
        return sb.toString();
    }
}
