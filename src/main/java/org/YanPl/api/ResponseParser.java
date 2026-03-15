package org.YanPl.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.YanPl.model.AIResponse;

/**
 * AI API 响应解析器
 * 负责解析多种 API 格式的响应（OpenAI 兼容格式、CloudFlare 原生格式等）
 */
public class ResponseParser {

    /**
     * 解析 AI API 响应
     * 支持多种格式：OpenAI choices 格式、CloudFlare output 格式、CloudFlare result 格式
     *
     * @param responseJson 响应 JSON 对象
     * @return 解析后的 AIResponse，如果解析失败返回 null
     */
    public AIResponse parseResponse(JsonObject responseJson) {
        String textContent = null;
        String thoughtContent = null;
        long promptTokens = 0;
        long completionTokens = 0;

        // 解析 Token 使用情况 (OpenAI 格式)
        if (responseJson.has("usage") && responseJson.get("usage").isJsonObject()) {
            JsonObject usage = responseJson.getAsJsonObject("usage");
            promptTokens = usage.has("prompt_tokens") ? usage.get("prompt_tokens").getAsLong() : 0;
            completionTokens = usage.has("completion_tokens") ? usage.get("completion_tokens").getAsLong() : 0;
        }
        // 解析 Token 使用情况 (CloudFlare result.usage 格式)
        else if (responseJson.has("result") && responseJson.get("result").isJsonObject()) {
            JsonObject result = responseJson.getAsJsonObject("result");
            if (result.has("usage") && result.get("usage").isJsonObject()) {
                JsonObject usage = result.getAsJsonObject("usage");
                promptTokens = usage.has("prompt_tokens") ? usage.get("prompt_tokens").getAsLong() : 0;
                completionTokens = usage.has("completion_tokens") ? usage.get("completion_tokens").getAsLong() : 0;
            }
        }

        // 1. 尝试解析 OpenAI 兼容格式 (choices 数组)
        ParsedContent openAiContent = parseOpenAiFormat(responseJson);
        if (openAiContent != null) {
            textContent = openAiContent.text;
            thoughtContent = openAiContent.thought;
        }

        // 2. 尝试解析 Cloudflare 原生 output 格式
        ParsedContent cfOutputContent = parseCloudFlareOutputFormat(responseJson);
        if (cfOutputContent != null) {
            if (textContent == null) textContent = cfOutputContent.text;
            if (thoughtContent == null) thoughtContent = cfOutputContent.thought;
        }

        // 3. 尝试解析 Cloudflare 原生 result 格式
        ParsedContent cfResultContent = parseCloudFlareResultFormat(responseJson);
        if (cfResultContent != null) {
            if (textContent == null) textContent = cfResultContent.text;
            if (thoughtContent == null) thoughtContent = cfResultContent.thought;
        }

        // 检查 finish_reason 是否为 length（表示输出被截断）
        String finishReason = extractFinishReason(responseJson);
        boolean isTruncated = "length".equals(finishReason);
        if (isTruncated && textContent == null) {
            // 当 finish_reason 为 length 且 content 为 null 时，返回空内容但标记为截断
            return new AIResponse("", thoughtContent, promptTokens, completionTokens, true);
        } else if (isTruncated && textContent != null) {
            // 当 finish_reason 为 length 但有内容时，标记为截断
            return new AIResponse(textContent, thoughtContent, promptTokens, completionTokens, true);
        }

        if (textContent != null) {
            return new AIResponse(textContent, thoughtContent, promptTokens, completionTokens);
        }
        return null;
    }

    /**
     * 提取 finish_reason 字段
     */
    private String extractFinishReason(JsonObject responseJson) {
        // 尝试 OpenAI 格式
        if (responseJson.has("choices") && responseJson.get("choices").isJsonArray()) {
            JsonArray choices = responseJson.getAsJsonArray("choices");
            if (choices.size() > 0) {
                JsonObject choice = choices.get(0).getAsJsonObject();
                if (choice.has("finish_reason") && !choice.get("finish_reason").isJsonNull()) {
                    return choice.get("finish_reason").getAsString();
                }
            }
        }
        return null;
    }

    /**
     * 解析 OpenAI 兼容格式 (choices 数组)
     */
    private ParsedContent parseOpenAiFormat(JsonObject responseJson) {
        if (!responseJson.has("choices") || !responseJson.get("choices").isJsonArray()) {
            return null;
        }

        JsonArray choices = responseJson.getAsJsonArray("choices");
        if (choices.size() == 0) {
            return null;
        }

        JsonObject choice = choices.get(0).getAsJsonObject();
        if (!choice.has("message")) {
            return null;
        }

        JsonObject message = choice.getAsJsonObject("message");
        ParsedContent content = new ParsedContent();

        if (message.has("content") && !message.get("content").isJsonNull()) {
            content.text = message.get("content").getAsString();
        }

        if (message.has("reasoning_content") && !message.get("reasoning_content").isJsonNull()) {
            content.thought = message.get("reasoning_content").getAsString();
        }

        return content;
    }

