package org.YanPl.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 持久化会话记录，用于保存到 sessions/{playerUUID}.json
 */
public class SessionRecord {
    private String sessionUUID;
    private String title;
    private long timestamp;
    private long totalInputTokens;
    private long totalOutputTokens;
    private String mode;
    private List<Map<String, Object>> messages;
    private List<String> toolCalls;

    public SessionRecord() {
    }

    /**
     * 从运行时会话构建持久化记录
     */
    public static SessionRecord fromSession(DialogueSession session, String sessionUUID) {
        SessionRecord record = new SessionRecord();
        record.sessionUUID = sessionUUID;
        record.timestamp = System.currentTimeMillis();
        record.totalInputTokens = session.getTotalInputTokens();
        record.totalOutputTokens = session.getTotalOutputTokens();
        record.mode = session.getMode().name();

        // 序列化消息列表
        record.messages = new ArrayList<>();
        for (DialogueSession.Message message : session.getHistory()) {
            Map<String, Object> msgData = new HashMap<>();
            msgData.put("role", message.getRole());
            msgData.put("content", message.getContent());
            if (message.getThought() != null) {
                msgData.put("thought", message.getThought());
            }
            record.messages.add(msgData);
        }

        // 工具调用历史
        record.toolCalls = new ArrayList<>(session.getToolCallHistory());

        return record;
    }

    /**
     * 重建为运行时会话
     */
    public DialogueSession toSession() {
        DialogueSession session = new DialogueSession();
        session.setSessionUUID(sessionUUID);
        session.setMode(DialogueSession.Mode.valueOf(mode != null ? mode : "NORMAL"));

        // 恢复消息
        if (messages != null) {
            for (Map<String, Object> msgData : messages) {
                String role = (String) msgData.getOrDefault("role", "user");
                String content = (String) msgData.getOrDefault("content", "");
                String thought = (String) msgData.get("thought");
                session.addMessage(role, content, thought);
            }
        }

        // 恢复工具调用历史
        if (toolCalls != null) {
            for (String toolCall : toolCalls) {
                session.addToolCall(toolCall);
            }
        }

        return session;
    }

    public String getSessionUUID() {
        return sessionUUID;
    }

    public void setSessionUUID(String sessionUUID) {
        this.sessionUUID = sessionUUID;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public long getTotalInputTokens() {
        return totalInputTokens;
    }

    public void setTotalInputTokens(long totalInputTokens) {
        this.totalInputTokens = totalInputTokens;
    }

    public long getTotalOutputTokens() {
        return totalOutputTokens;
    }

    public void setTotalOutputTokens(long totalOutputTokens) {
        this.totalOutputTokens = totalOutputTokens;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public List<Map<String, Object>> getMessages() {
        return messages;
    }

    public void setMessages(List<Map<String, Object>> messages) {
        this.messages = messages;
    }

    public List<String> getToolCalls() {
        return toolCalls;
    }

    public void setToolCalls(List<String> toolCalls) {
        this.toolCalls = toolCalls;
    }

    public long getTotalTokens() {
        return totalInputTokens + totalOutputTokens;
    }
}
