package org.YanPl.api;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.YanPl.FancyHelper;
import org.bukkit.entity.Player;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 流式输出处理器
 * 负责解析SSE格式的AI响应，并以32字为缓冲单位发送给玩家
 */
public class StreamingHandler {
    private static final int BUFFER_SIZE = 32;
    
    private final FancyHelper plugin;
    private final StringBuilder buffer;
    private final AtomicBoolean isCancelled;
    private final Gson gson;
    private volatile Consumer<String> onChunkCallback;
    private volatile Consumer<String> onCompleteCallback;
    private volatile Consumer<Throwable> onErrorCallback;
    
    /**
     * 创建流式输出处理器
     * @param plugin 插件实例
     * @param player 目标玩家
     */
    public StreamingHandler(FancyHelper plugin, Player player) {
        this.plugin = plugin;
        this.buffer = new StringBuilder();
        this.isCancelled = new AtomicBoolean(false);
        this.gson = new Gson();
    }
    
    /**
     * 设置数据块回调（每收到32字触发一次）
     * @param callback 回调函数，参数为文本片段
     */
    public void setOnChunkCallback(Consumer<String> callback) {
        this.onChunkCallback = callback;
    }
    
    /**
     * 设置完成回调
     * @param callback 回调函数，参数为完整文本
     */
    public void setOnCompleteCallback(Consumer<String> callback) {
        this.onCompleteCallback = callback;
    }
    
    /**
     * 设置错误回调
     * @param callback 回调函数，参数为异常
     */
    public void setOnErrorCallback(Consumer<Throwable> callback) {
        this.onErrorCallback = callback;
    }
    
    /**
     * 取消流式输出
     */
    public void cancel() {
        isCancelled.set(true);
    }
    
    /**
     * 检查是否已取消
     * @return 是否已取消
     */
    public boolean isCancelled() {
        return isCancelled.get();
    }
    
    /**
     * 处理流式响应
     * @param response HTTP响应
     * @return 完整的响应文本
     */
    public String processStream(HttpResponse<InputStream> response) throws IOException {
        StringBuilder fullText = new StringBuilder();
        
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
            
            String line;
            while ((line = reader.readLine()) != null && !isCancelled.get()) {
                line = line.trim();
                
                if (line.isEmpty()) {
                    continue;
                }
                
                if (line.startsWith("data:")) {
                    String data = line.substring(5).trim();
                    
                    if ("[DONE]".equals(data)) {
                        break;
                    }
                    
                    try {
                        String textChunk = extractTextFromSSE(data);
                        if (textChunk != null && !textChunk.isEmpty()) {
                            fullText.append(textChunk);
                            buffer.append(textChunk);
                            
                            flushBufferIfReady();
                        }
                    } catch (Exception e) {
                        if (plugin.getConfigManager().isDebug()) {
                            plugin.getLogger().warning("[Stream] 解析SSE数据失败: " + e.getMessage() + " | 原始数据: " + data);
                        }
                    }
                }
            }
            
            flushRemainingBuffer();
            
            if (onCompleteCallback != null && !isCancelled.get()) {
                onCompleteCallback.accept(fullText.toString());
            }
            
        } catch (IOException e) {
            if (onErrorCallback != null) {
                onErrorCallback.accept(e);
            }
            throw e;
        }
        
        return fullText.toString();
    }
    
    /**
     * 从SSE数据行中提取文本内容
     * 支持多种格式：
     * 1. CloudFlare原生格式: {"response":"text"}
     * 2. OpenAI格式: {"choices":[{"delta":{"content":"text"}}]}
     * 3. 通用格式: {"content":"text"} 或 {"text":"text"}
     */
    private String extractTextFromSSE(String jsonStr) {
        try {
            JsonObject json = gson.fromJson(jsonStr, JsonObject.class);
            
            if (json.has("choices") && json.get("choices").isJsonArray()) {
                var choices = json.getAsJsonArray("choices");
                if (choices.size() > 0) {
                    var choice = choices.get(0).getAsJsonObject();
                    if (choice.has("delta") && choice.get("delta").isJsonObject()) {
                        var delta = choice.getAsJsonObject("delta");
                        if (delta.has("content") && !delta.get("content").isJsonNull()) {
                            return delta.get("content").getAsString();
                        }
                    }
                    if (choice.has("text") && !choice.get("text").isJsonNull()) {
                        return choice.get("text").getAsString();
                    }
                }
            }
            
            if (json.has("response") && !json.get("response").isJsonNull()) {
                return json.get("response").getAsString();
            }
            
            if (json.has("content") && !json.get("content").isJsonNull()) {
                return json.get("content").getAsString();
            }
            
            if (json.has("text") && !json.get("text").isJsonNull()) {
                return json.get("text").getAsString();
            }
            
        } catch (Exception e) {
            if (plugin.getConfigManager().isDebug()) {
                plugin.getLogger().warning("[Stream] JSON解析异常: " + e.getMessage());
            }
        }
        
        return null;
    }
    
    /**
     * 如果缓冲区达到32字，则发送并清空
     */
    private void flushBufferIfReady() {
        if (buffer.length() >= BUFFER_SIZE) {
            String text = buffer.toString();
            buffer.setLength(0);
            
            if (onChunkCallback != null) {
                onChunkCallback.accept(text);
            }
        }
    }
    
    /**
     * 发送剩余的缓冲区内容
     */
    private void flushRemainingBuffer() {
        if (buffer.length() > 0 && !isCancelled.get()) {
            String text = buffer.toString();
            buffer.setLength(0);
            
            if (onChunkCallback != null) {
                onChunkCallback.accept(text);
            }
        }
    }
}
