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
 * DeepSeek 官方 Search / Fetch API 封装类
 * provider.search 为 deepseek 时作为搜索源，provider.jina 为 deepseek 时作为网页抓取源
 *
 * @see <a href="https://api-docs.deepseek.com/zh-cn/guides/search">DeepSeek Search/Fetch API 文档</a>
 */
public class DeepSeekSearchAPI {

    private final FancyHelper plugin;
    private final ConfigManager configManager;
    private final HttpClient httpClient;
    private final Gson gson;

    // DeepSeek Search/Fetch API 官方端点（与 chat completions 同一 Bearer 鉴权）
    private static final String SEARCH_URL = "https://api.deepseek.com/v1/tools/search";
    private static final String FETCH_URL = "https://api.deepseek.com/v1/tools/fetch";

    public DeepSeekSearchAPI(FancyHelper plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.gson = new Gson();
        // trust() 附加组合信任：默认 CA ∪ FancyConsole 直连自签证书 SPKI 锁定
        this.httpClient = FancyDirect.trust(HttpClient.newBuilder())
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    /**
     * 搜索源是否可用（provider.search 选了 deepseek 且配置了密钥）
     */
    public boolean isAvailable() {
        return configManager.isDeepSeekSearch() && !configManager.getDeepSeekApiKey().isEmpty();
    }

    /**
     * 执行 DeepSeek 网页搜索
     *
     * @param query 搜索查询
     * @return 格式化的搜索结果字符串
     */
    public String search(String query) {
        String apiKey = configManager.getDeepSeekApiKey();
        if (apiKey.isEmpty()) {
            return "DeepSeek 搜索未配置。请在 config.yml 中设置 deepseek.api_key";
        }

        try {
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("query", query);
            // count 影响 1-50，不影响单价；summary 字段是给模型读的长摘要，正文一般无需携带
            requestBody.addProperty("count", configManager.getDeepSeekSearchCount());
            requestBody.addProperty("need_content", configManager.isDeepSeekSearchNeedContent());

            if (configManager.isDebug()) {
                plugin.getLogger().info("[DeepSeek] 发送搜索请求: " + query);
            }

            HttpRequest request = buildRequest(SEARCH_URL, apiKey, gson.toJson(requestBody));
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (configManager.isDebug()) {
                plugin.getLogger().info("[DeepSeek] 搜索响应状态码: " + response.statusCode());
            }

            if (response.statusCode() == 200) {
                return parseSearchResponse(response.body(), query);
            }
            plugin.getLogger().warning("[DeepSeek] 搜索失败，状态码: " + response.statusCode());
            plugin.getLogger().warning("[DeepSeek] 错误响应: " + response.body());
            return "DeepSeek 搜索失败 (HTTP " + response.statusCode() + "): " + extractErrorMessage(response.body());
        } catch (Exception e) {
            plugin.getLogger().severe("[DeepSeek] 搜索异常: " + e.getMessage());
            if (configManager.isDebug()) {
                e.printStackTrace();
            }
            return "DeepSeek 搜索出错: " + e.getMessage();
        }
    }

    /**
     * 使用 DeepSeek Fetch API 抓取网页，返回 Markdown 正文
     *
     * @param url 目标网页
     * @return 网页内容；抓取失败返回 null（由调用方决定回退）
     */
    public String fetchPage(String url) {
        String apiKey = configManager.getDeepSeekApiKey();
        if (apiKey.isEmpty()) {
            return null;
        }

        try {
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("url", url);

            if (configManager.isDebug()) {
                plugin.getLogger().info("[DeepSeek] 发送抓取请求: " + url);
            }

            HttpRequest request = buildRequest(FETCH_URL, apiKey, gson.toJson(requestBody));
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            // 403=安全策略拒绝、404=无可用内容：属正常业务失败，静默回退
            if (response.statusCode() != 200) {
                if (configManager.isDebug()) {
                    plugin.getLogger().info("[DeepSeek] 抓取失败 (" + response.statusCode() + "): "
                            + extractErrorMessage(response.body()));
                }
                return null;
            }

            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            String content = getStringOrDefault(json, "content", "");
            if (content.isEmpty()) {
                return null;
            }
            String title = getStringOrDefault(json, "title", "");
            String result = title.isEmpty() ? content : "【" + title + "】\n" + content;
            if (result.length() > 8000) {
                result = result.substring(0, 8000) + "\n... (内容已截断)";
            }
            return result;
        } catch (Exception e) {
            if (configManager.isDebug()) {
                plugin.getLogger().info("[DeepSeek] 抓取异常: " + e.getMessage());
            }
            return null;
        }
    }

    private HttpRequest buildRequest(String url, String apiKey, String body) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .header("User-Agent", "FancyHelper/" + plugin.getDescription().getVersion())
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
    }

    /**
     * 解析 DeepSeek 搜索响应
     * 结果字段：title/url/snippet(短摘要)/summary(600-800字长摘要，适合直接喂模型)/date/rerank_score
     */
    private String parseSearchResponse(String responseBody, String query) {
        try {
            JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
            if (!json.has("results") || !json.get("results").isJsonArray()) {
                return "未找到相关搜索结果。";
            }
            JsonArray results = json.getAsJsonArray("results");
            if (results.size() == 0) {
                return "未找到相关搜索结果。";
            }

            StringBuilder result = new StringBuilder("【搜索结果】(").append(query).append(")\n");
            int count = 0;
            for (int i = 0; i < results.size(); i++) {
                JsonObject item = results.get(i).getAsJsonObject();

                String title = getStringOrDefault(item, "title", "无标题");
                String url = getStringOrDefault(item, "url", "");
                // summary 是官方给模型读的长摘要，比 snippet 适合直接回灌；超长兜底截断
                String summary = getStringOrDefault(item, "summary", "");
                if (summary.isEmpty()) {
                    summary = getStringOrDefault(item, "snippet", "");
                }
                if (summary.length() > 600) {
                    summary = summary.substring(0, 600) + "...";
                }
                String date = getStringOrDefault(item, "date", "");

                result.append("\n").append(count + 1).append(". ").append(title);
                if (!date.isEmpty()) {
                    result.append("（").append(date).append("）");
                }
                if (!url.isEmpty()) {
                    result.append("\n   链接: ").append(url);
                }
                if (!summary.isEmpty()) {
                    result.append("\n   摘要: ").append(summary);
                }
                result.append("\n");

                count++;
            }
            return result.toString();
        } catch (Exception e) {
            plugin.getLogger().warning("[DeepSeek] 解析响应失败: " + e.getMessage());
            return "解析 DeepSeek 搜索结果失败: " + e.getMessage();
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
     * 从错误响应中提取错误消息（OpenAI 兼容错误格式）
     */
    private String extractErrorMessage(String responseBody) {
        try {
            JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
            if (json.has("error") && json.get("error").isJsonObject()) {
                JsonObject error = json.getAsJsonObject("error");
                return getStringOrDefault(error, "message", responseBody);
            }
            if (json.has("message") && !json.get("message").isJsonNull()) {
                return json.get("message").getAsString();
            }
        } catch (Exception ignored) {
        }
        return responseBody;
    }
}
