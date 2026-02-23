package org.YanPl.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
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

/**
 * CloudFlare AI API 封装类
 * 负责构建请求、解析响应，以及管理 HttpClient 的生命周期
 */
public class CloudFlareAI {
    private static final String API_COMPLETIONS_URL = "https://api.cloudflare.com/client/v4/accounts/%s/ai/v1/chat/completions";
    private static final String API_RESPONSES_URL = "https://api.cloudflare.com/client/v4/accounts/%s/ai/v1/responses";
    private static final String ACCOUNTS_URL = "https://api.cloudflare.com/client/v4/accounts";
    
    private final FancyHelper plugin;
    private final HttpClient httpClient;
    private final Gson gson = new Gson();
    private final ResponseParser responseParser = new ResponseParser();
    private String cachedAccountId = null;

    public CloudFlareAI(FancyHelper plugin) {
        this.plugin = plugin;
        int timeoutSeconds = plugin.getConfigManager().getApiTimeoutSeconds();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                .version(HttpClient.Version.HTTP_1_1) // 强制使用 HTTP/1.1 以避免某些 API (如阿里云) 的 HTTP/2 EOF 错误
                .build();
    }

    public HttpClient getHttpClient() {
        return httpClient;
    }

    /**
     * 发送 HTTP 请求并带有重试机制
     * 解决 java.io.IOException: HTTP/1.1 header parser received no bytes 等偶发性网络问题
     */
    private HttpResponse<String> sendWithRetry(HttpRequest request) throws IOException, InterruptedException {
        int maxRetries = 3;
        for (int i = 0; i < maxRetries; i++) {
            try {
                return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (IOException e) {
                String errorMsg = e.getMessage();
                // 常见的偶发性网络错误，值得重试
                if (errorMsg != null && (errorMsg.contains("header parser received no bytes") || 
                    errorMsg.contains("Connection reset") || 
                    errorMsg.contains("EOF reached"))) {
                    
                    plugin.getLogger().warning("[AI 请求] 网络请求失败 (尝试 " + (i + 1) + "/" + maxRetries + "): " + errorMsg + "，正在重试...");
                    if (i < maxRetries - 1) {
                        Thread.sleep(500 * (i + 1)); // 指数退避
                        continue;
                    }
                }
                throw e; // 达到最大重试次数或非偶发性错误，抛出异常
            }
        }
        // 理论上不会到达这里，除非 maxRetries <= 0
        throw new IOException("请求失败：超过最大重试次数");
    }

    public void shutdown() {
        // Java 标准库的 HttpClient 不需要显式关闭
        // 它使用系统默认的 executor，会随 JVM 退出而终止
        plugin.getLogger().info("[CloudFlareAI] HTTP 客户端已完成关闭（java.net.http.HttpClient 无需特殊操作）。");
    }

    /**
     * 记录交互日志到文件
     * 记录完整的请求和响应内容（排除 API Key，格式化 JSON）
     */
    private void logInteraction(DialogueSession session, String requestBody, String responseBody) {
        try {
            StringBuilder sb = new StringBuilder();
            Gson prettyGson = new GsonBuilder().setPrettyPrinting().create();
            
            // 1. 格式化 Request
            try {
                JsonElement reqEl = gson.fromJson(requestBody, JsonElement.class);
                sb.append("Request Payload:\n").append(prettyGson.toJson(reqEl)).append("\n\n");
            } catch (Exception e) {
                sb.append("Request Payload (Raw):\n").append(requestBody).append("\n\n");
            }

            // 2. 格式化 Response
            try {
                JsonElement respEl = gson.fromJson(responseBody, JsonElement.class);
                sb.append("Response Payload:\n").append(prettyGson.toJson(respEl)).append("\n");
            } catch (Exception e) {
                 sb.append("Response Payload (Raw):\n").append(responseBody).append("\n");
            }

            session.appendLog("AI_INTERACTION", sb.toString());
            
        } catch (Exception e) {
            // 异常回退
            session.appendLog("AI_RAW_DEBUG", "Request: " + requestBody + "\nResponse: " + responseBody);
        }
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
        if (plugin.getConfigManager().isDebug()) {
            plugin.getLogger().info("[AI 请求] 已添加 System Prompt (长度: " + safeSystemPrompt.length() + ")");
        }

        List<DialogueSession.Message> historyCopy = new ArrayList<>(session.getHistory());
        if (plugin.getConfigManager().isDebug()) {
            plugin.getLogger().info("[AI 请求] 正在处理 " + historyCopy.size() + " 条历史消息");
        }

        for (DialogueSession.Message msg : historyCopy) {
            String content = msg.getContent();
            String role = msg.getRole();

            if (content == null || role == null) {
                if (plugin.getConfigManager().isDebug()) {
                    plugin.getLogger().info("[AI 请求] 已跳过内容或角色为空的消息 (Role: " + role + ")");
                }
                continue;
            }

            content = content.trim();
            role = role.trim();

            if (content.isEmpty() || role.isEmpty()) {
                if (plugin.getConfigManager().isDebug()) {
                    plugin.getLogger().info("[AI 请求] 已跳过修整后为空的消息 (Role: " + role + ")");
                }
                continue;
            }

            if ("system".equalsIgnoreCase(role)) {
                if (plugin.getConfigManager().isDebug()) {
                    plugin.getLogger().info("[AI 请求] 跳过重复的 system 消息");
                }
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
            if (plugin.getConfigManager().isDebug()) {
                plugin.getLogger().info("[AI 请求] 已添加备用用户消息");
            }
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

            HttpResponse<String> response = sendWithRetry(request);

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

        if (plugin.getConfigManager().isDebug()) {
            plugin.getLogger().info("[AI 请求] 使用 OpenAI 兼容 API: " + apiUrl);
            plugin.getLogger().info("[AI 请求] 模型: " + model);
        }

        // 如果 API 地址包含 aliyuncs.com 但不包含 /chat/completions，尝试自动补全（针对阿里云通义千问）
        if (apiUrl.contains("aliyuncs.com") && !apiUrl.contains("/chat/completions")) {
            if (apiUrl.endsWith("/")) {
                apiUrl += "compatible-mode/v1/chat/completions";
            } else {
                apiUrl += "/compatible-mode/v1/chat/completions";
            }
            if (plugin.getConfigManager().isDebug()) {
                plugin.getLogger().info("[AI 请求] 检测到阿里云 API，已自动补全路径: " + apiUrl);
            }
        }

        // 构建消息数组
        JsonArray messagesArray = buildMessagesArray(session, systemPrompt);

        // 构建请求体
        JsonObject bodyJson = new JsonObject();
        bodyJson.addProperty("model", model);
        bodyJson.add("messages", messagesArray);
        bodyJson.addProperty("max_tokens", 4096);

        // 对于支持推理参数的模型（如 deepseek-reasoner、o1、qwen-max 等），添加推理参数
        if (model.contains("reasoner") || model.contains("o1") || model.contains("deepseek") || model.contains("qwen")) {
            bodyJson.addProperty("reasoning_effort", "medium");
        }

        String bodyString = gson.toJson(bodyJson);
        if (plugin.getConfigManager().isDebug()) {
            plugin.getLogger().info("[AI 请求] 消息数: " + messagesArray.size());
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .timeout(Duration.ofSeconds(plugin.getConfigManager().getApiTimeoutSeconds()))
                    .POST(HttpRequest.BodyPublishers.ofString(bodyString, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = sendWithRetry(request);
            String responseBody = response.body();
            if (plugin.getConfigManager().isDebug()) {
                plugin.getLogger().info("[AI 响应] 状态码: " + response.statusCode());
            }

            // 调试日志：输出响应体前 500 个字符
            if (responseBody != null && plugin.getConfigManager().isDebug()) {
                String debugBody = responseBody.length() > 500 ? responseBody.substring(0, 500) + "..." : responseBody;
                plugin.getLogger().info("[AI 调试] 响应体内容: " + debugBody);
            }

            // 记录原始输入和输出到调试日志文件
            logInteraction(session, bodyString, responseBody);

            if (response.statusCode() != 200) {
                plugin.getLogger().warning("[AI 错误] 响应体: " + responseBody);
                throw new IOException("OpenAI API 调用失败: " + response.statusCode() + " - " + responseBody);
            }

            JsonObject responseJson = gson.fromJson(responseBody, JsonObject.class);
            AIResponse aiResponse = responseParser.parseResponse(responseJson);
            
            if (aiResponse != null && aiResponse.getContent() != null) {
                String thoughtContent = aiResponse.getThought();
                if (thoughtContent != null && !thoughtContent.isEmpty()) {
                    if (plugin.getConfigManager().isDebug()) {
                        plugin.getLogger().info("[AI] 检测到思考内容 (长度: " + thoughtContent.length() + ")");
                    }
                }
                return aiResponse;
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
        if (plugin.getConfigManager().isDebug()) {
            plugin.getLogger().info("[AI 请求] URL: " + url);
        }

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

        if (plugin.getConfigManager().isDebug()) {
            plugin.getLogger().info("[AI 请求] 模型: " + model);
            plugin.getLogger().info("[AI 请求] 数组中的总消息数: " + messagesArray.size());
        }

        if (bodyString.contains("\"content\":null") || bodyString.contains("\"role\":null")) {
            plugin.getLogger().severe("[AI 错误] 严重：载荷中包含空的 content 或 role！");
            plugin.getLogger().severe("[AI 错误] 完整载荷: " + bodyString);
            throw new IOException("载荷验证失败: JSON 中检测到空的 content 或 role");
        }

        if (bodyString.matches(".*\"content\":\\s*\"\"\\s*[,}].*")) {
            if (plugin.getConfigManager().isDebug()) {
                plugin.getLogger().warning("[AI 请求] 警告：检测到空的内容字符串");
            }
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + cfKey)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .timeout(Duration.ofSeconds(plugin.getConfigManager().getApiTimeoutSeconds()))
                    .POST(HttpRequest.BodyPublishers.ofString(bodyString, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = sendWithRetry(request);
            String responseBody = response.body();
            if (plugin.getConfigManager().isDebug()) {
                plugin.getLogger().info("[AI 响应] 状态码: " + response.statusCode());
            }

            // 记录原始输入和输出到调试日志文件
            logInteraction(session, bodyString, responseBody);

            if (response.statusCode() != 200) {
                plugin.getLogger().warning("[AI 错误] 响应体: " + responseBody);

                // 如果是 400 (常见于 payload 错误) 或 500 (常见于推理模型参数不兼容)，尝试使用最简 payload 重试
                if ((response.statusCode() == 400 || response.statusCode() == 500) && responseBody != null) {
                    plugin.getLogger().warning("[AI] 检测到 CF API 错误 " + response.statusCode() + "，正在尝试使用简化载荷重试...");
                    return retryWithSimplifiedPayload(session, model, useResponsesApi, url, cfKey);
                }

                throw new IOException("AI 调用失败: " + response.statusCode() + " - " + responseBody);
            }

            JsonObject responseJson = gson.fromJson(responseBody, JsonObject.class);
            AIResponse aiResponse = responseParser.parseResponse(responseJson);
            
            if (aiResponse != null && aiResponse.getContent() != null) {
                String thoughtContent = aiResponse.getThought();
                if (thoughtContent != null) {
                    if (plugin.getConfigManager().isDebug()) {
                        plugin.getLogger().info("[AI] Detected thought content in API field (length: " + thoughtContent.length() + ")");
                    }
                } else if (aiResponse.getContent().contains("<thought>")) {
                    if (plugin.getConfigManager().isDebug()) {
                        plugin.getLogger().info("[AI] Detected thought tags inside text content");
                    }
                }
                return aiResponse;
            }

            throw new IOException("无法解析 AI 响应结果: " + responseBody);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("AI 调用被中断: " + e.getMessage(), e);
        }
    }

    /**
     * 使用简化载荷重试 API 请求
     * 当 API 返回 400 或 500 错误时，尝试使用最简化的请求体重试
     */
    private AIResponse retryWithSimplifiedPayload(DialogueSession session, String model, boolean useResponsesApi, 
                                                    String url, String cfKey) throws IOException, InterruptedException {
        // 构建简化的消息数组
        JsonArray simpleInput = new JsonArray();
        JsonObject simpleSystem = new JsonObject();
        simpleSystem.addProperty("role", "system");
        simpleSystem.addProperty("content", "你是一个得力的助手。");
        simpleInput.add(simpleSystem);

        // 获取最后一条用户消息
        String lastUser = getLastUserMessage(session);
        JsonObject simpleUser = new JsonObject();
        simpleUser.addProperty("role", "user");
        simpleUser.addProperty("content", lastUser);
        simpleInput.add(simpleUser);

        // 构建简化的请求体
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
        if (plugin.getConfigManager().isDebug()) {
            plugin.getLogger().info("[AI Request] Retrying with simplified payload: " + simpleBodyString);
        }

        HttpRequest simpleRequest = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + cfKey)
                .header("Content-Type", "application/json; charset=utf-8")
                .timeout(Duration.ofSeconds(plugin.getConfigManager().getApiTimeoutSeconds()))
                .POST(HttpRequest.BodyPublishers.ofString(simpleBodyString, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> simpleResp = sendWithRetry(simpleRequest);
        String simpleRespBody = simpleResp.body();
        if (plugin.getConfigManager().isDebug()) {
            plugin.getLogger().info("[AI Response - Retry] Code: " + simpleResp.statusCode());
        }

        // 记录重试的原始输入和输出到调试日志文件
        session.appendLog("SYSTEM", "Retrying with simplified payload...");
        logInteraction(session, simpleBodyString, simpleRespBody);

        if (simpleResp.statusCode() != 200) {
            plugin.getLogger().warning("[AI Error - Retry] Response Body: " + simpleRespBody);
            throw new IOException("AI 调用失败(重试): " + simpleResp.statusCode() + " - " + simpleRespBody);
        }

        JsonObject responseJson = gson.fromJson(simpleRespBody, JsonObject.class);
        AIResponse retryResponse = responseParser.parseResponse(responseJson);
        if (retryResponse != null && retryResponse.getContent() != null) {
            return retryResponse;
        }
        throw new IOException("无法解析 AI 响应结果(重试): " + simpleRespBody);
    }

    /**
     * 获取会话中最后一条用户消息
     */
    private String getLastUserMessage(DialogueSession session) {
        List<DialogueSession.Message> hist = new ArrayList<>(session.getHistory());
        for (int i = hist.size() - 1; i >= 0; i--) {
            DialogueSession.Message mm = hist.get(i);
            if (mm != null && mm.getRole() != null && mm.getRole().equalsIgnoreCase("user") 
                && mm.getContent() != null && !mm.getContent().trim().isEmpty()) {
                return mm.getContent().trim();
            }
        }
        return "Hello";
    }
}
