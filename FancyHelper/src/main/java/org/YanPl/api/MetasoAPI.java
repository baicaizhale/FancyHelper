package org.YanPl.api;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.YanPl.FancyHelper;
import org.YanPl.manager.ConfigManager;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Metaso AI 搜索 API 封装类
 * 秘塔 AI 搜索，支持联网搜索能力
 * 
 * @see <a href="https://metaso.cn">秘塔 AI 搜索</a>
 */
public class MetasoAPI {
    
    private final FancyHelper plugin;
    private final ConfigManager configManager;
    private final HttpClient httpClient;
    private final Gson gson;
    
    // Metaso API 端点
    private static final String METASO_API_URL = "https://metaso.cn/api/v1/chat/completions";
    
    public MetasoAPI(FancyHelper plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.gson = new Gson();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .version(HttpClient.Version.HTTP_1_1) // 强制使用 HTTP/1.1 避免兼容性问题
                .build();
    }
    
    /**
     * 检查 Metaso 是否已启用并正确配置
     * @return 是否可用
     */
    public boolean isAvailable() {
        return configManager.isMetasoEnabled() && 
               configManager.getMetasoApiToken() != null && 
               !configManager.getMetasoApiToken().isEmpty() &&
               !configManager.getMetasoApiToken().equals("your-metaso-api-token");
    }
    
