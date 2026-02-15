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
import java.time.Duration;

/**
 * Tavily API 封装类
 * 用于执行全网搜索，提供高质量的搜索结果
 * 
 * @see <a href="https://docs.tavily.com/documentation/api-reference/endpoint/search">Tavily Search API 文档</a>
 */
public class TavilyAPI {
    
    private final FancyHelper plugin;
    private final ConfigManager configManager;
    private final HttpClient httpClient;
    private final Gson gson;
    
    // Tavily Search API 官方端点
    private static final String TAVILY_OFFICIAL_URL = "https://api.tavily.com/search";
    
    public TavilyAPI(FancyHelper plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.gson = new Gson();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }
    
    /**
     * 获取 Tavily API URL（优先使用代理地址）
     * @return API URL
     */
    private String getApiUrl() {
        String proxyUrl = configManager.getTavilyProxyUrl();
        if (proxyUrl != null && !proxyUrl.isEmpty()) {
            // 确保代理 URL 以 /search 结尾
            if (!proxyUrl.endsWith("/search")) {
                if (proxyUrl.endsWith("/")) {
                    return proxyUrl + "search";
                } else {
                    return proxyUrl + "/search";
                }
            }
            return proxyUrl;
        }
        return TAVILY_OFFICIAL_URL;
    }
    
    /**
     * 执行 Tavily 搜索
     * 
     * @param query 搜索查询
     * @return 格式化的搜索结果字符串
     */
    public String search(String query) {
        return search(query, configManager.getTavilyMaxResults());
    }
    
    /**
     * 执行 Tavily 搜索
     * 
     * @param query 搜索查询
     * @param maxResults 最大结果数量
     * @return 格式化的搜索结果字符串
     */
    public String search(String query, int maxResults) {
        if (!configManager.isTavilyEnabled()) {
            return "Tavily 搜索未启用。请在 config.yml 中配置 tavily.enabled = true";
        }
        
        String apiKey = configManager.getTavilyApiKey();
        if (apiKey == null || apiKey.isEmpty()) {
            return "Tavily API 密钥未配置。请在 config.yml 中设置 tavily.api_key";
        }
        
        String apiUrl = getApiUrl();
        
        try {
            // 构建请求体
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("api_key", apiKey);
            requestBody.addProperty("query", query);
            requestBody.addProperty("max_results", Math.min(maxResults, 10)); // Tavily 最多返回 10 条
            requestBody.addProperty("include_raw_content", configManager.isTavilyIncludeRawContent());
            // 使用 basic 搜索深度，平衡速度和质量
            requestBody.addProperty("search_depth", "basic");
            // 包含答案，提供更智能的摘要
            requestBody.addProperty("include_answer", true);
            
            if (configManager.isDebug()) {
                plugin.getLogger().info("[Tavily] 发送搜索请求: " + query);
                plugin.getLogger().info("[Tavily] API URL: " + apiUrl);
            }
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "FancyHelper/" + plugin.getDescription().getVersion())
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(requestBody)))
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (configManager.isDebug()) {
                plugin.getLogger().info("[Tavily] 响应状态码: " + response.statusCode());
            }
            
            if (response.statusCode() == 200) {
                return parseSearchResponse(response.body(), query);
            } else {
                plugin.getLogger().warning("[Tavily] 搜索失败，状态码: " + response.statusCode());
                plugin.getLogger().warning("[Tavily] 错误响应: " + response.body());
                return "Tavily 搜索失败 (HTTP " + response.statusCode() + "): " + extractErrorMessage(response.body());
            }
            
        } catch (Exception e) {
            plugin.getLogger().severe("[Tavily] 搜索异常: " + e.getMessage());
            if (configManager.isDebug()) {
                e.printStackTrace();
            }
            return "Tavily 搜索出错: " + e.getMessage();
        }
    }
    
    /**
     * 解析 Tavily 搜索响应
     * 
     * @param responseBody 响应体 JSON
     * @param query 原始查询
     * @return 格式化的搜索结果
     */
    private String parseSearchResponse(String responseBody, String query) {
        try {
            JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
            StringBuilder result = new StringBuilder();
            
            // 添加 AI 生成的答案（如果有）
            if (json.has("answer") && !json.get("answer").isJsonNull()) {
                String answer = json.get("answer").getAsString();
                if (!answer.isEmpty()) {
                    result.append("【AI 摘要答案】\n").append(answer).append("\n\n");
                }
            }
            
            // 解析搜索结果
            if (json.has("results") && json.get("results").isJsonArray()) {
                JsonArray results = json.getAsJsonArray("results");
                
                if (results.size() == 0) {
                    return "未找到相关搜索结果。";
                }
                
                result.append("【搜索结果】(").append(query).append(")\n");
                
                int count = 0;
                for (int i = 0; i < results.size() && count < configManager.getTavilyMaxResults(); i++) {
                    JsonObject item = results.get(i).getAsJsonObject();
                    
                    String title = getStringOrDefault(item, "title", "无标题");
                    String url = getStringOrDefault(item, "url", "");
                    String content = getStringOrDefault(item, "content", "");
                    
                    // 截断过长的内容
                    if (content.length() > 500) {
                        content = content.substring(0, 500) + "...";
                    }
                    
                    result.append("\n").append(count + 1).append(". ").append(title);
                    if (!url.isEmpty()) {
                        result.append("\n   链接: ").append(url);
                    }
                    if (!content.isEmpty()) {
                        result.append("\n   内容: ").append(content);
                    }
                    result.append("\n");
                    
                    count++;
                }
            } else {
                return "未找到相关搜索结果。";
            }
            
            return result.toString();
            
        } catch (Exception e) {
            plugin.getLogger().warning("[Tavily] 解析响应失败: " + e.getMessage());
            return "解析 Tavily 搜索结果失败: " + e.getMessage();
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
}
