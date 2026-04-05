package org.YanPl.api;

import com.google.gson.Gson;
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
import java.util.function.BiConsumer;

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
    private BiConsumer<Integer, String> retryCallback = null;

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
     * 设置重试回调函数
     * @param callback 当发生重试时的回调，参数1为HTTP状态码，参数2为重试提示消息
     */
    public void setRetryCallback(BiConsumer<Integer, String> callback) {
        this.retryCallback = callback;
    }

    /**
     * 清除重试回调函数
     */
    public void clearRetryCallback() {
        this.retryCallback = null;
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
                    
                    plugin.getLogger().warning("[ReTry] 网络请求失败 (尝试 " + (i + 1) + "/" + maxRetries + "): " + errorMsg + "，正在重试...");
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
            // 1. 记录请求内容
            StringBuilder requestSb = new StringBuilder();
            try {
                JsonElement reqEl = gson.fromJson(requestBody, JsonElement.class);
                if (reqEl.isJsonObject()) {
                    JsonObject reqObj = reqEl.getAsJsonObject();
                    
                    // 记录模型信息
                    if (reqObj.has("model")) {
                        requestSb.append("Model: ").append(reqObj.get("model").getAsString()).append("\n\n");
                    }
                    
                    // 记录消息（只记录本次新增的消息，避免重复记录历史上下文）
                    if (reqObj.has("messages") && reqObj.get("messages").isJsonArray()) {
                        JsonArray messages = reqObj.get("messages").getAsJsonArray();
                        int lastLoggedCount = session.getLastLoggedMessageCount();
                        int newMessageCount = messages.size() - lastLoggedCount;
                        
                        if (newMessageCount > 0) {
                            requestSb.append("New Messages (").append(newMessageCount).append(" items):\n");
                            
                            // 只记录新增的消息（从 lastLoggedCount 开始）
                            for (int i = lastLoggedCount; i < messages.size(); i++) {
                                JsonObject msg = messages.get(i).getAsJsonObject();
                                if (msg.has("role") && msg.has("content")) {
                                    String role = msg.get("role").getAsString();
                                    String content = msg.get("content").getAsString();
                                    
                                    // 记录系统提示词（只记录一次）
                                    if ("system".equals(role)) {
                                        session.logSystemPrompt(content);
                                    } else {
                                        requestSb.append("\n[").append(role.toUpperCase()).append("]:\n");
                                        requestSb.append(content).append("\n");
                                    }
                                }
                            }
                            
                            // 更新已记录的消息数量
                            session.setLastLoggedMessageCount(messages.size());
                        } else {
                            requestSb.append("No new messages (all already logged)\n");
                        }
                    }
                    
                    // 记录其他参数
                    if (reqObj.has("max_tokens")) {
                        requestSb.append("\nMax Tokens: ").append(reqObj.get("max_tokens").getAsInt()).append("\n");
                    }
                    if (reqObj.has("temperature")) {
                        requestSb.append("Temperature: ").append(reqObj.get("temperature").getAsDouble()).append("\n");
                    }
                }
            } catch (Exception e) {
                requestSb.append("Raw Request:\n").append(requestBody).append("\n");
            }
            session.logAIRequest(requestSb.toString());
            
            // 2. 记录响应内容
            StringBuilder responseSb = new StringBuilder();
            try {
                JsonElement respEl = gson.fromJson(responseBody, JsonElement.class);
                if (respEl.isJsonObject()) {
                    JsonObject respObj = respEl.getAsJsonObject();
                    
                    // 记录响应内容
                    if (respObj.has("choices") && respObj.get("choices").isJsonArray()) {
                        JsonArray choices = respObj.get("choices").getAsJsonArray();
                        if (!choices.isEmpty()) {
                            JsonObject choice = choices.get(0).getAsJsonObject();
                            if (choice.has("message") && choice.get("message").isJsonObject()) {
                                JsonObject message = choice.get("message").getAsJsonObject();
                                if (message.has("content")) {
                                    String content = message.get("content").getAsString();
                                    responseSb.append(content).append("\n");
                                }
                            }
                            if (choice.has("finish_reason")) {
                                responseSb.append("\nFinish Reason: ").append(choice.get("finish_reason").getAsString()).append("\n");
                            }
                        }
                    }
                    
                    // 记录 token 使用情况
                    if (respObj.has("usage") && respObj.get("usage").isJsonObject()) {
                        JsonObject usage = respObj.get("usage").getAsJsonObject();
                        responseSb.append("\nToken Usage: ");
                        if (usage.has("prompt_tokens")) {
                            responseSb.append("prompt=").append(usage.get("prompt_tokens").getAsInt());
                        }
                        if (usage.has("completion_tokens")) {
                            responseSb.append(", completion=").append(usage.get("completion_tokens").getAsInt());
                        }
                        if (usage.has("total_tokens")) {
                            responseSb.append(", total=").append(usage.get("total_tokens").getAsInt());
                        }
                        responseSb.append("\n");
                    }
                }
            } catch (Exception e) {
                responseSb.append("Raw Response:\n").append(responseBody).append("\n");
            }
            session.logAIResponse(responseSb.toString());
            
        } catch (Exception e) {
            // 异常回退 - 使用原始方法记录
            session.appendLog("AI_RAW_DEBUG", "Request:\n" + requestBody + "\n\nResponse:\n" + responseBody);
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
            plugin.getLogger().severe("[AI 错误] 未配置 Cloudflare API Key");
            throw new IOException("§zFancyHelper§b§r §7> §fAPI调用发生未知错误，请查看控制台");
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
                plugin.getLogger().warning("[AI 错误] 获取 Account ID 失败: " + response.statusCode());
                plugin.getLogger().warning("[AI 错误] 响应体: " + response.body());
                throw new IOException("§zFancyHelper§b§r §7> §fAPI调用发生未知错误，请查看控制台");
            }

            JsonObject resultJson = gson.fromJson(response.body(), JsonObject.class);

            if (resultJson.has("result") && resultJson.getAsJsonArray("result").size() > 0) {
                cachedAccountId = resultJson.getAsJsonArray("result").get(0).getAsJsonObject().get("id").getAsString();
                return cachedAccountId;
            } else {
                plugin.getLogger().warning("[AI 错误] 未找到关联的 CloudFlare 账户");
                throw new IOException("§zFancyHelper§b§r §7> §fAPI调用发生未知错误，请查看控制台");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            plugin.getLogger().warning("[AI 错误] 获取 Account ID 被中断: " + e.getMessage());
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
            return new AIResponse("错误: 请先在配置文件中设置 openai.api_key。", null, 0, 0, false);
        }

        if (model == null || model.isEmpty()) {
            model = "gpt-4o";
            plugin.getLogger().warning("[AI] OpenAI 模型名称为空，已回退到默认值: " + model);
        }

        if (plugin.getConfigManager().isDebug()) {
            plugin.getLogger().info("[AI 请求] 使用 OpenAI 兼容 API: " + apiUrl);
            plugin.getLogger().info("[AI 请求] 模型: " + model);
        }

        // 如果 API 地址不包含 /chat/completions，尝试自动补全（针对 OpenAI 兼容 API）
        if (!apiUrl.contains("/chat/completions")) {
            // 针对阿里云通义千问的特殊处理
            if (apiUrl.contains("aliyuncs.com")) {
                if (apiUrl.endsWith("/")) {
                    apiUrl += "compatible-mode/v1/chat/completions";
                } else {
                    apiUrl += "/compatible-mode/v1/chat/completions";
                }
            } else {
                // 通用处理：在 URL 末尾添加 /chat/completions
                if (apiUrl.endsWith("/")) {
                    apiUrl += "chat/completions";
                } else {
                    apiUrl += "/chat/completions";
                }
            }
            if (plugin.getConfigManager().isDebug()) {
                plugin.getLogger().info("[AI 请求] 检测到 OpenAI 兼容 API，已自动补全路径：" + apiUrl);
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
            int statusCode = response.statusCode();
            if (plugin.getConfigManager().isDebug()) {
                plugin.getLogger().info("[AI 响应] 状态码: " + statusCode);
            }

            // 处理临时性错误（可重试），包括：429、500、502、503、504
            int maxRetries = 3;
            int retryCount = 0;
            while (isRetryableError(statusCode) && retryCount < maxRetries) {
                retryCount++;
                
                String errorType = getErrorTypeDescription(statusCode);
                long waitSeconds = extractRetryAfter(response);
                
                if (waitSeconds <= 0) {
                    // 使用指数退避策略：2秒、4秒、8秒
                    waitSeconds = (long) Math.pow(2, retryCount);
                }
                
                plugin.getLogger().warning("[AI 重试] 收到 " + statusCode + " " + errorType + "，等待 " + waitSeconds + " 秒后重试 (" + retryCount + "/" + maxRetries + ")...");
                
                // 触发重试回调，通知玩家正在重试
                if (retryCallback != null) {
                    if (statusCode == 429) {
                        // 429 错误使用特殊配色：黄色⁕ 白色
                        retryCallback.accept(statusCode, "请求速率达到上限，正在重试...");
                    } else {
                        // 其他错误使用普通配色
                        retryCallback.accept(statusCode, "服务器繁忙，正在重试...");
                    }
                }
                
                try {
                    Thread.sleep(waitSeconds * 1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    plugin.getLogger().warning("[AI 错误] OpenAI API 调用被中断: " + ie.getMessage());
                    throw new IOException("OpenAI API 调用被中断: " + ie.getMessage(), ie);
                }
                
                // 重新发送请求
                response = sendWithRetry(request);
                responseBody = response.body();
                statusCode = response.statusCode();
                
                if (plugin.getConfigManager().isDebug()) {
                    plugin.getLogger().info("[AI 响应] 重试后状态码: " + statusCode);
                }
            }

            // 调试日志：输出响应体前 500 个字符
            if (responseBody != null && plugin.getConfigManager().isDebug()) {
                String debugBody = responseBody.length() > 500 ? responseBody.substring(0, 500) + "..." : responseBody;
                plugin.getLogger().info("[AI 调试] 响应体内容: " + debugBody);
            }

            // 记录原始输入和输出到调试日志文件
            logInteraction(session, bodyString, responseBody);

            if (statusCode != 200) {
                String errorPrompt = getErrorPrompt(statusCode);
                String errorLogMsg = getErrorLogMessage(statusCode);
                String errorMsg;
                if (errorPrompt != null) {
                    errorMsg = errorPrompt;
                    plugin.getLogger().warning(errorLogMsg);
                } else {
                    errorMsg = "§zFancyHelper§b§r §7> §fAPI调用发生未知错误，请查看控制台";
                    plugin.getLogger().warning("状态码: " + statusCode);
                    plugin.getLogger().warning("响应体: " + responseBody);
                }
                throw new IOException(errorMsg);
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

            plugin.getLogger().warning("[AI 错误] 无法解析 OpenAI API 响应: " + responseBody);
            throw new IOException("§zFancyHelper§b§r §7> §fAPI调用发生未知错误，请查看控制台");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            plugin.getLogger().warning("[AI 错误] OpenAI API 调用被中断: " + e.getMessage());
            throw new IOException("OpenAI API 调用被中断: " + e.getMessage(), e);
        }
    }

    /**
     * 从 HTTP 响应头中提取 Retry-After 值
     * @param response HTTP 响应
     * @return 等待秒数，如果未找到则返回 0
     */
    private long extractRetryAfter(HttpResponse<String> response) {
        // 尝试获取 Retry-After 头
        String retryAfter = response.headers().firstValue("Retry-After").orElse(null);
        if (retryAfter != null && !retryAfter.isEmpty()) {
            try {
                // Retry-After 可以是秒数或 HTTP 日期
                return Long.parseLong(retryAfter);
            } catch (NumberFormatException e) {
                // 如果不是数字，可能是 HTTP 日期格式，这里简化处理
                plugin.getLogger().warning("[AI 速率限制] 无法解析 Retry-After 头: " + retryAfter);
            }
        }
        return 0;
    }

    /**
     * 判断 HTTP 状态码是否为可重试的临时性错误
     * @param statusCode HTTP 状态码
     * @return 是否可重试
     */
    private boolean isRetryableError(int statusCode) {
        // 429: 速率限制
        // 500: 服务器内部错误
        // 502: 网关错误
        // 503: 服务不可用
        // 504: 网关超时
        return statusCode == 429 || statusCode == 500 || statusCode == 502 || statusCode == 503 || statusCode == 504;
    }

    /**
     * 获取错误类型的描述
     * @param statusCode HTTP 状态码
     * @return 错误类型描述
     */
    private String getErrorTypeDescription(int statusCode) {
        switch (statusCode) {
            case 400:
                return "请求体有问题";
            case 401:
                return "API-key 不正确";
            case 402:
                return "余额不足";
            case 422:
                return "请求体有问题";
            case 429:
                return "速率限制";
            case 500:
                return "服务器内部错误";
            case 502:
                return "网关错误";
            case 503:
                return "服务不可用";
            case 504:
                return "网关超时";
            default:
                return "错误";
        }
    }

    /**
     * 获取错误的详细提示消息（带颜色，用于玩家显示）
     * @param statusCode HTTP 状态码
     * @return 错误提示消息
     */
    private String getErrorPrompt(int statusCode) {
        switch (statusCode) {
            case 400:
                return "§zFancyHelper§b§r §7> §f构造的请求体有问题，请向开发者报告此错误";
            case 401:
                return "§zFancyHelper§b§r §7> §fAPI-key填写不正确，请检查config.yml [https://blog.baicaizhale.top/post/whyusee2]";
            case 402:
                return "§zFancyHelper§b§r §7> §f开放平台显示您的余额不足，请检查您的开放平台余额";
            case 422:
                return "§zFancyHelper§b§r §7> §f构造的请求体有问题，请向开发者报告此错误";
            case 429:
                return null; // 429 错误会自动重试，不需要提示
            case 500:
                return "§zFancyHelper§b§r §7> §f开放平台出现问题，请等待恢复";
            case 503:
                return "§zFancyHelper§b§r §7> §f开放平台出现问题，请等待恢复";
            default:
                return null;
        }
    }

    /**
     * 获取错误的控制台日志消息（纯文本，无颜色）
     * @param statusCode HTTP 状态码
     * @return 错误日志消息
     */
    private String getErrorLogMessage(int statusCode) {
        switch (statusCode) {
            case 400:
                return "构造的请求体有问题，请向开发者报告此错误";
            case 401:
                return "API-key填写不正确，请检查config.yml [了解更多: https://blog.baicaizhale.top/post/whyusee2]";
            case 402:
                return "开放平台显示您的余额不足，请检查您的开放平台余额";
            case 422:
                return "构造的请求体有问题，请向开发者报告此错误";
            case 429:
                return "请求速率达到上限";
            case 500:
                return "开放平台出现问题，请等待恢复";
            case 503:
                return "开放平台出现问题，请等待恢复";
            default:
                return "API调用发生未知错误";
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
            return new AIResponse("错误: 请先在配置文件中设置 CloudFlare cf_key。", null, 0, 0, false);
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
            throw new IOException("§zFancyHelper§b§r §7> §fAPI调用发生未知错误，请查看控制台");
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

            plugin.getLogger().warning("[AI 错误] 无法解析响应: " + responseBody);
            throw new IOException("§zFancyHelper§b§r §7> §fAPI调用发生未知错误，请查看控制台");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            plugin.getLogger().warning("[AI 错误] 调用被中断: " + e.getMessage());
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
            plugin.getLogger().warning("[AI Error - Retry] 状态码: " + simpleResp.statusCode());
            plugin.getLogger().warning("[AI Error - Retry] 响应体: " + simpleRespBody);
            throw new IOException("§zFancyHelper§b§r §7> §fAPI调用发生未知错误，请查看控制台");
        }

        JsonObject responseJson = gson.fromJson(simpleRespBody, JsonObject.class);
        AIResponse retryResponse = responseParser.parseResponse(responseJson);
        if (retryResponse != null && retryResponse.getContent() != null) {
            return retryResponse;
        }
        plugin.getLogger().warning("[AI 错误] 无法解析重试响应: " + simpleRespBody);
        throw new IOException("§zFancyHelper§b§r §7> §fAPI调用发生未知错误，请查看控制台");
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

    /**
     * 简单的单轮对话方法，不使用会话历史
     * @param prompt 用户提示
     * @return AI响应
     */
    public AIResponse chatSimple(String prompt) throws IOException {
        DialogueSession tempSession = new DialogueSession();
        tempSession.addMessage("user", prompt);
        return chat(tempSession, "你是一个得力的助手。");
    }

    /**
     * 使用 co-model 进行简单的单轮对话
     * @param systemPrompt 系统提示
     * @param userPrompt 用户提示
     * @return AI响应内容
     * @throws IOException 当 API 调用失败时
     */
    public String chatWithCompressionModel(String systemPrompt, String userPrompt) throws IOException {
        String provider = plugin.getConfigManager().getCompressionModelProvider();
        
        if ("openai".equalsIgnoreCase(provider)) {
            return chatWithOpenAICompressionModel(systemPrompt, userPrompt);
        } else {
            return chatWithCloudFlareCompressionModel(systemPrompt, userPrompt);
        }
    }

    /**
     * 使用 CloudFlare co-model 进行对话
     */
    private String chatWithCloudFlareCompressionModel(String systemPrompt, String userPrompt) throws IOException {
        String cfKey = plugin.getConfigManager().getCloudflareCfKey();
        String model = plugin.getConfigManager().getCompressionCloudflareModel();
        String accountId = fetchAccountId();

        if (cfKey == null || cfKey.isEmpty()) {
            throw new IOException("未配置 CloudFlare API Key");
        }

        String url = String.format(API_COMPLETIONS_URL, accountId);

        // 构建消息数组 - 只使用 user message，避免模型输出思考过程
        JsonArray messagesArray = new JsonArray();
        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", userPrompt);
        messagesArray.add(userMsg);

        // 构建请求体
        JsonObject bodyJson = new JsonObject();
        bodyJson.addProperty("model", model);
        bodyJson.add("messages", messagesArray);
        bodyJson.addProperty("max_tokens", 500);
        bodyJson.addProperty("temperature", 0.3);

        String bodyString = gson.toJson(bodyJson);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + cfKey)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(bodyString, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = sendWithRetry(request);
            String responseBody = response.body();

            if (response.statusCode() != 200) {
                plugin.getLogger().warning("[co-model] CloudFlare API 错误: " + response.statusCode());
                throw new IOException("API调用失败: " + response.statusCode());
            }

            JsonObject responseJson = gson.fromJson(responseBody, JsonObject.class);
            AIResponse aiResponse = responseParser.parseResponse(responseJson);
            
            if (aiResponse != null && aiResponse.getContent() != null) {
                return aiResponse.getContent().trim();
            }

            throw new IOException("无法解析API响应");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("API调用被中断: " + e.getMessage(), e);
        }
    }

    /**
     * 使用 OpenAI 兼容 co-model 进行对话
     */
    private String chatWithOpenAICompressionModel(String systemPrompt, String userPrompt) throws IOException {
        String apiUrl = plugin.getConfigManager().getCompressionOpenAiApiUrl();
        String apiKey = plugin.getConfigManager().getCompressionOpenAiApiKey();
        String model = plugin.getConfigManager().getCompressionOpenAiModel();

        if (apiKey == null || apiKey.isEmpty()) {
            throw new IOException("未配置 OpenAI API Key");
        }

        // 自动补全 API 路径
        if (!apiUrl.contains("/chat/completions")) {
            if (apiUrl.endsWith("/")) {
                apiUrl += "chat/completions";
            } else {
                apiUrl += "/chat/completions";
            }
        }

        // 构建消息数组
        JsonArray messagesArray = new JsonArray();
        JsonObject systemMsg = new JsonObject();
        systemMsg.addProperty("role", "system");
        systemMsg.addProperty("content", systemPrompt);
        messagesArray.add(systemMsg);

        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", userPrompt);
        messagesArray.add(userMsg);

        // 构建请求体
        JsonObject bodyJson = new JsonObject();
        bodyJson.addProperty("model", model);
        bodyJson.add("messages", messagesArray);
        bodyJson.addProperty("max_tokens", 500);
        bodyJson.addProperty("temperature", 0.3);

        String bodyString = gson.toJson(bodyJson);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(bodyString, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = sendWithRetry(request);
            String responseBody = response.body();

            if (response.statusCode() != 200) {
                plugin.getLogger().warning("[co-model] OpenAI API 错误: " + response.statusCode());
                throw new IOException("API调用失败: " + response.statusCode());
            }

            JsonObject responseJson = gson.fromJson(responseBody, JsonObject.class);
            AIResponse aiResponse = responseParser.parseResponse(responseJson);
            
            if (aiResponse != null && aiResponse.getContent() != null) {
                return aiResponse.getContent().trim();
            }

            throw new IOException("无法解析API响应");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("API调用被中断: " + e.getMessage(), e);
        }
    }

    /**
     * 使用压缩模型对上下文进行智能压缩
     * @param context 需要压缩的上下文内容
     * @return 压缩后的摘要
     * @throws IOException 当 API 调用失败时
     */
    public String compressContext(String context) throws IOException {
        String provider = plugin.getConfigManager().getCompressionModelProvider();
        
        if ("openai".equalsIgnoreCase(provider)) {
            return compressWithOpenAI(context);
        } else {
            return compressWithCloudFlare(context);
        }
    }

    /**
     * 使用 CloudFlare 压缩模型进行上下文压缩
     */
    private String compressWithCloudFlare(String context) throws IOException {
        String cfKey = plugin.getConfigManager().getCloudflareCfKey();
        String model = plugin.getConfigManager().getCompressionCloudflareModel();
        String accountId = fetchAccountId();

        if (cfKey == null || cfKey.isEmpty()) {
            throw new IOException("未配置 CloudFlare API Key");
        }

        String url = String.format(API_COMPLETIONS_URL, accountId);

        // 构建压缩提示 - 使用单个 user prompt 避免模型输出思考过程
        String userPrompt = "请将以下对话历史压缩成简洁的摘要，保留关键信息和用户意图。直接输出摘要内容，不要有任何解释、分析或编号。摘要应该简明扼要，不超过200字。\n\n对话历史：\n" + context + "\n\n摘要：";

        // 构建消息数组 - 只使用 user message，避免模型输出思考过程
        JsonArray messagesArray = new JsonArray();
        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", userPrompt);
        messagesArray.add(userMsg);

        // 构建请求体
        JsonObject bodyJson = new JsonObject();
        bodyJson.addProperty("model", model);
        bodyJson.add("messages", messagesArray);
        bodyJson.addProperty("max_tokens", 300);
        bodyJson.addProperty("temperature", 0.3);

        String bodyString = gson.toJson(bodyJson);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + cfKey)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(bodyString, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = sendWithRetry(request);
            String responseBody = response.body();

            if (response.statusCode() != 200) {
                plugin.getLogger().warning("[压缩] CloudFlare API 错误: " + response.statusCode());
                throw new IOException("压缩失败: " + response.statusCode());
            }

            JsonObject responseJson = gson.fromJson(responseBody, JsonObject.class);
            AIResponse aiResponse = responseParser.parseResponse(responseJson);
            
            if (aiResponse != null && aiResponse.getContent() != null) {
                return aiResponse.getContent().trim();
            }

            throw new IOException("无法解析压缩响应");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("压缩被中断: " + e.getMessage(), e);
        }
    }

    /**
     * 使用 OpenAI 兼容 API 进行上下文压缩
     * 使用主模型的 API URL 和 API Key，仅使用副模型的模型名称
     */
    private String compressWithOpenAI(String context) throws IOException {
        String apiUrl = plugin.getConfigManager().getOpenAiApiUrl();
        String apiKey = plugin.getConfigManager().getOpenAiApiKey();
        String model = plugin.getConfigManager().getCompressionOpenAiModel();

        if (apiKey == null || apiKey.isEmpty()) {
            throw new IOException("未配置 OpenAI API Key");
        }

        // 自动补全 API 路径
        if (!apiUrl.contains("/chat/completions")) {
            if (apiUrl.endsWith("/")) {
                apiUrl += "chat/completions";
            } else {
                apiUrl += "/chat/completions";
            }
        }

        // 构建压缩提示 - 使用单个 user prompt 避免模型输出思考过程
        String userPrompt = "请将以下对话历史压缩成简洁的摘要，保留关键信息和用户意图。直接输出摘要内容，不要有任何解释、分析或编号。摘要应该简明扼要，不超过200字。\n\n对话历史：\n" + context + "\n\n摘要：";

        // 构建消息数组 - 只使用 user message，避免模型输出思考过程
        JsonArray messagesArray = new JsonArray();
        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", userPrompt);
        messagesArray.add(userMsg);

        // 构建请求体
        JsonObject bodyJson = new JsonObject();
        bodyJson.addProperty("model", model);
        bodyJson.add("messages", messagesArray);
        bodyJson.addProperty("max_tokens", 300);
        bodyJson.addProperty("temperature", 0.3);

        String bodyString = gson.toJson(bodyJson);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(bodyString, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = sendWithRetry(request);
            String responseBody = response.body();

            if (response.statusCode() != 200) {
                plugin.getLogger().warning("[压缩] OpenAI API 错误: " + response.statusCode());
                throw new IOException("压缩失败: " + response.statusCode());
            }

            JsonObject responseJson = gson.fromJson(responseBody, JsonObject.class);
            AIResponse aiResponse = responseParser.parseResponse(responseJson);
            
            if (aiResponse != null && aiResponse.getContent() != null) {
                return aiResponse.getContent().trim();
            }

            throw new IOException("无法解析压缩响应");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("压缩被中断: " + e.getMessage(), e);
        }
    }
}
