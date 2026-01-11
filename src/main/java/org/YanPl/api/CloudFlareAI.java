package org.YanPl.api;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import okhttp3.*;
import org.YanPl.MineAgent;
import org.YanPl.model.DialogueSession;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * CloudFlare Workers AI API 集成
 */
public class CloudFlareAI {
    private static final String API_RESPONSES_URL = "https://api.cloudflare.com/client/v4/accounts/%s/ai/v1/responses";
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
        try {
            // 关闭dispatcher中的所有待执行任务
            httpClient.dispatcher().executorService().shutdown();
            // 等待最多5秒让任务完成
            if (!httpClient.dispatcher().executorService().awaitTermination(5, TimeUnit.SECONDS)) {
                httpClient.dispatcher().executorService().shutdownNow();
                plugin.getLogger().warning("[CloudFlareAI] Executor service did not terminate in time, forcing shutdown.");
            }
            
            // 清空连接池
            httpClient.connectionPool().evictAll();
            
            // 关闭缓存
            if (httpClient.cache() != null) {
                try {
                    httpClient.cache().close();
                } catch (IOException ignored) {}
            }
            
            plugin.getLogger().info("[CloudFlareAI] HTTP client shutdown completed.");
        } catch (InterruptedException e) {
            httpClient.dispatcher().executorService().shutdownNow();
            plugin.getLogger().warning("[CloudFlareAI] Shutdown interrupted: " + e.getMessage());
            Thread.currentThread().interrupt();
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

        // 使用 /ai/v1/responses 接口，这是 gpt-oss-120b 推荐的接口
        String url = String.format(API_RESPONSES_URL, accountId);
        plugin.getLogger().info("[AI Request] URL: " + url);

        JsonArray messagesArray = new JsonArray();

        // 1. 添加系统提示词 (作为 system 角色消息加入 input 数组)
        String safeSystemPrompt = (systemPrompt != null && !systemPrompt.isEmpty()) ? systemPrompt : "You are a helpful assistant.";
        safeSystemPrompt = safeSystemPrompt.trim();
        
        if (safeSystemPrompt.isEmpty()) {
            safeSystemPrompt = "You are a helpful assistant.";
        }
        
        JsonObject systemMsg = new JsonObject();
        systemMsg.addProperty("role", "system");
        systemMsg.addProperty("content", safeSystemPrompt);
        messagesArray.add(systemMsg);
        plugin.getLogger().info("[AI Request] System prompt added (length: " + safeSystemPrompt.length() + ", non-empty: " + !safeSystemPrompt.isEmpty() + ")");

        // 2. 添加历史记录 (role: user/assistant)
        // 创建消息副本以避免并发修改问题
        List<DialogueSession.Message> historyCopy = new ArrayList<>(session.getHistory());
        plugin.getLogger().info("[AI Request] Processing " + historyCopy.size() + " history messages");
        
        for (DialogueSession.Message msg : historyCopy) {
            String content = msg.getContent();
            String role = msg.getRole();
            
            // 防御性检查
            if (content == null) {
                plugin.getLogger().warning("[AI Request] Skipping message with null content");
                continue;
            }
            if (role == null) {
                plugin.getLogger().warning("[AI Request] Skipping message with null role");
                continue;
            }
            
            // 清理空白
            content = content.trim();
            role = role.trim();
            
            // 检查清理后是否为空
            if (content.isEmpty()) {
                plugin.getLogger().warning("[AI Request] Skipping message with empty content after trim");
                continue;
            }
            if (role.isEmpty()) {
                plugin.getLogger().warning("[AI Request] Skipping message with empty role after trim");
                continue;
            }
            
            // 跳过 system 消息避免重复
            if ("system".equalsIgnoreCase(role)) {
                plugin.getLogger().info("[AI Request] Skipping duplicate system message");
                continue;
            }
            
            // 创建消息对象
            JsonObject m = new JsonObject();
            m.addProperty("role", role);
            m.addProperty("content", content);
            messagesArray.add(m);
            plugin.getLogger().info("[AI Request] Added message - Role: '" + role + "', Content length: " + content.length());
        }

        // 验证数组中没有null
        for (int i = 0; i < messagesArray.size(); i++) {
            JsonObject msg = messagesArray.get(i).getAsJsonObject();
            String role = msg.get("role").getAsString();
            String content = msg.get("content").getAsString();
            if (role == null || content == null) {
                plugin.getLogger().severe("[AI Request] Detected null in message array at index " + i);
                throw new IOException("Message validation failed: null in array");
            }
        }

        // 如果没有任何用户消息，至少添加一条
        if (messagesArray.size() <= 1) {
            JsonObject m = new JsonObject();
            m.addProperty("role", "user");
            m.addProperty("content", "hello");
            messagesArray.add(m);
            plugin.getLogger().info("[AI Request] Added fallback user message");
        }

        // 构建符合 /ai/v1/responses 接口要求的请求体
        JsonObject bodyJson = new JsonObject();
        bodyJson.addProperty("model", model);
        bodyJson.add("input", messagesArray);
        
        // 移除 instructions 字段，改用 system message
        // if (systemPrompt != null && !systemPrompt.isEmpty()) {
        //    bodyJson.addProperty("instructions", systemPrompt);
        // }
        
        // 如果是 gpt-oss 模型，添加推理参数
        if (model.contains("gpt-oss")) {
            JsonObject reasoning = new JsonObject();
            reasoning.addProperty("effort", "medium");
            bodyJson.add("reasoning", reasoning);
        }
        
        String bodyString = gson.toJson(bodyJson);

        plugin.getLogger().info("[AI Request] Model: " + model);
        plugin.getLogger().info("[AI Request] Total messages in array: " + messagesArray.size());
        
        // 完整的payload验证
        plugin.getLogger().info("[AI Request] Full Payload: " + bodyString);
        
        // 检查JSON中的null content
        if (bodyString.contains("\"content\":null") || bodyString.contains("\"role\":null")) {
            plugin.getLogger().severe("[AI Error] CRITICAL: Payload contains null content or role!");
            plugin.getLogger().severe("[AI Error] Full payload: " + bodyString);
            throw new IOException("Payload validation failed: null content or role detected in JSON");
        }
        
        // 检查是否有空的content值
        if (bodyString.matches(".*\"content\":\\s*\"\"\\s*[,}].*")) {
            plugin.getLogger().warning("[AI Request] Warning: Empty content string detected");
        }

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
                plugin.getLogger().warning("[AI Error] Response Body: " + responseBody);

                // 如果返回特定的 startswith NoneType 错误，尝试使用更简单的 payload 重试一次
                if (response.code() == 400 && responseBody != null && responseBody.contains("startswith")) {
                    plugin.getLogger().warning("[AI] Detected startswith NoneType error from CF API, retrying with simplified payload...");

                    // 构造简化的请求体：仅包含简短 system prompt 和最后一条 user 消息
                    JsonArray simpleInput = new JsonArray();
                    JsonObject simpleSystem = new JsonObject();
                    simpleSystem.addProperty("role", "system");
                    simpleSystem.addProperty("content", "You are a helpful assistant.");
                    simpleInput.add(simpleSystem);

                    // 尝试取会话中最后一条用户消息作为简化的 user 内容
                    String lastUser = null;
                    List<DialogueSession.Message> hist = new ArrayList<>(session.getHistory());
                    for (int i = hist.size() - 1; i >= 0; i--) {
                        DialogueSession.Message mm = hist.get(i);
                        if (mm != null && mm.getRole() != null && mm.getRole().equalsIgnoreCase("user") && mm.getContent() != null && !mm.getContent().trim().isEmpty()) {
                            lastUser = mm.getContent().trim();
                            break;
                        }
                    }
                    if (lastUser == null || lastUser.isEmpty()) lastUser = "Hello";

                    JsonObject simpleUser = new JsonObject();
                    simpleUser.addProperty("role", "user");
                    simpleUser.addProperty("content", lastUser);
                    simpleInput.add(simpleUser);

                    JsonObject simpleBody = new JsonObject();
                    simpleBody.addProperty("model", model);
                    simpleBody.add("input", simpleInput);

                    String simpleBodyString = gson.toJson(simpleBody);
                    plugin.getLogger().info("[AI Request] Retrying with simplified payload: " + simpleBodyString);

                    RequestBody simpleReqBody = RequestBody.create(simpleBodyString, MediaType.get("application/json; charset=utf-8"));
                    Request simpleRequest = new Request.Builder()
                            .url(url)
                            .addHeader("Authorization", "Bearer " + cfKey)
                            .post(simpleReqBody)
                            .build();

                    try (Response simpleResp = httpClient.newCall(simpleRequest).execute()) {
                        String simpleRespBody = simpleResp.body() != null ? simpleResp.body().string() : "";
                        plugin.getLogger().info("[AI Response - Retry] Code: " + simpleResp.code());
                        if (!simpleResp.isSuccessful()) {
                            plugin.getLogger().warning("[AI Error - Retry] Response Body: " + simpleRespBody);
                            throw new IOException("AI 调用失败(重试): " + simpleResp.code() + " - " + simpleRespBody);
                        }

                        // 解析重试的响应
                        JsonObject responseJson = gson.fromJson(simpleRespBody, JsonObject.class);
                        if (responseJson.has("output") && responseJson.get("output").isJsonArray()) {
                            JsonArray outputArray = responseJson.getAsJsonArray("output");
                            for (int i = 0; i < outputArray.size(); i++) {
                                JsonObject item = outputArray.get(i).getAsJsonObject();
                                if (item.has("type") && "message".equals(item.get("type").getAsString())) {
                                    if (item.has("content") && item.get("content").isJsonArray()) {
                                        JsonArray contents = item.getAsJsonArray("content");
                                        for (int j = 0; j < contents.size(); j++) {
                                            JsonObject contentObj = contents.get(j).getAsJsonObject();
                                            if (contentObj.has("type") && "output_text".equals(contentObj.get("type").getAsString())) {
                                                String text = contentObj.get("text").isJsonNull() ? null : contentObj.get("text").getAsString();
                                                if (text != null) return text;
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 备选处理
                        if (responseJson.has("result")) {
                            JsonObject result = responseJson.getAsJsonObject("result");
                            if (result.has("response")) {
                                String r = result.get("response").isJsonNull() ? null : result.get("response").getAsString();
                                if (r != null) return r;
                            }
                            if (result.has("text")) {
                                String t = result.get("text").isJsonNull() ? null : result.get("text").getAsString();
                                if (t != null) return t;
                            }
                        }
                        throw new IOException("无法解析 AI 响应结果(重试): " + simpleRespBody);
                    }
                }

                throw new IOException("AI 调用失败: " + response.code() + " - " + responseBody);
            }

            JsonObject responseJson = gson.fromJson(responseBody, JsonObject.class);
            
            // 1. 处理新的 /ai/v1/responses (Responses API) 格式
            // 格式: { "output": [ { "type": "message", "content": [ { "type": "output_text", "text": "..." } ] } ] }
            if (responseJson.has("output") && responseJson.get("output").isJsonArray()) {
                JsonArray outputArray = responseJson.getAsJsonArray("output");
                for (int i = 0; i < outputArray.size(); i++) {
                    JsonObject item = outputArray.get(i).getAsJsonObject();
                    if (item.has("type") && "message".equals(item.get("type").getAsString())) {
                        if (item.has("content") && item.get("content").isJsonArray()) {
                            JsonArray contents = item.getAsJsonArray("content");
                            for (int j = 0; j < contents.size(); j++) {
                                JsonObject contentObj = contents.get(j).getAsJsonObject();
                                if (contentObj.has("type") && "output_text".equals(contentObj.get("type").getAsString())) {
                                    String text = contentObj.get("text").getAsString();
                                    if (text != null) {
                                        return text;
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 2. 处理标准 /run 接口返回格式 (备选)
            if (responseJson.has("result")) {
                JsonObject result = responseJson.getAsJsonObject("result");
                if (result.has("response")) {
                    String responseText = result.get("response").getAsString();
                    if (responseText != null) {
                        return responseText;
                    }
                }
            }

            // 备选格式处理 (某些模型可能返回不同的 key)
            if (responseJson.has("result")) {
                JsonObject result = responseJson.getAsJsonObject("result");
                if (result.has("text")) {
                    String text = result.get("text").getAsString();
                    if (text != null) {
                        return text;
                    }
                }
            }

            throw new IOException("无法解析 AI 响应结果: " + responseBody);
        }
    }
}
