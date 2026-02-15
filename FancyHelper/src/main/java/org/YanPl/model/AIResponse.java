package org.YanPl.model;

/**
 * 封装 AI 响应结果，包含正文和思考过程。
 */
public class AIResponse {
    private final String content;
    private final String thought;

    public AIResponse(String content, String thought) {
        this.content = content;
        this.thought = thought;
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
}
