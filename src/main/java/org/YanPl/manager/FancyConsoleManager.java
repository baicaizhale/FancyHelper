package org.YanPl.manager;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.YanPl.FancyHelper;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

/**
 * FancyConsole 集成管理器
 * 管理 client-fancy.yml、API Key 注册/绑定、FancyConsole 代理路由。
 */
public class FancyConsoleManager {

    private static final String DEFAULT_CONSOLE_URL = "https://api.fancy.baicaizhale.top";
    private static final String CONFIG_FILE_NAME = "client-fancy.yml";

    private final FancyHelper plugin;
    private final Gson gson = new Gson();
    private File configFile;
    private FileConfiguration config;
    private HttpClient httpClient;

    public FancyConsoleManager(FancyHelper plugin) {
        this.plugin = plugin;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        initConfig();
    }

    /**
     * 初始化 client-fancy.yml
     */
    private void initConfig() {
        configFile = new File(plugin.getDataFolder(), CONFIG_FILE_NAME);
        if (!configFile.exists()) {
            createDefaultConfig();
        }
        config = YamlConfiguration.loadConfiguration(configFile);
    }

    private void createDefaultConfig() {
        try {
            configFile.getParentFile().mkdirs();
            configFile.createNewFile();
            config = YamlConfiguration.loadConfiguration(configFile);
            config.set("server-id", UUID.randomUUID().toString());
            config.set("api-key", "");
            save();
            plugin.getLogger().info("已生成 " + CONFIG_FILE_NAME);
        } catch (IOException e) {
            plugin.getLogger().severe("无法创建 " + CONFIG_FILE_NAME + ": " + e.getMessage());
        }
    }

