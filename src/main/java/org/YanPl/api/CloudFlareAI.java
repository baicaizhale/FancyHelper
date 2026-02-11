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
    private static final String API_RESPONSES_URL = "https://api.cloudflare.com/client/v4/accounts/%s/ai/v1/responses";
    private static final String ACCOUNTS_URL = "https://api.cloudflare.com/client/v4/accounts";
    private final FancyHelper plugin;
    private final HttpClient httpClient;
    private final Gson gson = new Gson();
    private String cachedAccountId = null;

    public CloudFlareAI(FancyHelper plugin) {
        this.plugin = plugin;
        int timeoutSeconds = plugin.getConfigManager().getApiTimeoutSeconds();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                .build();
    }

    public HttpClient getHttpClient() {
        return httpClient;
    }

    public void shutdown() {
        // Java 标准库的 HttpClient 不需要显式关闭
        // 它使用系统默认的 executor，会随 JVM 退出而终止
        plugin.getLogger().info("[CloudFlareAI] HTTP 客户端已完成关闭（java.net.http.HttpClient 无需特殊操作）。");
    }

    /**
     * 构建消息数组（OpenAI 和 CloudFlare API 通用）
     */
    private JsonArray buildMessagesArray(DialogueSession session, String systemPrompt) {
        JsonArray messagesArray = new JsonArray();

        String safeSystemPrompt = (systemPrompt != null && !systemPrompt.isEmpty()) ? systemPrompt : "你是一个得力的助手。";
        safeSystemPrompt = safeSystemPrompt.trim();

        if (safeSystemPrompt.isEmpty()) {
            safeSystemPrompt = "你是一个得力的助手。";
        }

        JsonObject systemMsg = new JsonObject();
        systemMsg.addProperty("role", "system");
        systemMsg.addProperty("content", safeSystemPrompt);
        messagesArray.add(systemMsg);
        plugin.getLogger().info("[AI 请求] 已添加 System Prompt (长度: " + safeSystemPrompt.length() + ")");

        List<DialogueSession.Message> historyCopy = new ArrayList<>(session.getHistory());
        plugin.getLogger().info("[AI 请求] 正在处理 " + historyCopy.size() + " 条历史消息");

        for (DialogueSession.Message msg : historyCopy) {
            String content = msg.getContent();
            String role = msg.getRole();

            if (content == null || role == null) {
                plugin.getLogger().warning("[AI 请求] 跳过内容或角色为空的消息");
                continue;
            }

            content = content.trim();
            role = role.trim();

            if (content.isEmpty() || role.isEmpty()) {
                plugin.getLogger().warning("[AI 请求] 跳过修整后内容或角色为空的消息");
                continue;
            }

            if ("system".equalsIgnoreCase(role)) {
                plugin.getLogger().info("[AI 请求] 跳过重复的 system 消息");
                continue;
            }

            JsonObject m = new JsonObject();
            m.addProperty("role", role);
            m.addProperty("content", content);
            messagesArray.add(m);
        }

        // 验证消息数组
        for (int i = 0; i < messagesArray.size(); i++) {
            JsonObject msg = messagesArray.get(i).getAsJsonObject();
            String role = msg.get("role").getAsString();
            String content = msg.get("content").getAsString();
            if (role == null || content == null) {
                plugin.getLogger().severe("[AI 请求] 在消息数组索引 " + i + " 处检测到空值");
                throw new IllegalArgumentException("消息验证失败: 数组中存在空值");
            }
        }

        // 如果消息数量过少，添加备用用户消息
        if (messagesArray.size() <= 1) {
            JsonObject m = new JsonObject();
            m.addProperty("role", "user");
            m.addProperty("content", "hello");
            messagesArray.add(m);
            plugin.getLogger().info("[AI 请求] 已添加备用用户消息");
        }

        return messagesArray;
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
                    .timeout(Duration.ofSeconds(plugin.getConfigManager().getApiTimeoutSeconds()))
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
        // 检测是否启用 OpenAI 模式
        if (plugin.getConfigManager().isOpenAiEnabled()) {
            return chatWithOpenAI(session, systemPrompt);
        }
        // 否则使用 CloudFlare Workers AI
        return chatWithCloudFlare(session, systemPrompt);
    }

    /**
     * 使用 OpenAI 兼容 API 进行对话
     */
    private AIResponse chatWithOpenAI(DialogueSession session, String systemPrompt) throws IOException {
        String apiUrl = plugin.getConfigManager().getOpenAiApiUrl();
        String apiKey = plugin.getConfigManager().getOpenAiApiKey();
        String model = plugin.getConfigManager().getOpenAiModel();

        if (apiKey == null || apiKey.isEmpty()) {
            return new AIResponse("错误: 请先在配置文件中设置 openai.api_key。", null);
        }

        if (model == null || model.isEmpty()) {
            model = "gpt-4o";
            plugin.getLogger().warning("[AI] OpenAI 模型名称为空，已回退到默认值: " + model);
        }

        plugin.getLogger().info("[AI 请求] 使用 OpenAI 兼容 API: " + apiUrl);
        plugin.getLogger().info("[AI 请求] 模型: " + model);

        // 构建消息数组
        JsonArray messagesArray = buildMessagesArray(session, systemPrompt);

        // 构建请求体
        JsonObject bodyJson = new JsonObject();
        bodyJson.addProperty("model", model);
        bodyJson.add("messages", messagesArray);
        bodyJson.addProperty("max_tokens", 4096);

        // 对于支持推理参数的模型（如 deepseek-reasoner、o1 等），添加推理参数
        if (model.contains("reasoner") || model.contains("o1") || model.contains("deepseek-reasoner")) {
            bodyJson.addProperty("reasoning_effort", "medium");
        }

        String bodyString = gson.toJson(bodyJson);
        plugin.getLogger().info("[AI 请求] 消息数: " + messagesArray.size());

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .timeout(Duration.ofSeconds(plugin.getConfigManager().getApiTimeoutSeconds()))
                    .POST(HttpRequest.BodyPublishers.ofString(bodyString, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String responseBody = response.body();
            plugin.getLogger().info("[AI 响应] 状态码: " + response.statusCode());

            if (response.statusCode() != 200) {
                plugin.getLogger().warning("[AI 错误] 响应体: " + responseBody);
                throw new IOException("OpenAI API 调用失败: " + response.statusCode() + " - " + responseBody);
            }

            JsonObject responseJson = gson.fromJson(responseBody, JsonObject.class);
            String textContent = null;
            String thoughtContent = null;

            // 解析 OpenAI 标准格式 (choices 数组)
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

            if (textContent != null) {
                if (thoughtContent != null) {
                    plugin.getLogger().info("[AI] 检测到思考内容 (长度: " + thoughtContent.length() + ")");
                }
                return new AIResponse(textContent, thoughtContent);
            }

            throw new IOException("无法解析 OpenAI API 响应结果: " + responseBody);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("OpenAI API 调用被中断: " + e.getMessage(), e);
        }
    }

    /**
     * 使用 CloudFlare Workers AI 进行对话
     */
    private AIResponse chatWithCloudFlare(DialogueSession session, String systemPrompt) throws IOException {
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
            plugin.getLogger().severe("[AI 错误] 获取 Account ID 失败: " + e.getMessage());
            plugin.getCloudErrorReport().report(e);
            throw e;
        }

        boolean useResponsesApi = model.contains("gpt-oss");
        String url = String.format(useResponsesApi ? API_RESPONSES_URL : API_COMPLETIONS_URL, accountId);
        plugin.getLogger().info("[AI 请求] URL: " + url);

        // 使用公共方法构建消息数组
        JsonArray messagesArray = buildMessagesArray(session, systemPrompt);

        JsonObject bodyJson = new JsonObject();
        bodyJson.addProperty("model", model);
        bodyJson.addProperty("max_tokens", 4096);

        if (useResponsesApi) {
            bodyJson.add("input", messagesArray);
            JsonObject reasoning = new JsonObject();
            reasoning.addProperty("effort", "medium");
            reasoning.addProperty("summary", "detailed");
            bodyJson.add("reasoning", reasoning);
        } else {
            bodyJson.add("messages", messagesArray);
            if (model.contains("gpt") || model.contains("o1") || model.contains("deepseek-reasoner")) {
                bodyJson.addProperty("reasoning_effort", "medium");
            }
        }

        String bodyString = gson.toJson(bodyJson);

        plugin.getLogger().info("[AI 请求] 模型: " + model);
        plugin.getLogger().info("[AI 请求] 数组中的总消息数: " + messagesArray.size());

        if (bodyString.contains("\"content\":null") || bodyString.contains("\"role\":null")) {
            plugin.getLogger().severe("[AI 错误] 严重：载荷中包含空的 content 或 role！");
            plugin.getLogger().severe("[AI 错误] 完整载荷: " + bodyString);
            throw new IOException("载荷验证失败: JSON 中检测到空的 content 或 role");
        }

        if (bodyString.matches(".*\"content\":\\s*\"\"\\s*[,}].*")) {
            plugin.getLogger().warning("[AI 请求] 警告：检测到空的内容字符串");
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + cfKey)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .timeout(Duration.ofSeconds(plugin.getConfigManager().getApiTimeoutSeconds()))
                    .POST(HttpRequest.BodyPublishers.ofString(bodyString, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String responseBody = response.body();
            plugin.getLogger().info("[AI 响应] 状态码: " + response.statusCode());

            if (response.statusCode() != 200) {
                plugin.getLogger().warning("[AI 错误] 响应体: " + responseBody);

                // 如果是 400 (常见于 payload 错误) 或 500 (常见于推理模型参数不兼容)，尝试使用最简 payload 重试
                if ((response.statusCode() == 400 || response.statusCode() == 500) && responseBody != null) {
                    plugin.getLogger().warning("[AI] 检测到 CF API 错误 " + response.statusCode() + "，正在尝试使用简化载荷重试...");

                    JsonArray simpleInput = new JsonArray();
                    JsonObject simpleSystem = new JsonObject();
                    simpleSystem.addProperty("role", "system");
                    simpleSystem.addProperty("content", "你是一个得力的助手。");
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
                    simpleBody.addProperty("max_tokens", 4096);

                    if (useResponsesApi) {
                        simpleBody.add("input", simpleInput);
                        JsonObject reasoning = new JsonObject();
                        reasoning.addProperty("effort", "medium");
                        reasoning.addProperty("summary", "detailed");
                        simpleBody.add("reasoning", reasoning);
                    } else {
                        simpleBody.add("messages", simpleInput);
                        if (model.contains("gpt") || model.contains("o1") || model.contains("deepseek-reasoner")) {
                            simpleBody.addProperty("reasoning_effort", "medium");
                        }
                    }

                    String simpleBodyString = gson.toJson(simpleBody);
                    plugin.getLogger().info("[AI Request] Retrying with simplified payload: " + simpleBodyString);

                    HttpRequest simpleRequest = HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .header("Authorization", "Bearer " + cfKey)
                            .header("Content-Type", "application/json; charset=utf-8")
                            .timeout(Duration.ofSeconds(plugin.getConfigManager().getApiTimeoutSeconds()))
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
                    if (responseJson.has("output") && responseJson.get("output").isJsonArray()) {
                        JsonArray outputArray = responseJson.getAsJsonArray("output");
                        for (int i = 0; i < outputArray.size(); i++) {
                            JsonObject item = outputArray.get(i).getAsJsonObject();
                            String itemType = item.has("type") ? item.get("type").getAsString() : "";
                            if ("message".equals(itemType)) {
                                if (item.has("content") && item.get("content").isJsonArray()) {
                                    JsonArray contents = item.getAsJsonArray("content");
                                    for (int j = 0; j < contents.size(); j++) {
                                        JsonObject contentObj = contents.get(j).getAsJsonObject();
                                        String type = contentObj.has("type") ? contentObj.get("type").getAsString() : "";
                                        if ("output_text".equals(type) && textC == null) {
                                            textC = contentObj.get("text").isJsonNull() ? null : contentObj.get("text").getAsString();
                                        } else if (("thought".equals(type) || "reasoning".equals(type)) && thoughtC == null) {
                                            thoughtC = contentObj.get("text").isJsonNull() ? null : contentObj.get("text").getAsString();
                                        }
                                    }
                                }
                            } else if ("reasoning".equals(itemType) && thoughtC == null) {
                                if (item.has("summary")) {
                                    if (item.get("summary").isJsonArray()) {
                                        JsonArray summaryArray = item.getAsJsonArray("summary");
                                        StringBuilder sb = new StringBuilder();
                                        for (int j = 0; j < summaryArray.size(); j++) {
                                            JsonObject summaryObj = summaryArray.get(j).getAsJsonObject();
                                            if (summaryObj.has("text") && !summaryObj.get("text").isJsonNull()) {
                                                if (sb.length() > 0) sb.append("\n");
                                                sb.append(summaryObj.get("text").getAsString());
                                            }
                                        }
                                        if (sb.length() > 0) thoughtC = sb.toString();
                                    } else if (item.get("summary").isJsonPrimitive()) {
                                        thoughtC = item.get("summary").getAsString();
                                    }
                                }
                                if (thoughtC == null && item.has("content") && item.get("content").isJsonArray()) {
                                    JsonArray contents = item.getAsJsonArray("content");
                                    StringBuilder sb = new StringBuilder();
                                    for (int j = 0; j < contents.size(); j++) {
                                        JsonObject contentObj = contents.get(j).getAsJsonObject();
                                        String type = contentObj.has("type") ? contentObj.get("type").getAsString() : "";
                                        if ("reasoning_text".equals(type) && contentObj.has("text") && !contentObj.get("text").isJsonNull()) {
                                            if (sb.length() > 0) sb.append("\n");
                                            sb.append(contentObj.get("text").getAsString());
                                        }
                                    }
                                    if (sb.length() > 0) thoughtC = sb.toString();
                                }
                            }
                        }
                    }

                    // 3. 尝试解析 Cloudflare 原生 result 格式
                    if (responseJson.has("result")) {
                        JsonObject result = responseJson.getAsJsonObject("result");
                        if (textC == null) {
                            if (result.has("response")) textC = result.get("response").isJsonNull() ? null : result.get("response").getAsString();
                            else if (result.has("text")) textC = result.get("text").isJsonNull() ? null : result.get("text").getAsString();
                        }

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
            if (responseJson.has("output") && responseJson.get("output").isJsonArray()) {
                JsonArray outputArray = responseJson.getAsJsonArray("output");
                for (int i = 0; i < outputArray.size(); i++) {
                    JsonObject item = outputArray.get(i).getAsJsonObject();
                    String itemType = item.has("type") ? item.get("type").getAsString() : "";
                    if ("message".equals(itemType)) {
                        if (item.has("content") && item.get("content").isJsonArray()) {
                            JsonArray contents = item.getAsJsonArray("content");
                            for (int j = 0; j < contents.size(); j++) {
                                JsonObject contentObj = contents.get(j).getAsJsonObject();
                                String type = contentObj.has("type") ? contentObj.get("type").getAsString() : "";
                                if ("output_text".equals(type) && textContent == null) {
                                    textContent = contentObj.get("text").getAsString();
                                } else if (("thought".equals(type) || "reasoning".equals(type)) && thoughtContent == null) {
                                    thoughtContent = contentObj.get("text").getAsString();
                                }
                            }
                        }
                    } else if ("reasoning".equals(itemType) && thoughtContent == null) {
                        if (item.has("summary")) {
                            if (item.get("summary").isJsonArray()) {
                                JsonArray summaryArray = item.getAsJsonArray("summary");
                                StringBuilder sb = new StringBuilder();
                                for (int j = 0; j < summaryArray.size(); j++) {
                                    JsonObject summaryObj = summaryArray.get(j).getAsJsonObject();
                                    if (summaryObj.has("text") && !summaryObj.get("text").isJsonNull()) {
                                        if (sb.length() > 0) sb.append("\n");
                                        sb.append(summaryObj.get("text").getAsString());
                                    }
                                }
                                if (sb.length() > 0) thoughtContent = sb.toString();
                            } else if (item.get("summary").isJsonPrimitive()) {
                                thoughtContent = item.get("summary").getAsString();
                            }
                        }
                        if (thoughtContent == null && item.has("content") && item.get("content").isJsonArray()) {
                            JsonArray contents = item.getAsJsonArray("content");
                            StringBuilder sb = new StringBuilder();
                            for (int j = 0; j < contents.size(); j++) {
                                JsonObject contentObj = contents.get(j).getAsJsonObject();
                                String type = contentObj.has("type") ? contentObj.get("type").getAsString() : "";
                                if ("reasoning_text".equals(type) && contentObj.has("text") && !contentObj.get("text").isJsonNull()) {
                                    if (sb.length() > 0) sb.append("\n");
                                    sb.append(contentObj.get("text").getAsString());
                                }
                            }
                            if (sb.length() > 0) thoughtContent = sb.toString();
                        }
                    }
                }
            }

            // 3. 尝试解析 Cloudflare 原生 result 格式
            if (responseJson.has("result")) {
                JsonObject result = responseJson.getAsJsonObject("result");
                if (textContent == null) {
                    if (result.has("response")) textContent = result.get("response").getAsString();
                    else if (result.has("text")) textContent = result.get("text").getAsString();
                }

                if (thoughtContent == null) {
                    if (result.has("reasoning")) thoughtContent = result.get("reasoning").getAsString();
                    else if (result.has("thought")) thoughtContent = result.get("thought").getAsString();
                }
            }

            if (textContent != null) {
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
