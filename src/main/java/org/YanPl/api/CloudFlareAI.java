package org.YanPl.api;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.YanPl.FancyHelper;
import org.YanPl.model.AIResponse;
import org.YanPl.model.DialogueSession;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class CloudFlareAI {
    /**
     * 使用 Java 标准库 HttpClient 封装 CloudFlare AI HTTP 调用
     * 负责构建请求、解析响应，以及管理 HttpClient 的生命周期。
     */
    private static final String API_COMPLETIONS_URL = "https://api.cloudflare.com/client/v4/accounts/%s/ai/v1/chat/completions";
    private static final String ACCOUNTS_URL = "https://api.cloudflare.com/client/v4/accounts";
    private final FancyHelper plugin;
    private final HttpClient httpClient;
    private final Gson gson = new Gson();
    private String cachedAccountId = null;

    public CloudFlareAI(FancyHelper plugin) {
        this.plugin = plugin;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(60))
                .build();
    }

    public HttpClient getHttpClient() {
        return httpClient;
    }

    public void shutdown() {
        // Java 标准库的 HttpClient 不需要显式关闭
        // 它使用系统默认的 executor，会随 JVM 退出而终止
        plugin.getLogger().info("[CloudFlareAI] HTTP client shutdown completed (no-op for java.net.http.HttpClient).");
    }

    private String fetchAccountId() throws IOException {
        // 从 Cloudflare API 获取 Account ID 并缓存，依赖配置中的 cf_key
        if (cachedAccountId != null) return cachedAccountId;

        String cfKey = plugin.getConfigManager().getCloudflareCfKey();
        if (cfKey.isEmpty()) {
            throw new IOException("错误: 请先在配置文件中设置 cloudflare.cf_key。");
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ACCOUNTS_URL))
                    .header("Authorization", "Bearer " + cfKey)
                    .timeout(Duration.ofSeconds(60))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new IOException("获取 Account ID 失败: " + response.statusCode() + " " + response.body());
            }

            JsonObject resultJson = gson.fromJson(response.body(), JsonObject.class);

            if (resultJson.has("result") && resultJson.getAsJsonArray("result").size() > 0) {
                cachedAccountId = resultJson.getAsJsonArray("result").get(0).getAsJsonObject().get("id").getAsString();
                return cachedAccountId;
            } else {
                throw new IOException("未找到关联的 CloudFlare 账户，请检查 cf_key 权限。");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("获取 Account ID 被中断: " + e.getMessage(), e);
        }
    }

    public AIResponse chat(DialogueSession session, String systemPrompt) throws IOException {
        // 将会话历史与 systemPrompt 打包为 CloudFlare Responses API 所需的 JSON，发起 HTTP 请求并解析返回
        String cfKey = plugin.getConfigManager().getCloudflareCfKey();
        String model = plugin.getConfigManager().getCloudflareModel();

        if (cfKey == null || cfKey.isEmpty()) {
            return new AIResponse("错误: 请先在配置文件中设置 CloudFlare cf_key。", null);
        }

        if (model == null || model.isEmpty()) {
            model = "@cf/openai/gpt-oss-120b";
            plugin.getLogger().warning("[AI] 模型名称为空，已回退到默认值: " + model);
        }

        String accountId;
        try {
            accountId = fetchAccountId();
        } catch (IOException e) {
            plugin.getLogger().severe("[AI Error] Failed to fetch Account ID: " + e.getMessage());
            plugin.getCloudErrorReport().report(e);
            throw e;
        }

        String url = String.format(API_COMPLETIONS_URL, accountId);
        plugin.getLogger().info("[AI Request] URL: " + url);

        JsonArray messagesArray = new JsonArray();

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

        List<DialogueSession.Message> historyCopy = new ArrayList<>(session.getHistory());
        plugin.getLogger().info("[AI Request] Processing " + historyCopy.size() + " history messages");

        for (DialogueSession.Message msg : historyCopy) {
            String content = msg.getContent();
            String role = msg.getRole();

            if (content == null) {
                plugin.getLogger().warning("[AI Request] Skipping message with null content");
                continue;
            }
            if (role == null) {
                plugin.getLogger().warning("[AI Request] Skipping message with null role");
                continue;
            }

            content = content.trim();
            role = role.trim();

            if (content.isEmpty()) {
                plugin.getLogger().warning("[AI Request] Skipping message with empty content after trim");
                continue;
            }
            if (role.isEmpty()) {
                plugin.getLogger().warning("[AI Request] Skipping message with empty role after trim");
                continue;
            }

            if ("system".equalsIgnoreCase(role)) {
                plugin.getLogger().info("[AI Request] Skipping duplicate system message");
                continue;
            }

            JsonObject m = new JsonObject();
            m.addProperty("role", role);
            m.addProperty("content", content);
            messagesArray.add(m);
        }

        for (int i = 0; i < messagesArray.size(); i++) {
            JsonObject msg = messagesArray.get(i).getAsJsonObject();
            String role = msg.get("role").getAsString();
            String content = msg.get("content").getAsString();
            if (role == null || content == null) {
                plugin.getLogger().severe("[AI Request] Detected null in message array at index " + i);
                throw new IOException("Message validation failed: null in array");
            }
        }

        if (messagesArray.size() <= 1) {
            JsonObject m = new JsonObject();
            m.addProperty("role", "user");
            m.addProperty("content", "hello");
            messagesArray.add(m);
            plugin.getLogger().info("[AI Request] Added fallback user message");
        }

        JsonObject bodyJson = new JsonObject();
        bodyJson.addProperty("model", model);
        // 根据用户要求，gpt 系列必须使用 completions 接口并使用 messages 字段
        bodyJson.add("messages", messagesArray);

        if (model.contains("gpt-oss")) {
            // 根据 OpenAI 规范，对于推理模型（如 o1/gpt-oss），在 chat/completions 接口中
            // 应当使用 reasoning_effort 字段（字符串），而不是 Cloudflare 原生的 reasoning 对象
            bodyJson.addProperty("reasoning_effort", "medium");

            // 确保有足够的 token 输出思考过程
            bodyJson.addProperty("max_tokens", 4096);
        }

        String bodyString = gson.toJson(bodyJson);

        plugin.getLogger().info("[AI Request] Model: " + model);
        plugin.getLogger().info("[AI Request] Total messages in array: " + messagesArray.size());

        if (bodyString.contains("\"content\":null") || bodyString.contains("\"role\":null")) {
            plugin.getLogger().severe("[AI Error] CRITICAL: Payload contains null content or role!");
            plugin.getLogger().severe("[AI Error] Full payload: " + bodyString);
            throw new IOException("Payload validation failed: null content or role detected in JSON");
        }

        if (bodyString.matches(".*\"content\":\\s*\"\"\\s*[,}].*")) {
            plugin.getLogger().warning("[AI Request] Warning: Empty content string detected");
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + cfKey)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .timeout(Duration.ofSeconds(90))
                    .POST(HttpRequest.BodyPublishers.ofString(bodyString, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String responseBody = response.body();
            plugin.getLogger().info("[AI Response] Code: " + response.statusCode());

            if (response.statusCode() != 200) {
                plugin.getLogger().warning("[AI Error] Response Body: " + responseBody);

                // 如果是 400 (常见于 payload 错误) 或 500 (常见于推理模型参数不兼容)，尝试使用最简 payload 重试
                if ((response.statusCode() == 400 || response.statusCode() == 500) && responseBody != null) {
                    plugin.getLogger().warning("[AI] Detected error " + response.statusCode() + " from CF API, retrying with simplified payload...");

                    JsonArray simpleInput = new JsonArray();
                    JsonObject simpleSystem = new JsonObject();
                    simpleSystem.addProperty("role", "system");
                    simpleSystem.addProperty("content", "You are a helpful assistant.");
                    simpleInput.add(simpleSystem);

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
                    simpleBody.add("messages", simpleInput);

                    String simpleBodyString = gson.toJson(simpleBody);
                    plugin.getLogger().info("[AI Request] Retrying with simplified payload: " + simpleBodyString);

                    HttpRequest simpleRequest = HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .header("Authorization", "Bearer " + cfKey)
                            .header("Content-Type", "application/json; charset=utf-8")
                            .timeout(Duration.ofSeconds(90))
                            .POST(HttpRequest.BodyPublishers.ofString(simpleBodyString, StandardCharsets.UTF_8))
                            .build();

                    HttpResponse<String> simpleResp = httpClient.send(simpleRequest, HttpResponse.BodyHandlers.ofString());
                    String simpleRespBody = simpleResp.body();
                    plugin.getLogger().info("[AI Response - Retry] Code: " + simpleResp.statusCode());

                    if (simpleResp.statusCode() != 200) {
                        plugin.getLogger().warning("[AI Error - Retry] Response Body: " + simpleRespBody);
                        throw new IOException("AI 调用失败(重试): " + simpleResp.statusCode() + " - " + simpleRespBody);
                    }

                    JsonObject responseJson = gson.fromJson(simpleRespBody, JsonObject.class);
                    String textC = null;
                    String thoughtC = null;

                    // 1. 尝试解析 OpenAI 兼容格式
                    if (responseJson.has("choices") && responseJson.get("choices").isJsonArray()) {
                        JsonArray choices = responseJson.getAsJsonArray("choices");
                        if (choices.size() > 0) {
                            JsonObject choice = choices.get(0).getAsJsonObject();
                            if (choice.has("message")) {
                                JsonObject message = choice.getAsJsonObject("message");
                                if (message.has("content") && !message.get("content").isJsonNull()) {
                                    textC = message.get("content").getAsString();
                                }
                                if (message.has("reasoning_content") && !message.get("reasoning_content").isJsonNull()) {
                                    thoughtC = message.get("reasoning_content").getAsString();
                                }
                            }
                        }
                    }

                    // 2. 尝试解析 Cloudflare 原生 output 格式
                    if (textC == null && responseJson.has("output") && responseJson.get("output").isJsonArray()) {
                        JsonArray outputArray = responseJson.getAsJsonArray("output");
                        for (int i = 0; i < outputArray.size(); i++) {
                            JsonObject item = outputArray.get(i).getAsJsonObject();
                            if (item.has("type") && "message".equals(item.get("type").getAsString())) {
                                if (item.has("content") && item.get("content").isJsonArray()) {
                                    JsonArray contents = item.getAsJsonArray("content");
                                    for (int j = 0; j < contents.size(); j++) {
                                        JsonObject contentObj = contents.get(j).getAsJsonObject();
                                        String type = contentObj.has("type") ? contentObj.get("type").getAsString() : "";
                                        if ("output_text".equals(type)) {
                                            textC = contentObj.get("text").isJsonNull() ? null : contentObj.get("text").getAsString();
                                        } else if ("thought".equals(type) || "reasoning".equals(type)) {
                                            thoughtC = contentObj.get("text").isJsonNull() ? null : contentObj.get("text").getAsString();
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 3. 尝试解析 Cloudflare 原生 result 格式
                    if (textC == null && responseJson.has("result")) {
                        JsonObject result = responseJson.getAsJsonObject("result");
                        if (result.has("response")) textC = result.get("response").isJsonNull() ? null : result.get("response").getAsString();
                        else if (result.has("text")) textC = result.get("text").isJsonNull() ? null : result.get("text").getAsString();

                        if (thoughtC == null) {
                            if (result.has("reasoning")) thoughtC = result.get("reasoning").isJsonNull() ? null : result.get("reasoning").getAsString();
                            else if (result.has("thought")) thoughtC = result.get("thought").isJsonNull() ? null : result.get("thought").getAsString();
                        }
                    }

                    if (textC != null) {
                        return new AIResponse(textC, thoughtC);
                    }
                    throw new IOException("无法解析 AI 响应结果(重试): " + simpleRespBody);
                }

                throw new IOException("AI 调用失败: " + response.statusCode() + " - " + responseBody);
            }

            JsonObject responseJson = gson.fromJson(responseBody, JsonObject.class);
            String textContent = null;
            String thoughtContent = null;

            // 1. 尝试解析 OpenAI 兼容格式 (choices 数组)
            if (responseJson.has("choices") && responseJson.get("choices").isJsonArray()) {
                JsonArray choices = responseJson.getAsJsonArray("choices");
                if (choices.size() > 0) {
                    JsonObject choice = choices.get(0).getAsJsonObject();
                    if (choice.has("message")) {
                        JsonObject message = choice.getAsJsonObject("message");
                        if (message.has("content") && !message.get("content").isJsonNull()) {
                            textContent = message.get("content").getAsString();
                        }
                        // 某些模型在 reasoning_content 中返回思考过程
                        if (message.has("reasoning_content") && !message.get("reasoning_content").isJsonNull()) {
                            thoughtContent = message.get("reasoning_content").getAsString();
                        }
                    }
                }
            }

            // 2. 尝试解析 Cloudflare 原生 output 格式
            if (textContent == null && responseJson.has("output") && responseJson.get("output").isJsonArray()) {
                JsonArray outputArray = responseJson.getAsJsonArray("output");
                for (int i = 0; i < outputArray.size(); i++) {
                    JsonObject item = outputArray.get(i).getAsJsonObject();
                    if (item.has("type") && "message".equals(item.get("type").getAsString())) {
                        if (item.has("content") && item.get("content").isJsonArray()) {
                            JsonArray contents = item.getAsJsonArray("content");
                            for (int j = 0; j < contents.size(); j++) {
                                JsonObject contentObj = contents.get(j).getAsJsonObject();
                                String type = contentObj.has("type") ? contentObj.get("type").getAsString() : "";
                                if ("output_text".equals(type)) {
                                    textContent = contentObj.get("text").getAsString();
                                } else if ("thought".equals(type) || "reasoning".equals(type)) {
                                    thoughtContent = contentObj.get("text").getAsString();
                                }
                            }
                        }
                    }
                }
            }

            // 3. 尝试解析 Cloudflare 原生 result 格式
            if (textContent == null && responseJson.has("result")) {
                JsonObject result = responseJson.getAsJsonObject("result");
                if (result.has("response")) textContent = result.get("response").getAsString();
                else if (result.has("text")) textContent = result.get("text").getAsString();

                if (thoughtContent == null) {
                    if (result.has("reasoning")) thoughtContent = result.get("reasoning").getAsString();
                    else if (result.has("thought")) thoughtContent = result.get("thought").getAsString();
                }
            }

            if (textContent != null) {
                // 如果思考内容为空，但在正文中包含 <thought> 标签，我们不在这里处理，交给 CLIManager 处理
                // 但为了确保按钮能显示，我们记录一下日志
                if (thoughtContent != null) {
                    plugin.getLogger().info("[AI] Detected thought content in API field (length: " + thoughtContent.length() + ")");
                } else if (textContent.contains("<thought>")) {
                    plugin.getLogger().info("[AI] Detected thought tags inside text content");
                }
                return new AIResponse(textContent, thoughtContent);
            }

            throw new IOException("无法解析 AI 响应结果: " + responseBody);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("AI 调用被中断: " + e.getMessage(), e);
        }
    }
}