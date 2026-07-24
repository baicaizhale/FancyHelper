package org.YanPl.api;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.YanPl.FancyHelper;
import org.YanPl.manager.ConfigManager;
import org.YanPl.model.AIResponse;
import org.YanPl.model.DialogueSession;
import org.YanPl.model.ProviderConfig;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * API 路由层
 * 根据 ProviderConfig 决定走 FancyConsole 代理还是 BYOK 直连
 * BYOK 路径委托给原有的 LLMClient/TavilyAPI/MetasoAPI，fancy 路径直接发往 api.fancy.baicaizhale.top
 */
public class APIRouter {

    private final FancyHelper plugin;
    private final ConfigManager configManager;
    private final HttpClient httpClient;
    private final Gson gson = new Gson();

    public APIRouter(FancyHelper plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    // ============ 对话 ============

    /**
     * 路由聊天请求
     * @return AIResponse 对象（调用失败时自动返回错误 AIResponse）
     */
    public AIResponse chat(DialogueSession session, String systemPrompt) {
        ProviderConfig cfg = configManager.getProviderConfig();
        if (cfg.isAiFancy()) {
            return chatViaFancyConsole(session, systemPrompt, false);
        }
        // BYOK：直连
        try {
            return plugin.getLlmClient().chatDirect(session, systemPrompt);
        } catch (IOException e) {
            return new AIResponse(e.getMessage(), null, 0, 0, false);
        }
    }

    /**
     * 路由流式聊天请求
     */
    public String chatStreaming(DialogueSession session, String systemPrompt, StreamingHandler handler) throws IOException {
        ProviderConfig cfg = configManager.getProviderConfig();
        if (cfg.isAiFancy()) {
            return chatStreamingViaFancyConsole(session, systemPrompt, handler);
        }
        // BYOK：直连
        return plugin.getLlmClient().chatStreamingDirect(session, systemPrompt, handler);
    }

    /**
     * 路由简单对话（无历史记录）
     */
    public AIResponse chatSimple(String prompt) {
        ProviderConfig cfg = configManager.getProviderConfig();
        if (cfg.isAiFancy()) {
            return chatViaFancyConsoleSimple(prompt);
        }
        try {
            return plugin.getLlmClient().chatSimpleDirect(prompt);
        } catch (IOException e) {
            return new AIResponse(e.getMessage(), null, 0, 0, false);
        }
    }

    /**
     * 路由压缩模型对话
     */
    public String chatWithCompressionModel(String systemPrompt, String userPrompt) throws IOException {
        ProviderConfig cfg = configManager.getProviderConfig();
        if (cfg.isAiFancy()) {
            return chatViaFancyConsoleCompress(systemPrompt, userPrompt);
        }
        return plugin.getLlmClient().chatWithCompressionModelDirect(systemPrompt, userPrompt);
    }

    /**
     * 路由上下文压缩
     */
    public String compressContext(String context) throws IOException {
        ProviderConfig cfg = configManager.getProviderConfig();
        if (cfg.isAiFancy()) {
            return compressViaFancyConsole(context);
        }
        return plugin.getLlmClient().compressContextDirect(context);
    }

    /**
     * 路由标题生成
     */
    public String generateTitle(String firstMessage) throws IOException {
        ProviderConfig cfg = configManager.getProviderConfig();
        if (cfg.isAiFancy()) {
            return generateTitleViaFancyConsole(firstMessage);
        }
        return plugin.getLlmClient().generateTitleDirect(firstMessage);
    }

    // ============ 搜索 ============

    /**
     * 路由搜索请求
     */
    public String search(String query) {
        ProviderConfig cfg = configManager.getProviderConfig();
        if (cfg.isSearchFancy()) {
            return searchViaFancyConsole(query, cfg);
        }
        // BYOK 直连，保留现有 Metaso→Tavily fallback
        if (plugin.getMetasoAPI().isAvailable()) {
            return plugin.getMetasoAPI().search(query);
        } else if (configManager.isTavilyEnabled()) {
            return plugin.getTavilyAPI().search(query);
        }
        return "搜索服务不可用，请在配置文件中配置 Metaso API 或 Tavily API。";
    }

    // ============ 网页抓取 ============

    /**
     * 路由网页抓取
     */
    public String webFetch(String url) throws Exception {
        ProviderConfig cfg = configManager.getProviderConfig();
        if (cfg.isJinaFancy()) {
            return webFetchViaFancyConsole(url);
        }
        // none：回退到直连（不使用 Jina）
        return webFetchDirect(url);
    }

    // ============ FancyConsole 代理实现 ============

    private final String API_BASE = "https://api.fancy.baicaizhale.top";

    private String getApiUrl() {
        return API_BASE;
    }

    private String getApiKey() {
        return plugin.getRegistrationManager().getApiKey();
    }

    private String getServerId() {
        return plugin.getRegistrationManager().getServerId();
    }

    /**
     * 通过 FancyConsole 代理对话（非流式）
     */
    private AIResponse chatViaFancyConsole(DialogueSession session, String systemPrompt, boolean isCompression) {
        try {
            String apiUrl = getApiUrl() + "/v1/chat/completions";
            String apiKey = getApiKey();

            if (apiKey == null || apiKey.isEmpty()) {
                return new AIResponse("错误：FancyConsole API Key 未配置，请先注册。", null, 0, 0, false);
            }

            JsonObject requestBody = new JsonObject();
            requestBody.add("messages", plugin.getLlmClient().buildMessagesArray(session, systemPrompt));
            requestBody.addProperty("model", configManager.getFancyModel());
            requestBody.addProperty("max_tokens", 10000);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("X-Server-Id", getServerId())
                    .timeout(Duration.ofSeconds(configManager.getApiTimeoutSeconds()))
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(requestBody), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int statusCode = response.statusCode();
            String responseBody = response.body();

            if (statusCode == 429) {
                return new AIResponse("FancyConsole API 调用次数已超今日限额，请明天再试或升级。", null, 0, 0, false);
            }
            if (statusCode != 200) {
                String errorMsg = "FancyConsole API 请求失败 (HTTP " + statusCode + ")";
                try {
                    JsonObject err = gson.fromJson(responseBody, JsonObject.class);
                    if (err.has("error")) errorMsg = err.get("error").getAsString();
                } catch (Exception ignored) {}
                return new AIResponse(errorMsg, null, 0, 0, false);
            }

            return plugin.getLlmClient().getResponseParser().parseResponse(gson.fromJson(responseBody, JsonObject.class));

        } catch (Exception e) {
            plugin.getLogger().warning("[APIRouter] FancyConsole 对话请求失败: " + e.getMessage());
            return new AIResponse("FancyConsole 请求失败: " + e.getMessage(), null, 0, 0, false);
        }
    }

    /**
     * 通过 FancyConsole 代理对话（流式）
     */
    private String chatStreamingViaFancyConsole(DialogueSession session, String systemPrompt, StreamingHandler handler) throws IOException {
        String apiUrl = getApiUrl() + "/v1/chat/completions";
        String apiKey = getApiKey();

        if (apiKey == null || apiKey.isEmpty()) {
            throw new IOException("FancyConsole API Key 未配置。");
        }

        try {
            JsonObject requestBody = new JsonObject();
            requestBody.add("messages", plugin.getLlmClient().buildMessagesArray(session, systemPrompt));
            requestBody.addProperty("model", configManager.getFancyModel());
            requestBody.addProperty("stream", true);
            requestBody.addProperty("max_tokens", 10000);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("X-Server-Id", getServerId())
                    .header("Accept", "text/event-stream")
                    .timeout(Duration.ofSeconds(configManager.getApiTimeoutSeconds()))
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(requestBody), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<java.io.InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() == 429) {
                throw new IOException("今日 API 调用限额已用尽。");
            }
            if (response.statusCode() != 200) {
                // 读取错误响应体
                String errorBody;
                try (java.io.InputStream is = response.body()) {
                    errorBody = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                }
                throw new IOException("FancyConsole 请求失败 (HTTP " + response.statusCode() + "): " + errorBody);
            }

            return handler.processStream(response);

        } catch (IOException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("流式请求被中断", e);
        } catch (Exception e) {
            throw new IOException("FancyConsole 流式请求失败: " + e.getMessage());
        }
    }