    /**
     * 执行 Metaso AI 搜索
     * 
     * @param query 搜索查询
     * @return 格式化的搜索结果字符串
     */
    public String search(String query) {
        if (!configManager.isMetasoEnabled()) {
            return "Metaso AI 搜索未启用。请在 config.yml 中配置 metaso.enabled = true";
        }
        
        String apiToken = configManager.getMetasoApiToken();
        if (apiToken == null || apiToken.isEmpty() || apiToken.equals("your-metaso-api-token")) {
            return "Metaso API 令牌未配置。请在 config.yml 中设置 metaso.api_token";
        }
        
        String model = configManager.getMetasoModel();
        boolean conciseSnippet = configManager.isMetasoConciseSnippet();
        
        try {
            // 构建消息数组
            JsonArray messages = new JsonArray();
            JsonObject userMessage = new JsonObject();
            userMessage.addProperty("role", "user");
            userMessage.addProperty("content", query);
            messages.add(userMessage);
            
            // 构建请求体
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("model", model);
            requestBody.addProperty("stream", false); // 不使用流式响应，便于解析
            requestBody.addProperty("conciseSnippet", conciseSnippet);
            requestBody.add("messages", messages);
            
            if (configManager.isDebug()) {
                plugin.getLogger().info("[Metaso] 发送搜索请求: " + query);
                plugin.getLogger().info("[Metaso] 模型: " + model);
                plugin.getLogger().info("[Metaso] 请求体: " + gson.toJson(requestBody));
            }
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(METASO_API_URL))
                    .header("Authorization", "Bearer " + apiToken)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("User-Agent", "FancyHelper/" + plugin.getDescription().getVersion())
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(requestBody), StandardCharsets.UTF_8))
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            
            if (configManager.isDebug()) {
                plugin.getLogger().info("[Metaso] 响应状态码: " + response.statusCode());
                String debugBody = response.body();
                if (debugBody != null && debugBody.length() > 500) {
                    debugBody = debugBody.substring(0, 500) + "...";
                }
                plugin.getLogger().info("[Metaso] 响应体: " + debugBody);
            }
            
            if (response.statusCode() == 200) {
                return parseSearchResponse(response.body(), query);
            } else {
                plugin.getLogger().warning("[Metaso] 搜索失败，状态码: " + response.statusCode());
                plugin.getLogger().warning("[Metaso] 错误响应: " + response.body());
                return "Metaso 搜索失败 (HTTP " + response.statusCode() + "): " + extractErrorMessage(response.body());
            }
            
        } catch (Exception e) {
            plugin.getLogger().severe("[Metaso] 搜索异常: " + e.getMessage());
            if (configManager.isDebug()) {
                e.printStackTrace();
            }
            return "Metaso 搜索出错: " + e.getMessage();
        }
    }
    
    /**
     * 执行 Metaso AI 搜索（带对话历史）
     * 
     * @param query 搜索查询
     * @param conversationId 对话 ID（可选，用于多轮对话）
     * @return 格式化的搜索结果字符串
     */
    public String searchWithContext(String query, String conversationId) {
        // 目前简化实现，直接调用 search
        // 未来可以扩展支持多轮对话
        return search(query);
    }
    
    /**
     * 解析 Metaso 搜索响应
     * 
     * @param responseBody 响应体 JSON
     * @param query 原始查询
     * @return 格式化的搜索结果
     */
    private String parseSearchResponse(String responseBody, String query) {
        try {
            JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
            StringBuilder result = new StringBuilder();
            
            // 解析 OpenAI 兼容格式的响应
            if (json.has("choices") && json.get("choices").isJsonArray()) {
                JsonArray choices = json.getAsJsonArray("choices");
                
                if (choices.size() == 0) {
                    return "Metaso 未返回搜索结果。";
                }
                
                JsonObject choice = choices.get(0).getAsJsonObject();
                
                // 获取消息内容
                if (choice.has("message")) {
                    JsonObject message = choice.getAsJsonObject("message");
                    if (message.has("content") && !message.get("content").isJsonNull()) {
                        String content = message.get("content").getAsString();
                        result.append("【Metaso AI 搜索结果】\n");
                        result.append("查询: ").append(query).append("\n\n");
                        result.append(content);
                    }
                }
                
                // 解析额外的搜索信息（如果有）
                if (json.has("web_pages") && json.get("web_pages").isJsonArray()) {
                    JsonArray webPages = json.getAsJsonArray("web_pages");
                    if (webPages.size() > 0) {
                        result.append("\n\n【参考来源】\n");
                        for (int i = 0; i < webPages.size(); i++) {
                            JsonObject page = webPages.get(i).getAsJsonObject();
                            String title = getStringOrDefault(page, "title", "无标题");
                            String url = getStringOrDefault(page, "url", "");
                            result.append((i + 1)).append(". ").append(title);
                            if (!url.isEmpty()) {
                                result.append(" - ").append(url);
                            }
                            result.append("\n");
                        }
                    }
                }
                
                // 解析引用信息（如果有）
                if (json.has("references") && json.get("references").isJsonArray()) {
                    JsonArray references = json.getAsJsonArray("references");
                    if (references.size() > 0) {
                        result.append("\n【引用信息】\n");
                        for (int i = 0; i < references.size(); i++) {
                            JsonObject ref = references.get(i).getAsJsonObject();
                            String title = getStringOrDefault(ref, "title", "无标题");
                            String url = getStringOrDefault(ref, "url", "");
                            result.append("[").append(i + 1).append("] ").append(title);
                            if (!url.isEmpty()) {
                                result.append(" - ").append(url);
                            }
                            result.append("\n");
                        }
                    }
                }
                
                if (result.length() == 0) {
                    return "Metaso 未返回有效内容。";
                }
                
                return result.toString();
                
            } else if (json.has("error")) {
                // 错误响应
                String error = json.get("error").getAsString();
                return "Metaso 搜索错误: " + error;
            } else {
                // 尝试解析其他格式
                plugin.getLogger().warning("[Metaso] 未知的响应格式: " + responseBody.substring(0, Math.min(200, responseBody.length())));
                return "Metaso 返回了未知格式的响应，请查看控制台日志。";
            }
            
        } catch (Exception e) {
            plugin.getLogger().warning("[Metaso] 解析响应失败: " + e.getMessage());
            return "解析 Metaso 搜索结果失败: " + e.getMessage();
        }
    }
    
    /**
     * 安全获取字符串字段
     */
    private String getStringOrDefault(JsonObject json, String field, String defaultValue) {
        if (json.has(field) && !json.get(field).isJsonNull()) {
            return json.get(field).getAsString();
        }
        return defaultValue;
    }
    
    /**
     * 从错误响应中提取错误消息
     */
    private String extractErrorMessage(String responseBody) {
        try {
            JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
            if (json.has("error")) {
                if (json.get("error").isJsonObject()) {
                    JsonObject errorObj = json.getAsJsonObject("error");
                    if (errorObj.has("message")) {
                        return errorObj.get("message").getAsString();
                    }
                    return errorObj.toString();
                }
                return json.get("error").getAsString();
            } else if (json.has("message")) {
                return json.get("message").getAsString();
            } else if (json.has("detail")) {
                return json.get("detail").getAsString();
            }
        } catch (Exception ignored) {
        }
        return responseBody;
    }
    
    /**
     * 关闭 HTTP 客户端
     */
    public void shutdown() {
        // Java 标准库的 HttpClient 不需要显式关闭
        plugin.getLogger().info("[MetasoAPI] HTTP 客户端已完成关闭。");
    }
}
