package org.YanPl.model;

import java.util.ArrayList;
import java.util.List;

public class DialogueSession {
    /**
     * 对话模式
     */
    public enum Mode {
        NORMAL, YOLO
    }

    /**
     * DialogueSession 保存简短的对话历史与最近活动时间，用于与 AI 交互时传递上下文。
     */
    private final List<Message> history = new ArrayList<>();
    private long lastActivityTime;
    private long startTime;
    private int toolSuccessCount = 0;
    private int toolFailureCount = 0;
    private int thoughtTokens = 0;
    private Mode mode = Mode.NORMAL;

    public DialogueSession() {
        this.lastActivityTime = System.currentTimeMillis();
        this.startTime = System.currentTimeMillis();
    }

    public void addMessage(String role, String content) {
        // 添加消息并更新活动时间；限制历史长度以节省 token
        history.add(new Message(role, content));
        this.lastActivityTime = System.currentTimeMillis();
        
        if (history.size() > 20) {
            // 移除最早的两条，保持历史在一个较小范围
            history.remove(0);
            history.remove(0);
        }
    }

    public List<Message> getHistory() {
        return history;
    }

    public int getEstimatedTokens() {
        // 粗略估算 token：每 4 个字符为 1 token（近似值）
        int chars = 0;
        for (Message msg : history) {
            chars += msg.getContent().length();
        }
        return chars / 4;
    }

    public long getLastActivityTime() {
        return lastActivityTime;
    }

    public long getStartTime() {
        return startTime;
    }

    public void updateActivity() {
        this.lastActivityTime = System.currentTimeMillis();
    }

    public void clearHistory() {
        history.clear();
    }

    public int getToolSuccessCount() {
        return toolSuccessCount;
    }

    public int getToolFailureCount() {
        return toolFailureCount;
    }

    public int getThoughtTokens() {
        return thoughtTokens;
    }

    public void addThoughtTokens(int tokens) {
        thoughtTokens += tokens;
    }

    public void incrementToolSuccess() {
        toolSuccessCount++;
    }

    public void incrementToolFailure() {
        toolFailureCount++;
    }

    public void removeLastMessage() {
        if (!history.isEmpty()) {
            history.remove(history.size() - 1);
        }
    }

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    public static class Message {
        private final String role;
        private final String content;

        public Message(String role, String content) {
            this.role = role != null ? role : "user";
            this.content = content != null ? content : "";
        }

        public String getRole() {
            return role;
        }

        public String getContent() {
            return content;
        }
    }
}
