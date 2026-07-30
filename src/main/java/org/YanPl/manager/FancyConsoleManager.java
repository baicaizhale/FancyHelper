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
            JsonObject json = gson.fromJson(response.body(), JsonObject.class);

            if (response.statusCode() == 200 && json.has("valid") && json.get("valid").getAsBoolean()) {
                String email = json.has("user") ? json.getAsJsonObject("user").get("email").getAsString() : "";
                String tier = json.has("user") ? json.getAsJsonObject("user").get("tier").getAsString() : "";
                return new ValidateKeyResult(true, email, tier);
            }

            String error = json.has("error") ? json.get("error").getAsString() : "验证失败";
            return new ValidateKeyResult(false, null, null, error);

        } catch (Exception e) {
            return new ValidateKeyResult(false, null, null, "无法连接到 FancyConsole: " + e.getMessage());
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

    public static class ValidateKeyResult {
        public final boolean valid;
        public final String email;
        public final String tier;
        public final String error;

        public ValidateKeyResult(boolean valid, String email, String tier) {
            this.valid = valid;
            this.email = email;
            this.tier = tier;
            this.error = null;
        }

        public ValidateKeyResult(boolean valid, String email, String tier, String error) {
            this.valid = valid;
            this.email = email;
            this.tier = tier;
            this.error = error;
        }
    }
}
