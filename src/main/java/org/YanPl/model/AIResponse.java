package org.YanPl.model;

/**
 * 封装 AI 响应结果，包含正文和思考过程。
 */
public class AIResponse {
    private final String content;
    private final String thought;
    private final long promptTokens;
    private final long completionTokens;
    private final boolean truncated;

    public AIResponse(String content, String thought) {
        this(content, thought, 0, 0, false);
    }

    public AIResponse(String content, String thought, long promptTokens, long completionTokens) {
        this(content, thought, promptTokens, completionTokens, false);
    }

    public AIResponse(String content, String thought, long promptTokens, long completionTokens, boolean truncated) {
        this.content = content;
        this.thought = thought;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.truncated = truncated;
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
}
