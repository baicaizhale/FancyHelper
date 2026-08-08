package org.YanPl.model;

import java.util.Collections;
import java.util.List;

/**
 * 封装 AI 响应结果，包含正文和思考过程。
 */
public class AIResponse {
    private final String content;
    private final String thought;
    private final long promptTokens;
    private final long completionTokens;
    private final boolean truncated;
    private final List<NativeToolCall> toolCalls;

    public AIResponse(String content, String thought) {
        this(content, thought, 0, 0, false, null);
    }

    public AIResponse(String content, String thought, long promptTokens, long completionTokens) {
        this(content, thought, promptTokens, completionTokens, false, null);
    }

    public AIResponse(String content, String thought, long promptTokens, long completionTokens, boolean truncated) {
        this(content, thought, promptTokens, completionTokens, truncated, null);
    }

    public AIResponse(String content, String thought, long promptTokens, long completionTokens, boolean truncated,
                      List<NativeToolCall> toolCalls) {
        this.content = content;
        this.thought = thought;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.truncated = truncated;
        this.toolCalls = toolCalls;
    }

    public String getContent() {
        return content;
    }

    public String getThought() {
        return thought;
    }

    public boolean hasThought() {
        return thought != null && !thought.isEmpty();
    }

    public long getPromptTokens() {
        return promptTokens;
    }

    public long getCompletionTokens() {
        return completionTokens;
    }

    public boolean isTruncated() {
        return truncated;
    }

    /** 原生函数调用的工具调用列表；无则为空列表。 */
    public List<NativeToolCall> getToolCalls() {
        return toolCalls == null ? Collections.emptyList() : toolCalls;
    }
}