    public void save() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().warning("无法保存 " + CONFIG_FILE_NAME + ": " + e.getMessage());
        }
    }

    /**
     * 重新加载 client-fancy.yml（用于 /cli reload）
     */
    public void reload() {
        config = YamlConfiguration.loadConfiguration(configFile);
    }

    /**
     * 是否有可用的 FancyConsole API Key
     */
    public boolean hasApiKey() {
        String key = config.getString("api-key", "");
        return !key.isEmpty();
    }

    /**
     * 获取 FancyConsole API Key
     */
    public String getApiKey() {
        return config.getString("api-key", "");
    }

    /**
     * 获取服务器 ID
     */
    public String getServerId() {
        return config.getString("server-id", "");
    }

    /**
     * 设置 API Key 并保存
     */
    public void setApiKey(String apiKey) {
        config.set("api-key", apiKey);
        save();
    }

    /**
     * 获取 FancyConsole 基础 URL
     */
    public String getConsoleUrl() {
        return DEFAULT_CONSOLE_URL;
    }

    /**
     * 检查 FancyConsole 服务是否健康（GET /health）。
     * @return true 表示 HTTP 200（服务可用）；连接失败/超时/非 200 一律返回 false
     */
    public boolean checkHealth() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(getConsoleUrl() + "/health"))
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 判断 HTTP 状态码是否表示"服务不可用"（5xx 及网关错误）。
     * 仅把 >=500 视为服务不可用，避免把"key 确实错误"的 4xx（如 401/403）误判为宕机。
     *
     * @param status HTTP 状态码
     * @return true 表示服务端错误（服务宕机/过载/网关错误）
     */
    public static boolean isServiceUnavailableStatus(int status) {
        return status >= 500;
    }

    /**
     * 向 FancyConsole 验证 API Key 是否有效
     * @param apiKey 要验证的 API Key
     * @return 验证结果
     */
    public ValidateKeyResult validateKey(String apiKey) {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("api_key", apiKey);
            body.addProperty("server_id", getServerId());

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(getConsoleUrl() + "/api/fancyhelper/validate-key"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            // 服务不可用（5xx/网关错误）：fancyapi 宕机时网关常返回 502/503/504，
            // 这不是 key 无效，不得让上层误报"API Key 无效"。
            if (isServiceUnavailableStatus(response.statusCode())) {
                return ValidateKeyResult.serviceUnavailable("FancyConsole 服务不可用 (HTTP " + response.statusCode() + ")");
            }

            JsonObject json;
            try {
                json = gson.fromJson(response.body(), JsonObject.class);
            } catch (Exception e) {
                // 响应体非 JSON（如网关 HTML 错误页）：无法判断 key 有效性，
                // 视为服务异常而非 key 无效，避免误报。
                return ValidateKeyResult.serviceUnavailable("FancyConsole 响应解析失败");
            }

            if (response.statusCode() == 200 && json.has("valid") && json.get("valid").getAsBoolean()) {
                String email = json.has("user") ? json.getAsJsonObject("user").get("email").getAsString() : "";
                String tier = json.has("user") ? json.getAsJsonObject("user").get("tier").getAsString() : "";
                return new ValidateKeyResult(true, email, tier);
            }

            String error = json.has("error") ? json.get("error").getAsString() : "验证失败";
            return new ValidateKeyResult(false, null, null, error);

        } catch (Exception e) {
            return ValidateKeyResult.serviceUnavailable("无法连接到 FancyConsole: " + e.getMessage());
        }
    }

    /**
     * 生成 FancyConsole 注册链接
     */
    public String getRegistrationUrl() {
        return "https://console.fancy.baicaizhale.top/register?server=" + getServerId();
    }

    /**
     * API Key 是否已配置且有效（快速检查）
     */
    public boolean isReady() {
        return hasApiKey();
    }

    /**
     * 向 FancyConsole 上报服务器统计数据
     * @param snapshot 统计数据快照
     * @return 是否上报成功
     */
    public boolean reportStats(StatsManager.StatsSnapshot snapshot) {
        try {
            JsonObject body = new JsonObject();
            // Don't include api_key in body - use Authorization header
            body.addProperty("server_id", getServerId());
            body.addProperty("reported_at", java.time.Instant.now().toString());

            // 环境信息
            body.addProperty("plugin_version", snapshot.pluginVersion);
            body.addProperty("server_software", snapshot.serverSoftware);
            body.addProperty("minecraft_version", snapshot.minecraftVersion);
            body.addProperty("java_version", snapshot.javaVersion);
            body.addProperty("os_name", snapshot.osName);
            body.addProperty("os_arch", snapshot.osArch);
            body.addProperty("available_processors", snapshot.availableProcessors);
            body.addProperty("max_memory_mb", snapshot.maxMemoryMb);
            body.addProperty("online_mode", snapshot.onlineMode);
            body.addProperty("ai_provider", snapshot.aiProvider);

            // 累计计数
            body.addProperty("total_input_tokens", snapshot.totalInputTokens);
            body.addProperty("total_output_tokens", snapshot.totalOutputTokens);
            body.addProperty("cli_entry_count", snapshot.cliEntryCount);
            body.addProperty("conversation_count", snapshot.conversationCount);
            body.addProperty("tool_success_count", snapshot.toolSuccessCount);
            body.addProperty("tool_failure_count", snapshot.toolFailureCount);
            body.addProperty("error_count", snapshot.errorCount);
            body.addProperty("total_thinking_time_ms", snapshot.totalThinkingTimeMs);

            // 快照
            body.addProperty("online_players", snapshot.onlinePlayers);
            body.addProperty("active_cli_sessions", snapshot.activeCliSessions);
            body.addProperty("loaded_skills", snapshot.loadedSkills);
            body.addProperty("indexed_commands", snapshot.indexedCommands);
            body.addProperty("uptime_seconds", snapshot.uptimeSeconds);
            body.addProperty("mode_yolo_count", snapshot.modeYoloCount);
            body.addProperty("mode_smart_count", snapshot.modeSmartCount);
            body.addProperty("mode_normal_count", snapshot.modeNormalCount);
            body.addProperty("mode_plan_count", snapshot.modePlanCount);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(getConsoleUrl() + "/api/fancyhelper/report-stats"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            // 上报失败静默处理（后台任务，不打扰控制台）
            return false;
        }
    }

    public static class ValidateKeyResult {
        public final boolean valid;
        public final String email;
        public final String tier;
        public final String error;
        /** 服务不可用（宕机/网关错误/网络异常）：不能据此判定 key 无效。 */
        public final boolean serviceUnavailable;

        public ValidateKeyResult(boolean valid, String email, String tier) {
            this(valid, email, tier, null, false);
        }

        public ValidateKeyResult(boolean valid, String email, String tier, String error) {
            this(valid, email, tier, error, false);
        }

        public ValidateKeyResult(boolean valid, String email, String tier, String error, boolean serviceUnavailable) {
            this.valid = valid;
            this.email = email;
            this.tier = tier;
            this.error = error;
            this.serviceUnavailable = serviceUnavailable;
        }

        /** 服务不可用（宕机/网关错误/网络异常）时的结果：不视为 key 无效。 */
        public static ValidateKeyResult serviceUnavailable(String error) {
            return new ValidateKeyResult(false, null, null, error, true);
        }
    }
}
