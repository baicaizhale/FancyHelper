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
import java.util.logging.Logger;

/**
 * 流式输出处理器
 * 负责解析SSE格式的AI响应，并以视觉宽度为缓冲单位发送给玩家
 *
 * 改进: 增强异常处理、线程安全性和流取消机制
 */
public class StreamingHandler {
    private static final int MAX_LINE_WIDTH = 55;  // 视觉宽度阈值（中文字符=2，英文字符=1）
    private static final long READ_POLL_INTERVAL_MS = 100;  // 读取超时轮询间隔

    private final FancyHelper plugin;
    private final StringBuffer buffer;  // 线程安全的 StringBuffer 替代 StringBuilder
    private final StringBuilder thoughtContent;  // 累积思考内容（reasoning_content）
    private final AtomicBoolean isCancelled;
    private final Gson gson;
    private volatile Consumer<String> onChunkCallback;
    private volatile Consumer<String> onCompleteCallback;
    private volatile Consumer<Throwable> onErrorCallback;
    private volatile Consumer<Long> onReasoningCompleteCallback;  // 思考结束回调，参数为思考耗时ms
    private volatile boolean errorOccurred = false;
    private long reasoningStartTime = -1;       // 第一个 reasoning token 的时间戳
    private boolean reasoningJustCompleted = false;  // 本次 extractTextFromSSE 是否刚完成思考
    private boolean reasoningCompleteFired = false;  // 是否已触发过思考结束回调
    private volatile boolean toolCallDetected = false;  // 是否已检测到 # 工具调用标记
    private final Logger logger;
    private final int readTimeoutSeconds;  // 流式读取超时秒数
    
