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
    private List<String> loadedSkillIds;
    private List<String> readFiles;
    private int thoughtTokens;
    private long totalThinkingTimeMs;
    private int toolSuccessCount;
    private int toolFailureCount;
    private long nextMessageId;
    private boolean antiLoopExempted;
    private String lastThought;
    private String lastError;

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

        // 已加载的 Skill ID 列表
        record.loadedSkillIds = new ArrayList<>(session.getLoadedSkillIds());

        // 已读取的文件列表
        record.readFiles = new ArrayList<>(session.getReadFiles());

        // 思考相关统计
        record.thoughtTokens = session.getThoughtTokens();
        record.totalThinkingTimeMs = session.getTotalThinkingTimeMs();

        // 工具调用统计
        record.toolSuccessCount = session.getToolSuccessCount();
        record.toolFailureCount = session.getToolFailureCount();

        // 消息 ID 计数器（用于恢复后保持 ID 连续性）
        record.nextMessageId = session.getNextMessageId();

        // 其他会话状态
        record.antiLoopExempted = session.isAntiLoopExempted();
        record.lastThought = session.getLastThought();
        record.lastError = session.getLastError();

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

        // 恢复已加载的 Skill ID
        if (loadedSkillIds != null) {
            for (String skillId : loadedSkillIds) {
                session.addLoadedSkillId(skillId);
            }
        }

        // 恢复已读取的文件
        if (readFiles != null) {
            for (String file : readFiles) {
                session.addReadFile(file);
            }
        }

        // 恢复 Token 统计
        session.addInputTokens(totalInputTokens);
        session.addOutputTokens(totalOutputTokens);

        // 恢复思考相关统计
        if (thoughtTokens > 0) session.addThoughtTokens(thoughtTokens);
        if (totalThinkingTimeMs > 0) session.addThinkingTime(totalThinkingTimeMs);

        // 恢复工具调用统计
        for (int i = 0; i < toolSuccessCount; i++) session.incrementToolSuccess();
        for (int i = 0; i < toolFailureCount; i++) session.incrementToolFailure();
        // 重置工具链计数（恢复后不应从之前工具链继续）
        session.resetToolChain();

        // 恢复消息 ID 计数器（在 addMessage 之后覆盖，保持连续性）
        if (nextMessageId > 0) {
            session.setNextMessageId(nextMessageId);
        }

        // 恢复其他状态
        session.setAntiLoopExempted(antiLoopExempted);
        if (lastThought != null) session.setLastThought(lastThought);
        if (lastError != null) session.setLastError(lastError);

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
