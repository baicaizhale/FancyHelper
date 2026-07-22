package org.YanPl.manager;

import com.google.gson.Gson;
import org.YanPl.FancyHelper;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

/**
 * FancyConsole 注册管理器
 * 管理 server-id 和 api-key 的存储、校验和引导流程
 */
public class RegistrationManager {

    private static final String CLIENT_FILE = "client-fancy.yml";
    // private static final String DEFAULT_CONSOLE_API = "https://api.fancy.baicaizhale.top";
    // private static final String DEFAULT_CONSOLE_URL = "https://console.fancy.baicaizhale.top";

    private final FancyHelper plugin;
    private final File clientFile;
    private FileConfiguration clientConfig;
    private final HttpClient httpClient;
    private final Gson gson = new Gson();

    private String serverId;
    private String apiKey;

    public RegistrationManager(FancyHelper plugin) {
        this.plugin = plugin;
        this.clientFile = new File(plugin.getDataFolder(), CLIENT_FILE);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        load();
    }

    /**
     * 加载或初始化 client-fancy.yml
     */
    private void load() {
        if (!clientFile.exists()) {
            // 首次加载，生成 server-id
            serverId = UUID.randomUUID().toString();
            apiKey = "";
            save();
            plugin.getLogger().info("已生成服务器标识 (server-id): " + serverId);
        } else {
            clientConfig = YamlConfiguration.loadConfiguration(clientFile);
            serverId = clientConfig.getString("server-id", "");
            apiKey = clientConfig.getString("api-key", "");
            if (serverId.isEmpty()) {
                serverId = UUID.randomUUID().toString();
                save();
            }
        }
    }

    /**
     * 保存 client-fancy.yml
     */
    public void save() {
        try {
            if (!clientFile.exists()) {
                clientFile.getParentFile().mkdirs();
                clientFile.createNewFile();
            }
            clientConfig = new YamlConfiguration();
            clientConfig.set("server-id", serverId);
            clientConfig.set("api-key", apiKey);
            clientConfig.save(clientFile);
        } catch (IOException e) {
            plugin.getLogger().severe("无法保存 " + CLIENT_FILE + ": " + e.getMessage());
        }
    }

    /**
     * 是否已注册（api-key 非空且通过校验）
     */
    public boolean isRegistered() {
        return apiKey != null && !apiKey.isEmpty();
    }

    /**
     * 获取 FancyConsole 注册页面 URL（带 server-id 参数）
     */
    public String getRegistrationUrl() {
        return "https://console.fancy.baicaizhale.top/register?server=" + serverId;
    }

    /**
     * 获取服务器标识
     */
    public String getServerId() {
        return serverId;
    }

    /**
     * 获取 FancyConsole API Key
     */
    public String getApiKey() {
        return apiKey;
    }

    /**
     * 获取 FancyConsole API 基础地址
     */
    public String getApiUrl() {
        return "https://api.fancy.baicaizhale.top";
    }

    /**
     * 向远程服务校验 API Key 有效性
     * @param key 用户输入的 API Key (fc_...)
     * @return 校验成功返回 true
     * @throws IOException 网络错误或校验失败
     */
    public boolean validateKey(String key) throws IOException {
        if (key == null || key.trim().isEmpty()) {
            throw new IOException("API Key 不能为空");
        }
        key = key.trim();

        String apiUrl = getApiUrl() + "/v1/validate-key";
        String json = gson.toJson(java.util.Map.of("server_id", serverId, "api_key", key));

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int statusCode = response.statusCode();
            String responseBody = response.body();

            if (statusCode == 200) {
                com.google.gson.JsonObject obj = gson.fromJson(responseBody, com.google.gson.JsonObject.class);
                if (obj.has("valid") && obj.get("valid").getAsBoolean()) {
                    this.apiKey = key;
                    save();
                    return true;
                }
            }

            // 提取错误信息
            String errorMsg = "API Key 校验失败";
            try {
                com.google.gson.JsonObject obj = gson.fromJson(responseBody, com.google.gson.JsonObject.class);
                if (obj.has("error")) {
                    errorMsg = obj.get("error").getAsString();
                }
            } catch (Exception ignored) {}

            throw new IOException(errorMsg);

        } catch (IOException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("校验请求被中断", e);
        } catch (Exception e) {
            throw new IOException("无法连接到 FancyConsole: " + e.getMessage());
        }
    }

    /**
     * 重新生成本地 API Key（强制重新注册）
     */
    public void reset() {
        this.apiKey = "";
        save();
    }

    public void shutdown() {
        // HttpClient 无需显式关闭
    }
}