    private AIResponse chatViaFancyConsoleSimple(String prompt) {
        try {
            String apiUrl = getApiUrl() + "/v1/chat/completions";
            String apiKey = getApiKey();
            if (apiKey == null || apiKey.isEmpty()) {
                return new AIResponse("FancyConsole API Key 未配置。", null, 0, 0, false);
            }

            JsonObject body = new JsonObject();
            JsonObject msg = new JsonObject();
            msg.addProperty("role", "user");
            msg.addProperty("content", prompt);
            body.add("messages", gson.toJsonTree(new JsonObject[]{msg}));
            body.addProperty("model", configManager.getFancyModel());

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return plugin.getLlmClient().getResponseParser().parseResponse(gson.fromJson(response.body(), JsonObject.class));
            }
            return new AIResponse("FancyConsole 请求失败 (" + response.statusCode() + ")", null, 0, 0, false);
        } catch (Exception e) {
            return new AIResponse("FancyConsole 错误: " + e.getMessage(), null, 0, 0, false);
        }
    }

    private String chatViaFancyConsoleCompress(String systemPrompt, String userPrompt) throws IOException {
        String apiUrl = getApiUrl() + "/v1/chat/completions";
        String apiKey = getApiKey();
        if (apiKey == null || apiKey.isEmpty()) {
            plugin.getLogger().warning("[压缩请求] API Key 为空");
            return "";
        }

        try {
            JsonObject body = new JsonObject();
            JsonArray messages = new JsonArray();

            // system 消息
            JsonObject sysMsg = new JsonObject();
            sysMsg.addProperty("role", "system");
            sysMsg.addProperty("content", systemPrompt);
            messages.add(sysMsg);

            // user 消息
            JsonObject usrMsg = new JsonObject();
            usrMsg.addProperty("role", "user");
            usrMsg.addProperty("content", userPrompt);
            messages.add(usrMsg);

            body.add("messages", messages);
            body.addProperty("model", configManager.getFancyCoModel());
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (plugin.getConfigManager().isDebug()) plugin.getLogger().info("[压缩请求] 响应状态码: " + response.statusCode());
            if (response.statusCode() == 200) {
                String raw = response.body();
                if (plugin.getConfigManager().isDebug()) plugin.getLogger().info("[压缩请求] 原始响应: " + raw);
                try {
                    AIResponse aiResp = plugin.getLlmClient().getResponseParser().parseResponse(gson.fromJson(raw, JsonObject.class));
                    String content = aiResp != null ? aiResp.getContent() : null;
                    if (plugin.getConfigManager().isDebug()) plugin.getLogger().info("[压缩请求] 提取内容: " + (content != null ? "'" + content + "'" : "null"));
                    return content != null ? content : "";
                } catch (Exception e) {
                    plugin.getLogger().warning("[压缩请求] 解析响应失败: " + e.getMessage() + " | 原始响应: " + raw);
                    return "";
                }
            }
            plugin.getLogger().warning("[压缩请求] 非200响应: " + response.statusCode() + " - " + response.body());
            return "";
        } catch (Exception e) {
            plugin.getLogger().warning("[压缩请求] 请求异常: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            throw new IOException("FancyConsole 压缩请求失败: " + e.getMessage());
        }
    }

    private String compressViaFancyConsole(String context) throws IOException {
        return chatViaFancyConsoleCompress("请压缩以下对话内容，保留关键信息：", context);
    }

    private String generateTitleViaFancyConsole(String firstMessage) throws IOException {
        String content = chatViaFancyConsoleCompress("[Core Constraints] (Violations cause parsing failures — follow strictly).Title labeling task. Do NOT think, reason, or echo. Do NOT use Markdown. Output ONLY: {\"title\": \"topic summary\"}. Describe the TOPIC of the message, do NOT repeat it. Same language.", "Label this: " + firstMessage);
        if (content == null || content.isEmpty()) return null;

        if (plugin.getConfigManager().isDebug()) {
            plugin.getLogger().info("[标题生成] FancyConsole 原始返回: " + content);
        }

        // 尝试从返回文本中提取 JSON title
        try {
            int jsonStart = content.lastIndexOf("{");
            int jsonEnd = content.lastIndexOf("}");
            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                JsonObject obj = gson.fromJson(content.substring(jsonStart, jsonEnd + 1), JsonObject.class);
                if (obj.has("title")) {
                    String title = obj.get("title").getAsString().trim();
                    if (title.length() > 30) title = title.substring(0, 30);
                    if (plugin.getConfigManager().isDebug()) {
                        plugin.getLogger().info("[标题生成] JSON 提取成功: " + title);
                    }
                    return title;
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[标题生成] JSON 解析失败: " + e.getMessage());
        }

        // 没找到 JSON title，直接作为纯文本标题
        String fallback = content.replaceAll("[\\n\\r]+", " ").trim();
        if (fallback.length() > 30) fallback = fallback.substring(0, 30);
        if (!fallback.isEmpty()) {
            plugin.getLogger().warning("[标题生成] 模型未按格式输出 JSON，使用纯文本回退: " + fallback);
            return fallback;
        }

        plugin.getLogger().warning("[标题生成] 模型输出为空");
        return null;
    }

    /**
     * 通过 FancyConsole 搜索
     */
    private String searchViaFancyConsole(String query, ProviderConfig cfg) {
        try {
            String apiUrl = getApiUrl() + "/v1/search";
            String apiKey = getApiKey();
            if (apiKey == null || apiKey.isEmpty()) {
                return "FancyConsole API Key 未配置。";
            }

            String provider = cfg.getSearchProvider() == ProviderConfig.SearchProvider.FANCY_METASO ? "metaso" : "tavily";

            String json = gson.toJson(Map.of("query", query, "provider", provider));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonObject obj = gson.fromJson(response.body(), JsonObject.class);
                if (obj.has("result")) return obj.get("result").getAsString();
                return "搜索结果解析失败";
            }
            if (response.statusCode() == 429) {
                return "搜索次数已超今日限额。";
            }
            return "搜索失败 (HTTP " + response.statusCode() + ")";

        } catch (Exception e) {
            plugin.getLogger().warning("[APIRouter] FancyConsole 搜索请求失败: " + e.getMessage());
            return "搜索请求失败: " + e.getMessage();
        }
    }

    /**
     * 通过 FancyConsole 网页抓取（Jina）
     */
    private String webFetchViaFancyConsole(String url) throws Exception {
        String apiUrl = getApiUrl() + "/v1/fetch";
        String apiKey = getApiKey();
        if (apiKey == null || apiKey.isEmpty()) {
            throw new Exception("FancyConsole API Key 未配置。");
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(
                            gson.toJson(Map.of("url", url)), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonObject obj = gson.fromJson(response.body(), JsonObject.class);
                if (obj.has("content")) return obj.get("content").getAsString();
                throw new Exception("FancyConsole 返回了无法解析的响应");
            }
            if (response.statusCode() == 429) {
                throw new Exception("网页抓取次数已超今日限额。");
            }
            throw new Exception("网页抓取失败 (HTTP " + response.statusCode() + ")");
        } catch (Exception e) {
            throw e;
        }
    }

    /**
     * 直连网页抓取（不使用 Jina，现有回退逻辑）
     */
    private String webFetchDirect(String url) throws Exception {
        // 使用 ToolExecutor 的现有 fetchWebPage 逻辑—但它是 protected
        // 这里提供简化实现：直接 HTTP GET + 截取
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw new IllegalArgumentException("URL 必须以 http:// 或 https:// 开头");
        }

        java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(15))
                .followRedirects(java.net.http.HttpClient.Redirect.NORMAL)
                .build();

        java.net.http.HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(java.time.Duration.ofSeconds(20))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .GET()
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() == 200) {
            String text = resp.body();
            // 简单清理 HTML 标签
            text = text.replaceAll("<[^>]+>", " ");
            text = text.replaceAll("\\s+", " ").trim();
            if (text.length() > 8000) text = text.substring(0, 8000) + "\n... (内容已截断)";
            return text;
        }
        throw new Exception("HTTP 请求失败，状态码: " + resp.statusCode());
    }

    public void shutdown() {
        // HttpClient 无需显式关闭
    }
}