    /**
     * 创建流式输出处理器
     * @param plugin 插件实例
     * @param player 目标玩家
     */
    public StreamingHandler(FancyHelper plugin, Player player) {
        this.plugin = plugin;
        this.buffer = new StringBuffer();  // 线程安全的 StringBuffer
        this.thoughtContent = new StringBuilder();
        this.isCancelled = new AtomicBoolean(false);
        this.errorOccurred = false;
        this.gson = new Gson();
        this.logger = plugin.getLogger();
        this.readTimeoutSeconds = plugin.getConfigManager().getApiTimeoutSeconds();
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
     * 设置思考结束回调（当 reasoning_content 切换到 content 时触发）
     * @param callback 回调函数，参数为思考耗时毫秒
     */
    public void setOnReasoningCompleteCallback(Consumer<Long> callback) {
        this.onReasoningCompleteCallback = callback;
    }

    /**
     * 获取累积的思考内容（来自 reasoning_content 字段）
     * @return 思考内容字符串
     */
    public String getThoughtContent() {
        return thoughtContent.toString();
    }

    /**
     * 思考结束回调是否已触发
     */
    public boolean hasReasoningCompleteFired() {
        return reasoningCompleteFired;
    }
    
    /**
     * 取消流式输出
     * 清理所有资源和回调引用
     */
    public void cancel() {
        isCancelled.set(true);
        
        // 清理回调引用以防止内存泄漏
        try {
            onChunkCallback = null;
            onCompleteCallback = null;
            onErrorCallback = null;
            onReasoningCompleteCallback = null;
            buffer.setLength(0);  // 清空缓冲
            thoughtContent.setLength(0);  // 清空思考内容
            reasoningStartTime = -1;
            reasoningJustCompleted = false;
            reasoningCompleteFired = false;
            toolCallDetected = false;
            
            logger.info("[Stream] 流式输出已取消并清理资源");
        } catch (Exception e) {
            logger.warning("[Stream] 取消流式输出时出错: " + e.getMessage());
        }
    }
    
    /**
     * 检查是否已取消
     * @return 是否已取消
     */
    public boolean isCancelled() {
        return isCancelled.get();
    }
    
    /**
     * 检查是否发生错误
     * @return 是否发生错误
     */
    public boolean hasError() {
        return errorOccurred;
    }

    /**
     * 处理流式响应
     * @param response HTTP响应
     * @return 完整的响应文本
     */
    public String processStream(HttpResponse<InputStream> response) throws IOException {
        StringBuilder fullText = new StringBuilder();
        StringBuilder nonSseFallback = new StringBuilder();  // 非SSE回退缓冲

        // 包装 InputStream 以支持读取超时和取消检查
        InputStream timeoutIn = createTimeoutInputStream(response.body());

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(timeoutIn, StandardCharsets.UTF_8))) {

            String line;
            boolean foundDataPrefix = false;  // 是否找到了 data: 前缀
            while ((line = reader.readLine()) != null && !isCancelled.get()) {
                if (errorOccurred) {
                    break;  // 如果已发生错误，停止继续处理
                }

                try {
                    line = line.trim();

                    if (line.isEmpty()) {
                        continue;
                    }

                    if (line.startsWith("data:")) {
                        foundDataPrefix = true;
                        String data = line.substring(5).trim();

                        if ("[DONE]".equals(data)) {
                            break;
                        }

                        try {
                            String textChunk = extractTextFromSSE(data);

                            // 检测 reasoning 刚结束 → 触发思考结束回调
                            if (reasoningJustCompleted) {
                                reasoningJustCompleted = false;
                                reasoningCompleteFired = true;
                                long thinkingMs = System.currentTimeMillis() - reasoningStartTime;
                                if (onReasoningCompleteCallback != null && !isCancelled.get()) {
                                    try {
                                        onReasoningCompleteCallback.accept(thinkingMs);
                                    } catch (Exception cbError) {
                                        logger.warning("[Stream] 思考结束回调异常: " + cbError.getMessage());
                                    }
                                }
                            }

                            if (textChunk != null && !textChunk.isEmpty()) {
                                fullText.append(textChunk);

                                if (!toolCallDetected) {
                                    int hashIndex = textChunk.indexOf('#');
                                    if (hashIndex >= 0) {
                                        toolCallDetected = true;
                                        // buffer 中可能有上一轮 flush 留下的尾部空白（如 \n\n），
                                        // 在 # 被检测到时一并裁掉，避免正文和工具调用之间出现空行
                                        rtrimBuffer();
                                        if (hashIndex > 0) {
                                            String prefix = rtrim(textChunk.substring(0, hashIndex));
                                            if (!prefix.isEmpty()) {
                                                appendWithNewlineFlush(prefix);
                                            }
                                        }
                                    } else {
                                        appendWithNewlineFlush(textChunk);
                                    }
                                }

                                flushBufferIfReady();
                            }
                        } catch (Exception e) {
                            // 记录解析错误但继续处理
                            logger.warning("[Stream] 解析SSE数据失败: " + e.getMessage() + " | 原始数据: " + data);
                            if (plugin.getConfigManager().isDebug()) {
                                logger.warning("[Stream] 完整错误堆栈:");
                                e.printStackTrace();
                            }
                            // 不调用 onErrorCallback，继续处理下一行
                        }
                    } else if (!foundDataPrefix && fullText.length() == 0) {
                        // 还没找到 data: 前缀时，缓存非空行作为非SSE回退
                        nonSseFallback.append(line);
                    }
                } catch (Exception lineProcessingError) {
                    // 行处理异常，记录但继续
                    logger.warning("[Stream] 处理流式行时出错: " + lineProcessingError.getMessage());
                    if (plugin.getConfigManager().isDebug()) {
                        lineProcessingError.printStackTrace();
                    }
                    continue;
                }
            }

            // 如果 SSE 解析没有产生任何文本，尝试作为非流式 JSON 响应解析
            if (fullText.length() == 0 && nonSseFallback.length() > 0 && !isCancelled.get()) {
                String fallbackJson = nonSseFallback.toString();
                try {
                    String fallbackText = extractTextFromSSE(fallbackJson);
                    if (fallbackText != null && !fallbackText.isEmpty()) {
                        logger.info("[Stream] 从非SSE响应中提取到文本 (长度: " + fallbackText.length() + ")");
                        fullText.append(fallbackText);
                        buffer.append(fallbackText);
                    }
                } catch (Exception e) {
                    logger.warning("[Stream] 非SSE回退解析失败: " + e.getMessage());
                }
            }
            
            flushRemainingBuffer();
            
            // 完成回调：只在未被取消且未出错时触发
            if (!isCancelled.get() && !errorOccurred && onCompleteCallback != null) {
                try {
                    onCompleteCallback.accept(fullText.toString());
                } catch (Exception callbackError) {
                    errorOccurred = true;
                    logger.warning("[Stream] 完成回调异常: " + callbackError.getMessage());
                    if (onErrorCallback != null) {
                        try {
                            onErrorCallback.accept(callbackError);
                        } catch (Exception errorCallbackError) {
                            logger.warning("[Stream] 错误回调异常: " + errorCallbackError.getMessage());
                        }
                    }
                }
            }
            
        } catch (IOException e) {
            errorOccurred = true;
            logger.warning("[Stream] IO异常: " + e.getMessage());
            
            if (onErrorCallback != null) {
                try {
                    onErrorCallback.accept(e);
                } catch (Exception errorCallbackException) {
                    logger.warning("[Stream] 错误回调中发生异常: " + errorCallbackException.getMessage());
                    if (plugin.getConfigManager().isDebug()) {
                        errorCallbackException.printStackTrace();
                    }
                }
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
     * 3. CloudFlare Responses API: {"type":"response.output_text.delta","data":{"delta":"text"}}
     * 4. 通用格式: {"content":"text"} 或 {"text":"text"}
     *
     * @param jsonStr JSON字符串
     * @return 提取的文本内容，如果无法解析返回null
     * @throws IllegalArgumentException 如果JSON格式完全无效
     */
    private String extractTextFromSSE(String jsonStr) throws IllegalArgumentException {
        if (jsonStr == null || jsonStr.isEmpty()) {
            return null;
        }

        try {
            JsonObject json = gson.fromJson(jsonStr, JsonObject.class);

            if (json == null) {
                logger.warning("[Stream] JSON解析结果为null: " + jsonStr);
                return null;
            }

            // 标记本 chunk 是否包含 reasoning（思考）内容，
            // 若是则无需在末尾打印"无法提取文本"的调试日志
            boolean hasReasoningInChunk = false;

            // 1. 尝试解析 OpenAI 格式 (choices 数组)
            if (json.has("choices") && json.get("choices").isJsonArray()) {
                var choices = json.getAsJsonArray("choices");
                if (choices.size() > 0) {
                    var choice = choices.get(0).getAsJsonObject();
                    if (choice.has("delta") && choice.get("delta").isJsonObject()) {
                        var delta = choice.getAsJsonObject("delta");
                        if (delta.has("content") && !delta.get("content").isJsonNull()) {
                            // 首次从 reasoning 切换到 content，标记思考结束
                            if (!reasoningCompleteFired && !reasoningJustCompleted && reasoningStartTime != -1 && thoughtContent.length() > 0) {
                                reasoningJustCompleted = true;
                            }
                            return delta.get("content").getAsString();
                        }
                        // 捕获思考模型的 reasoning_content（DeepSeek R1, OpenAI o1/o3 等）
                        if (delta.has("reasoning_content") && !delta.get("reasoning_content").isJsonNull()) {
                            String rc = delta.get("reasoning_content").getAsString();
                            if (!rc.isEmpty()) {
                                hasReasoningInChunk = true;
                                // 第一个非空 reasoning token → 开始计时
                                if (reasoningStartTime == -1) {
                                    reasoningStartTime = System.currentTimeMillis();
                                }
                                thoughtContent.append(rc);
                            }
                        }
                    }
                    if (choice.has("text") && !choice.get("text").isJsonNull()) {
                        return choice.get("text").getAsString();
                    }
                }
            }

            // 2. 尝试解析 CloudFlare Responses API 格式
            // SSE 事件格式:
            //   event: response.output_text.delta
            //   data: {"type":"response.output_text.delta","data":{"delta":"text"}}
            //   event: response.output_text.done
            //   data: {"type":"response.output_text.done","data":{"text":"text"}}
            //   event: response.reasoning.delta
            //   data: {"type":"response.reasoning.delta","data":{"delta":"thinking"}}
            if (json.has("type") && !json.get("type").isJsonNull()) {
                String type = json.get("type").getAsString();
                // 捕获思考模型的 reasoning 事件
                if (type.startsWith("response.reasoning.")) {
                    hasReasoningInChunk = true;
                    if (json.has("data") && json.get("data").isJsonObject()) {
                        JsonObject innerData = json.getAsJsonObject("data");
                        if (type.endsWith(".delta") && innerData.has("delta") && !innerData.get("delta").isJsonNull()) {
                            String rc = innerData.get("delta").getAsString();
                            if (!rc.isEmpty()) {
                                if (reasoningStartTime == -1) {
                                    reasoningStartTime = System.currentTimeMillis();
                                }
                                thoughtContent.append(rc);
                            }
                        }
                    }
                    return null;
                }
                if (type.startsWith("response.output_text.")) {
                    if (json.has("data") && json.get("data").isJsonObject()) {
                        JsonObject innerData = json.getAsJsonObject("data");
                        // delta 事件: type="response.output_text.delta", data.delta="text"
                        if (type.endsWith(".delta") && innerData.has("delta") && !innerData.get("delta").isJsonNull()) {
                            // 首次从 reasoning 切换到 content，标记思考结束
                            if (!reasoningCompleteFired && !reasoningJustCompleted && reasoningStartTime != -1 && thoughtContent.length() > 0) {
                                reasoningJustCompleted = true;
                            }
                            return innerData.get("delta").getAsString();
                        }
                        // done 事件: type="response.output_text.done", data.text="text"
                        if (type.endsWith(".done") && innerData.has("text") && !innerData.get("text").isJsonNull()) {
                            return innerData.get("text").getAsString();
                        }
                    }
                }
            }

            // 3. 尝试解析 CloudFlare 原生 response 格式
            if (json.has("response") && !json.get("response").isJsonNull()) {
                return json.get("response").getAsString();
            }

            // 4. 尝试解析通用 content 格式
            if (json.has("content") && !json.get("content").isJsonNull()) {
                return json.get("content").getAsString();
            }

            // 5. 尝试解析通用 text 格式
            if (json.has("text") && !json.get("text").isJsonNull()) {
                return json.get("text").getAsString();
            }

            // 如果到这里，可能是其他格式的 SSE 数据（如 [DONE] 标记或控制信息）
            // reasoning 内容已在前面捕获，跳过日志避免噪音
            if (plugin.getConfigManager().isDebug() && !hasReasoningInChunk) {
                logger.info("[Stream] 无法从JSON中提取文本内容: " + jsonStr);
            }

        } catch (com.google.gson.JsonSyntaxException jsonSyntaxError) {
            // JSON 格式错误
            logger.warning("[Stream] JSON语法错误: " + jsonSyntaxError.getMessage());
            if (plugin.getConfigManager().isDebug()) {
                logger.warning("[Stream] 原始数据: " + jsonStr);
            }
            throw new IllegalArgumentException("JSON格式无效: " + jsonSyntaxError.getMessage(), jsonSyntaxError);
        } catch (Exception e) {
            logger.warning("[Stream] 提取文本异常: " + e.getClass().getName() + " - " + e.getMessage());
            if (plugin.getConfigManager().isDebug()) {
                logger.warning("[Stream] 原始数据: " + jsonStr);
                e.printStackTrace();
            }
            // 不重新抛出，继续处理
        }

        return null;
    }
    
    /**
     * 计算文本在Minecraft聊天框中的视觉宽度
     * 中文字符/全角字符权重为2，ASCII/半角字符权重为1
     * @param text 要计算的文本
     * @return 视觉宽度值
     */
    private int getVisualWidth(CharSequence text) {
        int width = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (isFullWidth(c)) {
                width += 2;
            } else {
                width += 1;
            }
        }
        return width;
    }

    private boolean isFullWidth(char c) {
        // CJK Radicals Supplement
        if (c >= 0x2E80 && c <= 0x2EFF) return true;
        // Kangxi Radicals
        if (c >= 0x2F00 && c <= 0x2FDF) return true;
        // Ideographic Description Characters
        if (c >= 0x2FF0 && c <= 0x2FFF) return true;
        // CJK Symbols and Punctuation（全角空格、各种中文标点）
        if (c >= 0x3000 && c <= 0x303F) return true;
        // Hiragana
        if (c >= 0x3040 && c <= 0x309F) return true;
        // Katakana
        if (c >= 0x30A0 && c <= 0x30FF) return true;
        // Bopomofo
        if (c >= 0x3100 && c <= 0x312F) return true;
        // Hangul Compatibility Jamo
        if (c >= 0x3130 && c <= 0x318F) return true;
        // Enclosed CJK Letters and Months
        if (c >= 0x3200 && c <= 0x33FF) return true;
        // CJK Unified Ideographs Extension A
        if (c >= 0x3400 && c <= 0x4DBF) return true;
        // Yijing Hexagram Symbols
        if (c >= 0x4DC0 && c <= 0x4DFF) return true;
        // CJK Unified Ideographs
        if (c >= 0x4E00 && c <= 0x9FFF) return true;
        // Yi Script
        if (c >= 0xA000 && c <= 0xA4CF) return true;
        // Hangul Syllables
        if (c >= 0xAC00 && c <= 0xD7AF) return true;
        // CJK Compatibility Ideographs
        if (c >= 0xF900 && c <= 0xFAFF) return true;
        // Vertical Forms
        if (c >= 0xFE10 && c <= 0xFE1F) return true;
        // CJK Compatibility Forms
        if (c >= 0xFE30 && c <= 0xFE4F) return true;
        // Fullwidth Forms: fullwidth ASCII variants, fullwidth left/right white parenthesis, fullwidth signs
        if (c >= 0xFF01 && c <= 0xFF60) return true;
        if (c >= 0xFFE0 && c <= 0xFFE6) return true;
        return false;
    }

    /**
     * 如果缓冲区的视觉宽度达到阈值，则发送并清空
     */
    private void flushBufferIfReady() {
        if (getVisualWidth(buffer) >= MAX_LINE_WIDTH) {
            flushBuffer();
        }
    }

    /**
     * 强制发送缓冲区内容（忽略视觉宽度阈值）
     */
    private void flushBuffer() {
        if (buffer.length() == 0) return;
        String text = buffer.toString();
        if (!toolCallDetected) {
            int hashIndex = text.indexOf('#');
            if (hashIndex >= 0) {
                toolCallDetected = true;
                text = text.substring(0, hashIndex);
            }
        }
        buffer.setLength(0);
        if (onChunkCallback != null && !text.isEmpty()) {
            try {
                onChunkCallback.accept(text);
            } catch (Exception callbackError) {
                errorOccurred = true;
                logger.warning("[Stream] Flush回调异常: " + callbackError.getMessage());
                if (onErrorCallback != null) {
                    try { onErrorCallback.accept(callbackError); } catch (Exception e) {}
                }
            }
        }
    }

    /**
     * 发送剩余的缓冲区内容（最终 flush，裁掉尾部空白）
     */
    private void flushRemainingBuffer() {
        if (buffer.length() > 0 && !isCancelled.get()) {
            rtrimBuffer();
            flushBuffer();
        }
    }

    /**
     * 追加文本到缓冲区，遇 \\n 自动分段 flush。
     * 最后一个 \\n 及之后的内容留在缓冲区，不立即 flush：
     * - 若下个 chunk 是普通文本 → \\n 变为正常的行分隔
     * - 若下个 chunk 是 # → rtrimBuffer 裁掉，避免工具调用前出现空行
     */
    private void appendWithNewlineFlush(String text) {
        if (text.isEmpty()) return;
        // 纯空白 chunk（如 \\n\\n）全部留在 buffer，等下一个 chunk 决定命运
        if (text.trim().isEmpty()) {
            buffer.append(text);
            return;
        }
        int lastNewline = text.lastIndexOf('\n');
        if (lastNewline >= 0) {
            // 最后一个 \\n 之前的内容立即 flush
            if (lastNewline > 0) {
                buffer.append(text.substring(0, lastNewline));
                flushBuffer();
            }
            // 最后一个 \\n 及之后的内容留在 buffer
            buffer.append(text.substring(lastNewline));
        } else {
            buffer.append(text);
        }
    }

    /**
     * 去掉字符串尾部空白字符（空格、\\r、\\n、\\t 等）
     */
    private static String rtrim(String text) {
        int end = text.length();
        while (end > 0 && Character.isWhitespace(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(0, end);
    }

    /**
     * 就地裁掉 buffer 尾部的空白字符，避免 # 工具调用前残留空行
     */
    private void rtrimBuffer() {
        int end = buffer.length();
        while (end > 0 && Character.isWhitespace(buffer.charAt(end - 1))) {
            end--;
        }
        if (end < buffer.length()) {
            buffer.setLength(end);
        }
    }

    /**
     * 创建支持读取超时的 InputStream 包装器
     * 当 AI 服务器停止发送数据超过配置超时时间时，自动抛出 IOException
     * @param in 原始输入流
     * @return 带超时控制的输入流
     */
    private InputStream createTimeoutInputStream(InputStream in) {
        return new InputStream() {

            @Override
            public int read() throws IOException {
                waitForData();
                int b = in.read();
                return b;
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                waitForData();
                int n = in.read(b, off, len);
                return n;
            }

            /**
             * 等待数据可用，带有超时和取消检查
             */
            private void waitForData() throws IOException {
                long deadline = System.currentTimeMillis() + readTimeoutSeconds * 1000L;
                try {
                    while (in.available() == 0) {
                        if (isCancelled.get()) {
                            throw new IOException("流式读取已取消");
                        }
                        if (System.currentTimeMillis() > deadline) {
                            throw new IOException("流式读取超时 (" + readTimeoutSeconds + " 秒无数据)");
                        }
                        Thread.sleep(READ_POLL_INTERVAL_MS);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("流式读取被中断", e);
                }
            }

            @Override
            public int available() throws IOException {
                return in.available();
            }

            @Override
            public void close() throws IOException {
                in.close();
            }
        };
    }
}
