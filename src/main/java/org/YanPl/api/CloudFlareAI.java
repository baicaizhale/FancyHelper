package org.YanPl.api;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import okhttp3.*;
import org.YanPl.MineAgent;
import org.YanPl.model.DialogueSession;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * CloudFlare Workers AI API 集成
 */
public class CloudFlareAI {
    private static final String API_RUN_URL = "https://api.cloudflare.com/client/v4/accounts/%s/ai/run/%s";
    private static final String ACCOUNTS_URL = "https://api.cloudflare.com/client/v4/accounts";
    private final MineAgent plugin;
    private final OkHttpClient httpClient;
    private final Gson gson = new Gson();
    private String cachedAccountId = null;

    public CloudFlareAI(MineAgent plugin) {
        this.plugin = plugin;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(90, TimeUnit.SECONDS)
                .writeTimeout(90, TimeUnit.SECONDS)
                .build();
    }

    public OkHttpClient getHttpClient() {
        return httpClient;
    }

    /**
     * 关闭 HTTP 客户端，释放资源
     */
    public void shutdown() {
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
        if (httpClient.cache() != null) {
            try {
                httpClient.cache().close();
            } catch (IOException ignored) {}
        }
    }

    /**
     * 自动获取 CloudFlare Account ID
     */
    private String fetchAccountId() throws IOException {
        if (cachedAccountId != null) return cachedAccountId;

        String cfKey = plugin.getConfigManager().getCloudflareCfKey();
        if (cfKey.isEmpty()) {
            throw new IOException("错误: 请先在配置文件中设置 cloudflare.cf_key。");
        }

        Request request = new Request.Builder()
                .url(ACCOUNTS_URL)
                .addHeader("Authorization", "Bearer " + cfKey)
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("获取 Account ID 失败: " + response.code() + " " + response.message());
            }

            String responseBody = response.body().string();
            JsonObject resultJson = gson.fromJson(responseBody, JsonObject.class);
            
            if (resultJson.has("result") && resultJson.getAsJsonArray("result").size() > 0) {
                cachedAccountId = resultJson.getAsJsonArray("result").get(0).getAsJsonObject().get("id").getAsString();
                return cachedAccountId;
            } else {
                throw new IOException("未找到关联的 CloudFlare 账户，请检查 cf_key 权限。");
            }
        }
    }

    /**
     * 发送对话请求
     */
    public String chat(DialogueSession session, String systemPrompt) throws IOException {
        String cfKey = plugin.getConfigManager().getCloudflareCfKey();
        String model = plugin.getConfigManager().getCloudflareModel();

        if (cfKey == null || cfKey.isEmpty()) {
            return "错误: 请先在配置文件中设置 CloudFlare cf_key。";
        }

        if (model == null || model.isEmpty()) {
            model = "@cf/openai/gpt-oss-120b";
            plugin.getLogger().warning("[AI] 模型名称为空，已回退到默认值: " + model);
        }

        // 自动获取 Account ID
        String accountId;
        try {
            accountId = fetchAccountId();
        } catch (IOException e) {
            plugin.getLogger().severe("[AI Error] Failed to fetch Account ID: " + e.getMessage());
            throw e;
        }

        // 使用 /ai/run 接口，这是 CloudFlare Workers AI 的标准接口
        String url = String.format(API_RUN_URL, accountId, model);
        plugin.getLogger().info("[AI Request] URL: " + url);

        JsonArray messagesArray = new JsonArray();

        // 1. 添加系统提示词
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            JsonObject systemMsg = new JsonObject();
            systemMsg.addProperty("role", "system");
            systemMsg.addProperty("content", systemPrompt);
            messagesArray.add(systemMsg);
        }

        // 2. 添加历史记录 (role: user/assistant)
        for (DialogueSession.Message msg : session.getHistory()) {
            String content = msg.getContent();
            String role = msg.getRole();
            if (content == null || content.isEmpty() || role == null || role.isEmpty()) continue;
            
            // 跳过可能已经被误加进去的 system 消息
            if ("system".equalsIgnoreCase(role)) continue;
            
            JsonObject m = new JsonObject();
            m.addProperty("role", role);
            m.addProperty("content", content);
            messagesArray.add(m);
        }

        // 如果没有任何消息，至少添加一条占位符消息
        if (messagesArray.size() == 0) {
            JsonObject m = new JsonObject();
            m.addProperty("role", "user");
            m.addProperty("content", "Hello");
            messagesArray.add(m);
        }

        // 构建请求体
        JsonObject bodyJson = new JsonObject();
        bodyJson.add("messages", messagesArray);
        
        // 如果是 gpt-oss 模型，且使用了 /ai/v1/responses (虽然这里改回了 /run，但部分参数可能仍然通用)
        // 不过在 /run 接口中，通常不支持 reasoning 字段，除非是特定的模型
        // 为了安全起见，先移除 reasoning，因为 gpt-oss-120b 在 /run 接口下可能不支持它
        
        String bodyString = gson.toJson(bodyJson);

        plugin.getLogger().info("[AI Request] Model: " + model);
        // 不记录完整 Payload 避免日志过大，或者只记录摘要
        // plugin.getLogger().info("[AI Request] Payload: " + bodyString);

        RequestBody body = RequestBody.create(
                bodyString,
                MediaType.get("application/json; charset=utf-8")
        );

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + cfKey)
                .post(body)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            plugin.getLogger().info("[AI Response] Code: " + response.code());

            if (!response.isSuccessful()) {
                throw new IOException("AI 调用失败: " + response.code() + " - " + responseBody);
            }

            JsonObject responseJson = gson.fromJson(responseBody, JsonObject.class);
            
            // 处理 /ai/run 接口返回格式
            // 格式: { "result": { "response": "..." }, "success": true, ... }
            if (responseJson.has("result")) {
                JsonObject result = responseJson.getAsJsonObject("result");
                if (result.has("response")) {
                    return result.get("response").getAsString();
                }
            }

            // 备选格式处理 (某些模型可能返回不同的 key)
            if (responseJson.has("result")) {
                JsonObject result = responseJson.getAsJsonObject("result");
                if (result.has("text")) {
                    return result.get("text").getAsString();
                }
            }

            throw new IOException("无法解析 AI 响应结果: " + responseBody);
        }
    }
}
