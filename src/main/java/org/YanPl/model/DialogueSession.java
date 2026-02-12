package org.YanPl.model;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
    private final List<String> toolCallHistory = new ArrayList<>();
    private long lastActivityTime;
    private long startTime;
    private int toolSuccessCount = 0;
    private int toolFailureCount = 0;
    private int currentChainToolCount = 0;
    private int thoughtTokens = 0;
    private long totalThinkingTimeMs = 0;
    private boolean antiLoopExempted = false;
    private Mode mode = Mode.NORMAL;
    private String lastThought = null;
    private long lastThinkingTimeMs = 0;
    private long nextMessageId = 0;
    private final Map<Long, ThoughtSnapshot> thoughtSnapshots = new LinkedHashMap<Long, ThoughtSnapshot>(64, 0.75f, false) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, ThoughtSnapshot> eldest) {
            return size() > 50;
        }
    };

    /**
     * 编码注册表（单例）
     */
    private static final EncodingRegistry REGISTRY = Encodings.newDefaultEncodingRegistry();

    /**
     * 编码缓存，避免重复创建
     */
    private static final Map<String, Encoding> ENCODING_CACHE = new ConcurrentHashMap<>();

    /**
     * 默认编码 (cl100k_base，用于 GPT-4 和 GPT-3.5-turbo)
     */
    private static final Encoding DEFAULT_ENCODING = REGISTRY.getEncoding(EncodingType.CL100K_BASE);

    public DialogueSession() {
        this.lastActivityTime = System.currentTimeMillis();
        this.startTime = System.currentTimeMillis();
    }

    /**
     * 根据模型名称获取对应的编码
     */
    private static Encoding getEncodingForModel(String modelName) {
        if (modelName == null || modelName.isEmpty()) {
            return DEFAULT_ENCODING;
        }

        // 使用缓存
        return ENCODING_CACHE.computeIfAbsent(modelName, name -> {
            // CloudFlare AI 模型映射到对应的编码
            String lowerName = name.toLowerCase();

            // OpenAI GPT 系列使用 cl100k_base
            if (lowerName.contains("gpt") || lowerName.contains("openai")) {
                return REGISTRY.getEncoding(EncodingType.CL100K_BASE);
            }

            // 其他模型使用默认编码（回退方案）
            // jtokkit 主要支持 OpenAI 的编码，对于其他模型（如 LLaMA）可能不完全匹配
            // 这里使用 cl100k_base 作为通用近似
            return DEFAULT_ENCODING;
        });
    }

    public void addMessage(String role, String content) {
        addMessage(role, content, null);
    }

    public void addMessage(String role, String content, String thought) {
        // 添加消息并更新活动时间；限制历史长度以节省 token
        long messageId = nextMessageId++;
        long thinkingTimeMs = thought != null && !thought.isEmpty() ? lastThinkingTimeMs : 0;
        history.add(new Message(messageId, role, content, thought, thinkingTimeMs));
        this.lastActivityTime = System.currentTimeMillis();

        if (thought != null && !thought.isEmpty()) {
            synchronized (thoughtSnapshots) {
                thoughtSnapshots.put(messageId, new ThoughtSnapshot(thought, thinkingTimeMs));
            }
        }
        
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
        return getEstimatedTokens(null);
    }

    public int getEstimatedTokens(String modelName) {
        // 使用 jtokkit 进行精确的 token 计算
        Encoding encoding = getEncodingForModel(modelName);
        int totalTokens = 0;
        for (Message msg : history) {
            totalTokens += encoding.countTokens(msg.getContent());
        }
        return totalTokens;
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
        toolCallHistory.clear();
        synchronized (thoughtSnapshots) {
            thoughtSnapshots.clear();
        }
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

    /**
     * 获取总思考时长 (ms)
     */
    public long getTotalThinkingTimeMs() {
        return totalThinkingTimeMs;
    }

    /**
     * 增加思考时长
     */
    public void addThinkingTime(long ms) {
        totalThinkingTimeMs += ms;
        lastThinkingTimeMs = ms;
    }

    /**
     * 获取最近一次思考时长 (ms)
     */
    public long getLastThinkingTimeMs() {
        return lastThinkingTimeMs;
    }

    /**
     * 计算字符串的 token 数量（使用默认编码）
     */
    public static int calculateTokens(String text) {
        return calculateTokens(text, null);
    }

    /**
     * 计算字符串的 token 数量（根据模型名称选择编码）
     */
    public static int calculateTokens(String text, String modelName) {
        Encoding encoding = getEncodingForModel(modelName);
        return encoding.countTokens(text);
    }

    public void incrementToolFailure() {
        toolFailureCount++;
        currentChainToolCount++;
    }

    public void incrementToolSuccess() {
        toolSuccessCount++;
        currentChainToolCount++;
    }

    public void resetToolChain() {
        currentChainToolCount = 0;
    }

    public int getCurrentChainToolCount() {
        return currentChainToolCount;
    }

    /**
     * 设置本会话是否豁免防循环检测
     * @param exempted 是否豁免
     */
    public void setAntiLoopExempted(boolean exempted) {
        this.antiLoopExempted = exempted;
    }

    /**
     * 获取本会话是否豁免防循环检测
     * @return 是否豁免
     */
    public boolean isAntiLoopExempted() {
        return antiLoopExempted;
    }

    /**
     * 添加工具调用到历史记录
     * @param toolCall 工具调用内容
     */
    public void addToolCall(String toolCall) {
        toolCallHistory.add(toolCall);
        // 仅保留最近的 10 次工具调用
        if (toolCallHistory.size() > 10) {
            toolCallHistory.remove(0);
        }
    }

    /**
     * 获取工具调用历史记录
     * @return 工具调用历史
     */
    public List<String> getToolCallHistory() {
        return new ArrayList<>(toolCallHistory);
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

    public String getLastThought() {
        return lastThought;
    }

    public void setLastThought(String lastThought) {
        this.lastThought = lastThought;
    }

    /**
     * 获取某条消息对应的思考快照（用于稳定地回放对应 Thought）。
     */
    public ThoughtSnapshot getThoughtSnapshot(long messageId) {
        synchronized (thoughtSnapshots) {
            return thoughtSnapshots.get(messageId);
        }
    }

    /**
     * 在当前历史中按 messageId 查找消息（历史可能会因裁剪而丢失旧消息）。
     */
    public Message findMessageById(long messageId) {
        for (Message message : history) {
            if (message.getId() == messageId) {
                return message;
            }
        }
        return null;
    }

    public static class ThoughtSnapshot {
        private final String thought;
        private final long thinkingTimeMs;

        public ThoughtSnapshot(String thought, long thinkingTimeMs) {
            this.thought = thought;
            this.thinkingTimeMs = thinkingTimeMs;
        }

        public String getThought() {
            return thought;
        }

        public long getThinkingTimeMs() {
            return thinkingTimeMs;
        }
    }

    public static class Message {
        private final long id;
        private final String role;
        private final String content;
        private final String thought;
        private final long thinkingTimeMs;

        public Message(String role, String content) {
            this(-1, role, content, null, 0);
        }

        public Message(String role, String content, String thought) {
            this(-1, role, content, thought, 0);
        }

        public Message(long id, String role, String content, String thought, long thinkingTimeMs) {
            this.id = id;
            this.role = role != null ? role : "user";
            this.content = content != null ? content : "";
            this.thought = thought;
            this.thinkingTimeMs = thinkingTimeMs;
        }

        public long getId() {
            return id;
        }

        public String getRole() {
            return role;
        }

        public String getContent() {
            return content;
        }

        public String getThought() {
            return thought;
        }

        public long getThinkingTimeMs() {
            return thinkingTimeMs;
        }

        public boolean hasThought() {
            return thought != null && !thought.isEmpty();
        }
    }
}