    /**
     * 解析 Cloudflare 原生 output 格式
     */
    private ParsedContent parseCloudFlareOutputFormat(JsonObject responseJson) {
        if (!responseJson.has("output") || !responseJson.get("output").isJsonArray()) {
            return null;
        }

        JsonArray outputArray = responseJson.getAsJsonArray("output");
        ParsedContent content = new ParsedContent();

        for (int i = 0; i < outputArray.size(); i++) {
            JsonObject item = outputArray.get(i).getAsJsonObject();
            String itemType = item.has("type") ? item.get("type").getAsString() : "";

            if ("message".equals(itemType)) {
                parseMessageItem(item, content);
            } else if ("reasoning".equals(itemType)) {
                parseReasoningItem(item, content);
            }
        }

        return content;
    }

    /**
     * 解析 message 类型的 output 项
     */
    private void parseMessageItem(JsonObject item, ParsedContent content) {
        if (!item.has("content") || !item.get("content").isJsonArray()) {
            return;
        }

        JsonArray contents = item.getAsJsonArray("content");
        for (int j = 0; j < contents.size(); j++) {
            JsonObject contentObj = contents.get(j).getAsJsonObject();
            String type = contentObj.has("type") ? contentObj.get("type").getAsString() : "";

            if ("output_text".equals(type) && content.text == null) {
                if (!contentObj.get("text").isJsonNull()) {
                    content.text = contentObj.get("text").getAsString();
                }
            } else if (("thought".equals(type) || "reasoning".equals(type)) && content.thought == null) {
                if (!contentObj.get("text").isJsonNull()) {
                    content.thought = contentObj.get("text").getAsString();
                }
            }
        }
    }

    /**
     * 解析 reasoning 类型的 output 项
     */
    private void parseReasoningItem(JsonObject item, ParsedContent content) {
        if (content.thought != null) {
            return; // 已经有思考内容了，跳过
        }

        // 尝试从 summary 字段解析
        if (item.has("summary")) {
            content.thought = parseSummaryField(item.get("summary"), item);
        }

        // 如果 summary 没有内容，尝试从 content 字段解析
        if (content.thought == null && item.has("content") && item.get("content").isJsonArray()) {
            content.thought = parseReasoningContent(item.getAsJsonArray("content"));
        }
    }

    /**
     * 解析 summary 字段（可能是数组或字符串）
     */
    private String parseSummaryField(JsonElement summaryElement, JsonObject item) {
        if (summaryElement.isJsonArray()) {
            JsonArray summaryArray = summaryElement.getAsJsonArray();
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < summaryArray.size(); j++) {
                JsonObject summaryObj = summaryArray.get(j).getAsJsonObject();
                if (summaryObj.has("text") && !summaryObj.get("text").isJsonNull()) {
                    if (sb.length() > 0) sb.append("\n");
                    sb.append(summaryObj.get("text").getAsString());
                }
            }
            return sb.length() > 0 ? sb.toString() : null;
        } else if (summaryElement.isJsonPrimitive()) {
            return summaryElement.getAsString();
        }
        return null;
    }

    /**
     * 解析 reasoning content 数组
     */
    private String parseReasoningContent(JsonArray contents) {
        StringBuilder sb = new StringBuilder();
        for (int j = 0; j < contents.size(); j++) {
            JsonObject contentObj = contents.get(j).getAsJsonObject();
            String type = contentObj.has("type") ? contentObj.get("type").getAsString() : "";
            if ("reasoning_text".equals(type) && contentObj.has("text") && !contentObj.get("text").isJsonNull()) {
                if (sb.length() > 0) sb.append("\n");
                sb.append(contentObj.get("text").getAsString());
            }
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    /**
     * 解析 Cloudflare 原生 result 格式
     */
    private ParsedContent parseCloudFlareResultFormat(JsonObject responseJson) {
        if (!responseJson.has("result")) {
            return null;
        }

        JsonObject result = responseJson.getAsJsonObject("result");
        ParsedContent content = new ParsedContent();

        // 解析文本内容
        if (result.has("response") && !result.get("response").isJsonNull()) {
            content.text = result.get("response").getAsString();
        } else if (result.has("text") && !result.get("text").isJsonNull()) {
            content.text = result.get("text").getAsString();
        }

        // 解析思考内容
        if (result.has("reasoning") && !result.get("reasoning").isJsonNull()) {
            content.thought = result.get("reasoning").getAsString();
        } else if (result.has("thought") && !result.get("thought").isJsonNull()) {
            content.thought = result.get("thought").getAsString();
        }

        return content;
    }

    /**
     * 解析内容的内部类
     */
    private static class ParsedContent {
        String text;
        String thought;
    }
}
