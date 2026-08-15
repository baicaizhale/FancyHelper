package org.YanPl.api;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.YanPl.FancyHelper;
import org.YanPl.model.NativeToolCall;
import org.bukkit.entity.Player;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
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
    // 思考内容预算（字符）：gemma-4 等思考模型正常思考几百~2000 字符即出正文，
    // 超过该阈值仍无正文视为"思考循环"（反复输出同一段内心戏），流式侧主动中断，
    // 避免上游一直吐 reasoning 把请求拖到超时/无输出（连接有数据，看门狗不会触发）。
    private static final int THINKING_BUDGET = 4000;
    // 思考时间预算（毫秒）：模型思考超时仍未出正文同样视为循环。
    // 模型吐 reasoning 慢时字符预算可能迟迟达不到（HTTP 绝对超时先到，玩家看到超时错误），
    // 时间维度在 HTTP 超时前中断，让循环走"自动重试链"而非报错。
    private static final long THINKING_TIME_BUDGET_MS = 60000;

    private final FancyHelper plugin;
    private final StringBuffer buffer;  // 线程安全的 StringBuffer 替代 StringBuilder
    private final StringBuilder thoughtContent;  // 累积思考内容（reasoning_content）
    private final AtomicBoolean isCancelled;
    private final Gson gson;
    private volatile Consumer<String> onChunkCallback;
    private volatile Consumer<String> onCompleteCallback;
    private volatile Consumer<Throwable> onErrorCallback;
    private volatile Consumer<String> onReasoningCallback;      // 思考内容逐片回调
    private volatile Consumer<Long> onReasoningCompleteCallback;  // 思考结束回调，参数为思考耗时ms
    private volatile BiConsumer<Long, Long> onUsageTokens;        // API 返回的 token 用量回调 (input, output)
    private volatile boolean errorOccurred = false;
    private long reasoningStartTime = -1;       // 第一个 reasoning token 的时间戳
    private boolean reasoningJustCompleted = false;  // 本次 extractTextFromSSE 是否刚完成思考
    private boolean reasoningCompleteFired = false;  // 是否已触发过思考结束回调
    private volatile boolean reasoningBudgetExceeded = false;  // 思考内容超预算（疑似循环思考）已被中断
    private volatile boolean toolCallDetected = false;  // 是否已检测到 # 工具调用标记
    private final Logger logger;
    private final int readTimeoutSeconds;  // 流式读取超时秒数
    private long pendingUsageInput = 0;    // 流中最后一次出现的 usage 输入 token（累计值）
    private long pendingUsageOutput = 0;   // 流中最后一次出现的 usage 输出 token（累计值）
    private long pendingUsageCacheHit = 0;   // 流中最后一次出现的 usage 缓存命中 token（DeepSeek 等提供）
    private long pendingUsageCacheMiss = 0;  // 流中最后一次出现的 usage 缓存未命中 token
    private boolean usageSeen = false;     // 本次流是否出现过非零 usage

    // 原生函数调用（Native Function Calling）累加器
    // 流式 tool_calls 跨多个 SSE delta 分片到达：id+name 在首个 delta，arguments 是后续 delta 的 JSON 片段
    // 按 tool_call id 归并（CF gemma 流式并行调用共享同一 index，按 index 会导致合并丢失）
    private final Map<String, ToolCallAccum> nativeToolAccum = new LinkedHashMap<>();
    private List<NativeToolCall> nativeToolCalls = List.of();
    // 当前活跃 tool_call 的 key：CF gemma 的参数分片不带 id/index 归属，顺序路由到它
    private String currentToolCallKey;

    /** 流式 tool_call 的跨 delta 累加单元。 */
    public record ToolCallAccum(int index, String id, String name, StringBuilder arguments) {
    }
    
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
        // 钳制下限，避免配置为 0/负数时看门狗立即触发
        this.readTimeoutSeconds = Math.max(1, plugin.getConfigManager().getApiTimeoutSeconds());
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
     * 设置思考内容逐片回调（每收到一段 reasoning_content 时触发）
     * @param callback 回调函数，参数为思考内容文本片段
     */
    public void setOnReasoningCallback(Consumer<String> callback) {
        this.onReasoningCallback = callback;
    }

    /**
     * 设置思考结束回调（当 reasoning_content 切换到 content 时触发）
     * @param callback 回调函数，参数为思考耗时毫秒
     */
    public void setOnReasoningCompleteCallback(Consumer<Long> callback) {
        this.onReasoningCompleteCallback = callback;
    }

    /**
     * 设置 API token 用量回调（当 SSE 尾部出现 usage 字段时触发）
     * @param callback 回调函数，参数为 (inputTokens, outputTokens)
     */
    public void setOnUsageTokens(BiConsumer<Long, Long> callback) {
        this.onUsageTokens = callback;
    }

    /**
     * 获取累积的思考内容（来自 reasoning_content 字段）
     * @return 思考内容字符串
     */
    public String getThoughtContent() {
        return thoughtContent.toString();
    }

    /**
     * 获取本次流式响应中解析出的原生函数调用列表（跨 delta 累加完成后的最终结果）。
     * 注意：仅当流结束、finalizeNativeToolCalls() 已调用后才完整；流中途调用可能为空。
     * @return 不可变的 NativeToolCall 列表
     */
    public List<NativeToolCall> getNativeToolCalls() {
        return nativeToolCalls;
    }

    /**
     * 思考结束回调是否已触发
     */
    public boolean hasReasoningCompleteFired() {
        return reasoningCompleteFired;
    }

    /**
     * 本次流是否因"思考内容超预算（疑似循环思考）"被主动中断
     * @return true 表示模型长时间只输出思考内容未出正文，流已被中断
     */
    public boolean isReasoningBudgetExceeded() {
        return reasoningBudgetExceeded;
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
            onReasoningCallback = null;
            onReasoningCompleteCallback = null;
            buffer.setLength(0);  // 清空缓冲
            thoughtContent.setLength(0);  // 清空思考内容
            reasoningStartTime = -1;
            reasoningJustCompleted = false;
            reasoningCompleteFired = false;
            toolCallDetected = false;
            nativeToolAccum.clear();
            nativeToolCalls = List.of();

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
        nativeToolAccum.clear();
        nativeToolCalls = List.of();
        StringBuilder fullText = new StringBuilder();
        StringBuilder nonSseFallback = new StringBuilder();  // 非SSE回退缓冲
        // 流开始时间戳：用于"总时长预算"——即使模型完全不吐 reasoning（连接挂起/无数据），
        // 只要超时仍未出正文也中断，让上层走自动重试链而非等 HTTP 绝对超时
        long streamStartTime = System.currentTimeMillis();

        // 看门狗共享状态：只有真实模型数据（data: 行）才重置计时，
        // SSE 心跳注释行（如 ": keep-alive"）仅保活连接，不视为有效进度，
        // 避免上游真正挂起时（心跳不断但无数据）无限等待
        AtomicLong lastReadTime = new AtomicLong(System.currentTimeMillis());

        // 包装 InputStream 以支持读取超时和取消检查
        InputStream timeoutIn = createTimeoutInputStream(response.body(), lastReadTime);

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

                    // SSE 注释行（如 FancyConsole 的心跳 ": keep-alive"）：跳过，
                    // 既不污染 nonSseFallback，也不计入看门狗进度
                    if (line.startsWith(":")) {
                        continue;
                    }

                    if (line.startsWith("data:")) {
                        // 真实模型数据到达 → 重置看门狗计时
                        lastReadTime.set(System.currentTimeMillis());
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

                            // 思考循环检测：模型长时间只输出 reasoning_content（内心戏）不出正文，
                            // 或连接挂起（既不吐思考也不吐正文）。SSE 连接可能一直有数据，看门狗不会触发；
                            // 这里按思考内容量/时长（含无 reasoning 的挂起）主动中断，
                            // 避免上游"思考循环/挂起"把请求拖到超时/无输出（日志表现为长时间无响应最后返回空）。
                            if (!reasoningBudgetExceeded && fullText.length() == 0
                                    && !reasoningCompleteFired
                                    && (textChunk == null || textChunk.isEmpty())) {
                                long elapsedMs = System.currentTimeMillis() - streamStartTime;
                                boolean overChars = thoughtContent.length() > THINKING_BUDGET;
                                boolean overTime = elapsedMs > THINKING_TIME_BUDGET_MS;
                                if (overChars || overTime) {
                                    reasoningBudgetExceeded = true;
                                    logger.warning("[Stream] 思考超预算 (" + thoughtContent.length()
                                            + " 字符/" + (elapsedMs / 1000) + "s) 仍未输出正文，疑似思考循环或挂起，已中断本次流");
                                    break;
                                }
                            }

                            if (textChunk != null && !textChunk.isEmpty()) {
                                // 流起始（尚无任何正文）时裁掉正文前的空白占位。
                                // 部分模型（如 FancyConsole default → agnes-2.5-flash）正文前会先输出
                                // \n 或 \n\n 等空白 delta，若原样进入 buffer，CLIManager 按行切分后
                                // 会在玩家聊天框产生 "◆ " 空行 + 缩进正文（正文开始后的段落空行不受影响）。
                                if (fullText.length() == 0) {
                                    textChunk = ltrim(textChunk);
                                    if (textChunk.isEmpty()) {
                                        continue; // 前导纯空白 chunk：丢弃，不进入 buffer/fullText
                                    }
                                }
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
                        // 与流式路径一致：非SSE回退同样裁掉正文前导空白
                        fallbackText = ltrim(fallbackText);
                        if (!fallbackText.isEmpty()) {
                            fullText.append(fallbackText);
                            buffer.append(fallbackText);
                        }
                    }
                } catch (Exception e) {
                    logger.warning("[Stream] 非SSE回退解析失败: " + e.getMessage());
                }
            }
            
            flushRemainingBuffer();

            // 流结束时固化为原生函数调用列表（onComplete 回调前）
            finalizeNativeToolCalls();

            // 流结束时统一触发一次 token 用量回调（最后一次 usage 的累计值）
            if (usageSeen && onUsageTokens != null && !isCancelled.get()) {
                try {
                    onUsageTokens.accept(pendingUsageInput, pendingUsageOutput);
                } catch (Exception usageCallbackError) {
                    logger.warning("[Stream] token 用量回调异常: " + usageCallbackError.getMessage());
                }
                // 上下文缓存命中日志（每次请求只打一条，便于观察缓存命中率变化）
                if (pendingUsageCacheHit > 0 || pendingUsageCacheMiss > 0) {
                    long total = pendingUsageCacheHit + pendingUsageCacheMiss;
                    long pct = total > 0 ? pendingUsageCacheHit * 100 / total : 0;
                    logger.info("[Cache] 本次请求 prompt=" + pendingUsageInput
                        + " 缓存命中=" + pendingUsageCacheHit + " (" + pct + "%) 未命中=" + pendingUsageCacheMiss);
                }
            }

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
            // 取消触发的读取中断（看门狗关闭底层流导致 readLine 抛出）属正常流程，不视为错误：
            // 优雅返回已累积的文本，避免上游调用方把“用户取消”误判为错误/超时并展示错误消息
            if (isCancelled.get()) {
                logger.info("[Stream] 流式读取因取消而终止");
                return fullText.toString();
            }

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
     * 处理非流式完成的文本（用于 gpt-oss 等不支持流式的模型）
     * 将完整文本通过回调机制传递给 UI，触发完成回调
     * @param fullText 完整的响应文本
     * @return 原始文本
     */
    public String feedCompletedText(String fullText) {
        if (fullText != null && !fullText.isEmpty() && !isCancelled.get() && onChunkCallback != null) {
            try {
                onChunkCallback.accept(fullText);
            } catch (Exception e) {
                logger.warning("[Stream] 非流式文本回调异常: " + e.getMessage());
            }
        }
        // 触发完成回调
        if (!isCancelled.get() && !errorOccurred && onCompleteCallback != null) {
            try {
                onCompleteCallback.accept(fullText != null ? fullText : "");
            } catch (Exception e) {
                errorOccurred = true;
                logger.warning("[Stream] 完成回调异常: " + e.getMessage());
                if (onErrorCallback != null) {
                    try { onErrorCallback.accept(e); } catch (Exception ignored) {}
                }
            }
        }
        return fullText != null ? fullText : "";
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

            // 标记本 chunk 是否包含 reasoning（思考）内容或纯控制信息
            // （finish_reason / usage / tool_calls），若是则无需在末尾打印"无法提取文本"的调试日志
            boolean hasReasoningInChunk = false;
            boolean hasControlData = false;

            // 检测 SSE usage 字段（API 返回的真实 token 消耗）。
            // 某些模型会在每个 chunk 都附带 usage（累计值），只暂存最后一次，流结束时统一触发一次
            if (json.has("usage") && json.get("usage").isJsonObject()) {
                hasControlData = true; // 纯 usage 统计 chunk，由上方代码消费，不是"无法提取"的异常
                JsonObject usage = json.getAsJsonObject("usage");
                long pt = usage.has("prompt_tokens") ? usage.get("prompt_tokens").getAsLong() : 0;
                long ct = usage.has("completion_tokens") ? usage.get("completion_tokens").getAsLong() : 0;
                if (pt > 0 || ct > 0) {
                    pendingUsageInput = pt;
                    pendingUsageOutput = ct;
                    // 上下文缓存命中统计（DeepSeek 等返回 prompt_cache_hit_tokens / prompt_cache_miss_tokens）
                    if (usage.has("prompt_cache_hit_tokens")) {
                        pendingUsageCacheHit = usage.get("prompt_cache_hit_tokens").getAsLong();
                    }
                    if (usage.has("prompt_cache_miss_tokens")) {
                        pendingUsageCacheMiss = usage.get("prompt_cache_miss_tokens").getAsLong();
                    }
                    usageSeen = true;
                }
            }

            // 1. 尝试解析 OpenAI 格式 (choices 数组)
            if (json.has("choices") && json.get("choices").isJsonArray()) {
                var choices = json.getAsJsonArray("choices");
                if (choices.size() > 0) {
                    var choice = choices.get(0).getAsJsonObject();
                    // 流结束标记（finish_reason）chunk：无文本，不打印"无法提取"
                    if (choice.has("finish_reason") && !choice.get("finish_reason").isJsonNull()) {
                        hasControlData = true;
                    }
                    if (choice.has("delta") && choice.get("delta").isJsonObject()) {
                        var delta = choice.getAsJsonObject("delta");
                        // 原生函数调用：tool_calls 与 content 可能同框出现，必须在 return 前处理
                        handleDeltaToolCalls(delta);
                        // 纯 tool_calls 增量由 handleDeltaToolCalls 消费，不是"无法提取"的异常
                        if (delta.has("tool_calls") && !delta.get("tool_calls").isJsonNull()) {
                            hasControlData = true;
                        }
                        if (delta.has("content") && !delta.get("content").isJsonNull()) {
                            // 首次从 reasoning 切换到 content，标记思考结束
                            if (!reasoningCompleteFired && !reasoningJustCompleted && reasoningStartTime != -1 && thoughtContent.length() > 0) {
                                reasoningJustCompleted = true;
                            }
                            return delta.get("content").getAsString();
                        }
                        // 捕获思考模型的 reasoning_content（DeepSeek R1, OpenAI o1/o3 等）
                        boolean capturedReasoning = false;
                        if (delta.has("reasoning_content") && !delta.get("reasoning_content").isJsonNull()) {
                            String rc = delta.get("reasoning_content").getAsString();
                            if (!rc.isEmpty()) {
                                hasReasoningInChunk = true;
                                capturedReasoning = true;
                                // 第一个非空 reasoning token → 开始计时
                                if (reasoningStartTime == -1) {
                                    reasoningStartTime = System.currentTimeMillis();
                                }
                                thoughtContent.append(rc);
                                if (onReasoningCallback != null) {
                                    try { onReasoningCallback.accept(rc); } catch (Exception ignored) {}
                                }
                            }
                        }
                        // 捕获 Gemma 等模型的 reasoning 字段（CloudFlare Workers AI 兼容格式）
                        // 仅当 reasoning_content 未命中时使用，避免同一 chunk 双字段重复累积
                        if (!capturedReasoning && delta.has("reasoning") && !delta.get("reasoning").isJsonNull()) {
                            String rc = delta.get("reasoning").getAsString();
                            if (!rc.isEmpty()) {
                                hasReasoningInChunk = true;
                                if (reasoningStartTime == -1) {
                                    reasoningStartTime = System.currentTimeMillis();
                                }
                                thoughtContent.append(rc);
                                if (onReasoningCallback != null) {
                                    try { onReasoningCallback.accept(rc); } catch (Exception ignored) {}
                                }
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
                                if (onReasoningCallback != null) {
                                    try { onReasoningCallback.accept(rc); } catch (Exception ignored) {}
                                }
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
                // 原生函数调用（防御性）：gpt-oss 当前走非流式，此分支为未来流式 Responses 预留
                if ("response.output_item.added".equals(type)) {
                    hasControlData = true; // 控制事件，由下方逻辑消费，不是"无法提取"的异常
                    if (json.has("data") && json.get("data").isJsonObject()) {
                        JsonObject innerData = json.getAsJsonObject("data");
                        JsonObject item = innerData.has("item") && innerData.get("item").isJsonObject()
                                ? innerData.getAsJsonObject("item") : null;
                        if (item != null && item.has("type") && "function_call".equals(item.get("type").getAsString())) {
                            String name = item.has("name") && !item.get("name").isJsonNull() ? item.get("name").getAsString() : null;
                            String callId = item.has("call_id") && !item.get("call_id").isJsonNull() ? item.get("call_id").getAsString() : null;
                            String args = item.has("arguments") && !item.get("arguments").isJsonNull() ? item.get("arguments").getAsString() : "";
                            int idx = nativeToolAccum.size();
                            nativeToolAccum.put("idx_" + idx, new ToolCallAccum(idx, callId, name, new StringBuilder(args)));
                        }
                    }
                    return null;
                }
                if ("response.function_call_arguments.delta".equals(type)) {
                    hasControlData = true; // 工具调用参数增量，由下方逻辑消费
                    if (json.has("data") && json.get("data").isJsonObject()) {
                        JsonObject innerData = json.getAsJsonObject("data");
                        if (innerData.has("delta") && !innerData.get("delta").isJsonNull()) {
                            String frag = innerData.get("delta").getAsString();
                            // 按 output_index 归并到对应 call（通常只有一个并行 call），key 与 added 分支一致
                            int outIdx = innerData.has("output_index") && !innerData.get("output_index").isJsonNull()
                                    ? innerData.get("output_index").getAsInt() : 0;
                            String key = "idx_" + outIdx;
                            if (nativeToolAccum.containsKey(key)) {
                                nativeToolAccum.get(key).arguments().append(frag);
                            } else {
                                nativeToolAccum.put(key, new ToolCallAccum(outIdx, null, null, new StringBuilder(frag)));
                            }
                        }
                    }
                    return null;
                }
            }

            // 3. 尝试解析 CloudFlare Responses API 非流式格式
            // {"output":[{"type":"message","content":[{"type":"output_text","text":"..."}]}]}
            if (json.has("output") && json.get("output").isJsonArray()) {
                JsonArray output = json.getAsJsonArray("output");
                for (int i = 0; i < output.size(); i++) {
                    JsonObject item = output.get(i).getAsJsonObject();
                    String itemType = item.has("type") ? item.get("type").getAsString() : "";
                    // 提取 reasoning 内容
                    if ("reasoning".equals(itemType) && !hasReasoningInChunk) {
                        hasReasoningInChunk = true;
                        StringBuilder rcBuilder = new StringBuilder();
                        // 尝试从 summary 字段解析（可能是数组或字符串）
                        if (item.has("summary")) {
                            JsonElement summaryEl = item.get("summary");
                            if (summaryEl.isJsonArray()) {
                                JsonArray summaries = summaryEl.getAsJsonArray();
                                for (int j = 0; j < summaries.size(); j++) {
                                    JsonObject s = summaries.get(j).getAsJsonObject();
                                    if (s.has("text") && !s.get("text").isJsonNull()) {
                                        String text = s.get("text").getAsString();
                                        if (!text.isEmpty()) {
                                            if (rcBuilder.length() > 0) rcBuilder.append("\n");
                                            rcBuilder.append(text);
                                        }
                                    }
                                }
                            } else if (summaryEl.isJsonPrimitive()) {
                                String text = summaryEl.getAsString();
                                if (!text.isEmpty()) {
                                    rcBuilder.append(text);
                                }
                            }
                        }
                        // 如果 summary 没有内容，尝试从 content 数组解析
                        if (rcBuilder.length() == 0 && item.has("content") && item.get("content").isJsonArray()) {
                            JsonArray reasoningContents = item.getAsJsonArray("content");
                            for (int j = 0; j < reasoningContents.size(); j++) {
                                JsonObject c = reasoningContents.get(j).getAsJsonObject();
                                String ct = c.has("type") ? c.get("type").getAsString() : "";
                                if (("reasoning_text".equals(ct) || "text".equals(ct)) && c.has("text") && !c.get("text").isJsonNull()) {
                                    String text = c.get("text").getAsString();
                                    if (!text.isEmpty()) {
                                        if (rcBuilder.length() > 0) rcBuilder.append("\n");
                                        rcBuilder.append(text);
                                    }
                                }
                            }
                        }
                        if (rcBuilder.length() > 0) {
                            if (reasoningStartTime == -1) {
                                reasoningStartTime = System.currentTimeMillis();
                            }
                            thoughtContent.append(rcBuilder);
                            if (onReasoningCallback != null) {
                                try { onReasoningCallback.accept(rcBuilder.toString()); } catch (Exception ignored) {}
                            }
                        }
                    }
                    // 提取消息内容
                    if ("message".equals(itemType) && item.has("content") && item.get("content").isJsonArray()) {
                        JsonArray contents = item.getAsJsonArray("content");
                        for (int j = 0; j < contents.size(); j++) {
                            JsonObject contentObj = contents.get(j).getAsJsonObject();
                            String contentType = contentObj.has("type") ? contentObj.get("type").getAsString() : "";
                            if ("output_text".equals(contentType) && !contentObj.get("text").isJsonNull()) {
                                String text = contentObj.get("text").getAsString();
                                if (text != null && !text.isEmpty()) {
                                    // 首次从 reasoning 切换到 content，标记思考结束
                                    if (!reasoningCompleteFired && !reasoningJustCompleted && reasoningStartTime != -1 && thoughtContent.length() > 0) {
                                        reasoningJustCompleted = true;
                                    }
                                    return text;
                                }
                            }
                        }
                    }
                }
            }

            // 4. 尝试解析 CloudFlare 原生 response 格式
            if (json.has("response") && !json.get("response").isJsonNull()) {
                return json.get("response").getAsString();
            }

            // 5. 尝试解析通用 content 格式
            if (json.has("content") && !json.get("content").isJsonNull()) {
                return json.get("content").getAsString();
            }

            // 6. 尝试解析通用 text 格式
            if (json.has("text") && !json.get("text").isJsonNull()) {
                return json.get("text").getAsString();
            }

            // 如果到这里，可能是其他格式的 SSE 数据（如 [DONE] 标记或控制信息）
            // reasoning 内容/控制信息已在前面捕获，跳过日志避免噪音
            if (plugin.getConfigManager().isDebug() && !hasReasoningInChunk && !hasControlData) {
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
     * 解析 OpenAI 流式 delta 中的 tool_calls 数组，按 id 归并累加。
     * CF gemma 并行调用共享同一 index 且参数分片不带 id，因此不能按 index 归并。
     * 规则：带 id 的 delta 按 id 归并并设为当前活跃调用；无 id 的参数分片
     * 顺序路由到当前活跃调用（CF 按序生成调用，一个流完才流下一个）。
     * 无 id 且带 name 的 delta（测试/非标准流）退化为按 index 归并。
     */
    private void handleDeltaToolCalls(JsonObject delta) {
        if (!delta.has("tool_calls") || !delta.get("tool_calls").isJsonArray()) {
            return;
        }
        JsonArray toolCalls = delta.getAsJsonArray("tool_calls");
        for (int i = 0; i < toolCalls.size(); i++) {
            JsonElement el = toolCalls.get(i);
            if (!el.isJsonObject()) continue;
            JsonObject tc = el.getAsJsonObject();
            String id = tc.has("id") && !tc.get("id").isJsonNull() ? tc.get("id").getAsString() : null;
            int index = tc.has("index") ? tc.get("index").getAsInt() : 0;
            String name = null;
            String arguments = null;
            if (tc.has("function") && tc.get("function").isJsonObject()) {
                JsonObject fn = tc.getAsJsonObject("function");
                if (fn.has("name") && !fn.get("name").isJsonNull()) {
                    name = fn.get("name").getAsString();
                }
                if (fn.has("arguments") && !fn.get("arguments").isJsonNull()) {
                    arguments = fn.get("arguments").getAsString();
                }
            }

            if (id != null) {
                // 标准流：按 id 归并，并设为当前活跃调用（CF 后续无 id 分片路由到这里）
                ToolCallAccum acc = nativeToolAccum.computeIfAbsent(id,
                        k -> new ToolCallAccum(index, id, null, new StringBuilder()));
                if (name != null && acc.name() == null) {
                    acc = new ToolCallAccum(index, id, name, acc.arguments());
                    nativeToolAccum.put(id, acc);
                }
                if (arguments != null) {
                    acc.arguments().append(arguments);
                }
                currentToolCallKey = id;
            } else if (name != null) {
                // 无 id 但带 name：退化为按 index 归并（测试/非标准流）
                String key = "sse_" + index;
                ToolCallAccum acc = nativeToolAccum.computeIfAbsent(key,
                        k -> new ToolCallAccum(index, null, null, new StringBuilder()));
                if (acc.name() == null) {
                    acc = new ToolCallAccum(index, null, name, acc.arguments());
                    nativeToolAccum.put(key, acc);
                }
                if (arguments != null) {
                    acc.arguments().append(arguments);
                }
                currentToolCallKey = key;
            } else if (arguments != null) {
                // 无 id 无 name：CF 参数分片，顺序路由到当前活跃调用
                if (currentToolCallKey != null && nativeToolAccum.containsKey(currentToolCallKey)) {
                    nativeToolAccum.get(currentToolCallKey).arguments().append(arguments);
                } else {
                    String key = "sse_" + index;
                    ToolCallAccum acc = nativeToolAccum.computeIfAbsent(key,
                            k -> new ToolCallAccum(index, null, null, new StringBuilder()));
                    acc.arguments().append(arguments);
                    currentToolCallKey = key;
                }
            }
        }
    }

    /**
     * 流结束时把累加器固化为 NativeToolCall 列表。
     * arguments 片段若解析失败（无效 JSON）保留原始片段，字符串型工具能容忍。
     */
    private void finalizeNativeToolCalls() {
        List<NativeToolCall> calls = new ArrayList<>();
        for (ToolCallAccum acc : nativeToolAccum.values()) {
            if (acc.name() == null || acc.name().isEmpty()) {
                continue;
            }
            calls.add(new NativeToolCall(acc.id(), acc.name(), acc.arguments().toString()));
        }
        nativeToolCalls = List.copyOf(calls);
    }

    /**
     * 计算文本在Minecraft聊天框中的视觉宽度
     * 中文字符/全角字符权重为 1.7，ASCII/半角字符权重为 1.1
     * 计数前剥离 ** 加粗标记
     * @param text 要计算的文本
     * @return 视觉宽度值
     */
    private double getVisualWidth(CharSequence text) {
        // 剥离 ** 加粗标记，不计入视觉宽度
        String cleaned = text.toString().replace("**", "");
        double width = 0;
        for (int i = 0; i < cleaned.length(); i++) {
            char c = cleaned.charAt(i);
            if (isFullWidth(c)) {
                width += 1.7;
            } else {
                width += 1.1;
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
     * 去掉字符串前导空白字符（空格、\r、\n、\t 等）
     */
    private static String ltrim(String text) {
        int start = 0;
        while (start < text.length() && Character.isWhitespace(text.charAt(start))) {
            start++;
        }
        return text.substring(start);
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
     *
     * 注意：不再依赖 InputStream.available() 轮询判断“是否有数据”。
     * java.net.http.HttpClient 的响应流在 HTTP/1.1 chunked 传输下 available()
     * 可能持续返回 0（即使数据已到达内部缓冲），导致“数据明明在传却被误判为无数据”而提前超时。
     *
     * 改为“阻塞读 + 看门狗”方案：
     *  - read() 直接阻塞在底层流上，数据到达即返回；
     *  - 看门狗线程监控距最后一次“真实数据”读取的时间（由调用方在 data: 行时更新 lastReadTime），
     *    超过 readTimeoutSeconds 仍无真实数据时主动关闭底层流解除阻塞，并抛出明确的超时异常；
     *    心跳注释行不算有效进度，保证上游真挂起时仍能超时。
     *
     * @param in 原始输入流
     * @param lastReadTime 共享的最后一次真实数据读取时间戳（由行处理循环更新）
     * @return 带超时控制的输入流
     */
    private InputStream createTimeoutInputStream(InputStream in, AtomicLong lastReadTime) {
        AtomicBoolean timedOut = new AtomicBoolean(false);
        AtomicBoolean closed = new AtomicBoolean(false);

        Thread watchdog = new Thread(() -> {
            while (!closed.get()) {
                if (isCancelled.get()) {
                    // 取消时关闭流以解除阻塞中的 read()
                    try { in.close(); } catch (IOException ignored) {}
                    return;
                }
                long idle = System.currentTimeMillis() - lastReadTime.get();
                if (idle >= readTimeoutSeconds * 1000L) {
                    timedOut.set(true);
                    try { in.close(); } catch (IOException ignored) {}
                    return;
                }
                try {
                    Thread.sleep(READ_POLL_INTERVAL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }, "FancyHelper-StreamWatchdog");
        watchdog.setDaemon(true);
        watchdog.start();

        return new InputStream() {

            @Override
            public int read() throws IOException {
                int b;
                try {
                    b = in.read();
                } catch (IOException e) {
                    throw translateReadError(e, timedOut);
                }
                return b;
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                int n;
                try {
                    n = in.read(b, off, len);
                } catch (IOException e) {
                    throw translateReadError(e, timedOut);
                }
                return n;
            }

            @Override
            public int available() throws IOException {
                return in.available();
            }

            @Override
            public void close() throws IOException {
                closed.set(true);
                in.close();
            }
        };
    }

    /**
     * 将底层流读取异常转换为明确的超时/取消提示
     */
    private IOException translateReadError(IOException e, AtomicBoolean timedOut) {
        if (timedOut.get()) {
            return new IOException("流式读取超时 (" + readTimeoutSeconds + " 秒无数据)");
        }
        if (isCancelled.get()) {
            return new IOException("流式读取已取消");
        }
        return e;
    }
}
