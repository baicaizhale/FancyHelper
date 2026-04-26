package org.YanPl.model;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class DialogueSession {

    private final Set<String> loadedSkillIds = new LinkedHashSet<>();
    /**
     * 对话模式
     */
    public enum Mode {
        NORMAL, YOLO, SMART
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
    private long totalInputTokens = 0;
    private long totalOutputTokens = 0;
    private long totalThinkingTimeMs = 0;
    private boolean antiLoopExempted = false;
    private Mode mode = Mode.NORMAL;
    private String lastThought = null;
    private long lastThinkingTimeMs = 0;
    private long nextMessageId = 0;
    private String logFilePath = null;
    private String lastError = null;
    private boolean verboseLogging = false;
    private boolean systemPromptLogged = false;
    private int lastLoggedMessageCount = 0;
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

    /**
     * 添加消息并记录思维链
     *
     * @param role    角色
     * @param content 内容
     * @param thought 思维链
     */
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

    /**
     * 获取日志文件路径
     *
     * @return 日志文件路径
     */
    public String getLogFilePath() {
        return logFilePath;
    }

    /**
     * 设置日志文件路径
     *
     * @param logFilePath 日志文件路径
     */
    public void setLogFilePath(String logFilePath) {
        this.logFilePath = logFilePath;
    }

    /**
     * 追加内容到会话日志文件
     *
     * @param type    日志类型 (如 USER, AI, SYSTEM, TOOL, ERROR)
     * @param content 日志内容
     */
    public synchronized void appendLog(String type, String content) {
        if (logFilePath == null) return;
        
        try {
            String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            StringBuilder sb = new StringBuilder();
            sb.append("[").append(time).append("] [").append(type).append("] ");
            if (content != null && !content.isEmpty()) {
                sb.append(content);
            }
            sb.append("\n");
            
            Files.write(Paths.get(logFilePath), sb.toString().getBytes(StandardCharsets.UTF_8), 
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            // 忽略日志写入错误，避免影响主流程
        }
    }
    
    /**
     * 记录系统提示词（每个会话只记录一次）
     * @param systemPrompt 系统提示词内容
     */
    public synchronized void logSystemPrompt(String systemPrompt) {
        if (logFilePath == null || systemPromptLogged) return;
        
        systemPromptLogged = true;
        
        try {
            String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            StringBuilder sb = new StringBuilder();
            sb.append("[").append(time).append("] [SYSTEM_PROMPT] \n");
            sb.append(systemPrompt);
            sb.append("\n");
            sb.append("─".repeat(80)).append("\n");
            
            Files.write(Paths.get(logFilePath), sb.toString().getBytes(StandardCharsets.UTF_8), 
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            // 忽略日志写入错误
        }
    }
    
    /**
     * 记录 AI 请求内容
     * @param requestContent 请求内容
     */
    public synchronized void logAIRequest(String requestContent) {
        if (logFilePath == null) return;
        
        try {
            String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            StringBuilder sb = new StringBuilder();
            sb.append("[").append(time).append("] [AI_REQUEST] \n");
            sb.append(requestContent);
            sb.append("\n");
            sb.append("─".repeat(80)).append("\n");
            
            Files.write(Paths.get(logFilePath), sb.toString().getBytes(StandardCharsets.UTF_8), 
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            // 忽略日志写入错误
        }
    }
    
    /**
     * 记录 AI 响应内容
     * @param responseContent 响应内容
     */
    public synchronized void logAIResponse(String responseContent) {
        if (logFilePath == null) return;
        
        try {
            String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            StringBuilder sb = new StringBuilder();
            sb.append("[").append(time).append("] [AI_RESPONSE] \n");
            sb.append(responseContent);
            sb.append("\n");
            sb.append("─".repeat(80)).append("\n");
            
            Files.write(Paths.get(logFilePath), sb.toString().getBytes(StandardCharsets.UTF_8), 
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            // 忽略日志写入错误
        }
    }

    public List<Message> getHistory() {
        return history;
    }

    public int getLastLoggedMessageCount() {
        return lastLoggedMessageCount;
    }

    public void setLastLoggedMessageCount(int count) {
        this.lastLoggedMessageCount = count;
    }

    public int getEstimatedTokens() {
        return getEstimatedTokens(null);
    }

    public int getEstimatedTokens(String modelName) {
        // 使用 jtokkit 进行精确的 token 计算
        Encoding encoding = getEncodingForModel(modelName);
        int totalTokens = 0;
        for (Message msg : history) {
            // 每条消息的基础消耗: <|im_start|>{role}\n{content}<|im_end|>\n
            // 约为: tokens(role) + tokens(content) + 3
            totalTokens += encoding.countTokens(msg.getRole());
            totalTokens += encoding.countTokens(msg.getContent());
            totalTokens += 3; 
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

    public long getTotalInputTokens() {
        return totalInputTokens;
    }

    public long getTotalOutputTokens() {
        return totalOutputTokens;
    }

    public void addInputTokens(long tokens) {
        this.totalInputTokens += tokens;
    }

    public void addOutputTokens(long tokens) {
        this.totalOutputTokens += tokens;
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

    /**
     * 压缩上下文，保留最近的消息并将较早的消息压缩成摘要
     * @param keepRecent 保留最近的消息数量
     */
    public void compressContext(int keepRecent) {
        if (history.size() <= keepRecent * 2) {
            return; // 消息数量不足，不需要压缩
        }

        // 保留最近的消息
        List<Message> recentMessages = new ArrayList<>(history.subList(history.size() - keepRecent, history.size()));

        // 压缩较早的消息
        List<Message> oldMessages = history.subList(0, history.size() - keepRecent);
        StringBuilder summaryBuilder = new StringBuilder();
        summaryBuilder.append("[上下文摘要]: ");

        for (Message msg : oldMessages) {
            if (msg.getRole().equals("user")) {
                summaryBuilder.append("用户: ").append(msg.getContent()).append("; ");
            } else if (msg.getRole().equals("assistant")) {
                summaryBuilder.append("助手: ").append(msg.getContent()).append("; ");
            }
        }

        // 创建摘要消息
        String summary = summaryBuilder.toString();
        if (summary.length() > 500) {
            summary = summary.substring(0, 500) + "...";
        }

        // 清空历史并添加摘要和最近的消息
        history.clear();
        loadedSkillIds.clear();
        history.add(new Message("system", summary));
        history.addAll(recentMessages);
    }

    /**
     * 使用AI生成的摘要压缩上下文
     * @param keepRecent 保留最近的消息数量
     * @param aiSummary AI生成的摘要
     */
    public void compressContextWithSummary(int keepRecent, String aiSummary) {
        if (history.size() <= keepRecent * 2) {
            return; // 消息数量不足，不需要压缩
        }

        // 保留最近的消息
        List<Message> recentMessages = new ArrayList<>(history.subList(history.size() - keepRecent, history.size()));

        // 清空历史并添加AI摘要和最近的消息
        history.clear();
        loadedSkillIds.clear();
        
        // 添加AI生成的摘要作为system消息
        String summary = "[上下文摘要]: " + aiSummary;
        history.add(new Message("system", summary));
        
        // 添加保留的最近消息
        history.addAll(recentMessages);
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

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    /**
     * 设置是否启用详细日志记录
     * @param verboseLogging 是否启用详细日志
     */
    public void setVerboseLogging(boolean verboseLogging) {
        this.verboseLogging = verboseLogging;
    }

    /**
     * 获取是否启用详细日志记录
     * @return 是否启用详细日志
     */
    public boolean isVerboseLogging() {
        return verboseLogging;
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

    /**
     * 添加 Skill 上下文到对话中（引用式）
     * 如果已达到上限，自动淘汰最旧的 Skill
     * @param skill Skill 对象
     */
    public boolean addSkillContext(org.YanPl.model.Skill skill) {
        if (skill == null) {
            return false;
        }
        String skillId = skill.getId().toLowerCase();
        if (loadedSkillIds.contains(skillId)) {
            return false;
        }

        // 自动淘汰：达到上限时移除最旧的 Skill
        if (loadedSkillIds.size() >= 5) {
            String oldestId = loadedSkillIds.iterator().next();
            removeSkillContext(oldestId);
        }

        loadedSkillIds.add(skillId);

        // 创建引用式消息，不污染历史
        String skillRef = "[Skill Reference: " + skill.getMetadata().getName() + " v" + skill.getMetadata().getVersion() + "]";
        Message skillMessage = new Message("system", skillRef);
        history.add(skillMessage);

        // 记录到日志
        appendLog("SKILL_CONTEXT", "Loaded skill: " + skill.getId() + " (" + skill.getMetadata().getName() + ")");
        return true;
    }

    /**
     * 获取当前已加载的 Skill ID 集合（不可变视图）
     */
    public Set<String> getLoadedSkillIds() {
        return Collections.unmodifiableSet(loadedSkillIds);
    }

    /**
     * 检查是否已加载某个 Skill
     */
    public boolean hasLoadedSkill(String skillId) {
        return loadedSkillIds.contains(skillId.toLowerCase());
    }

    /**
     * 从对话上下文中移除指定 Skill
     * 同时移除对应的 Skill Reference 消息
     * @param skillId 要移除的 Skill ID
     * @return 是否成功移除
     */
    public boolean removeSkillContext(String skillId) {
        String lowerId = skillId.toLowerCase();
        if (!loadedSkillIds.contains(lowerId)) {
            return false;
        }

        loadedSkillIds.remove(lowerId);

        // 移除对应的 Skill Reference 消息
        String refPrefix = "[Skill Reference: ";
        history.removeIf(msg -> msg.getRole().equals("system")
                && msg.getContent() != null
                && msg.getContent().startsWith(refPrefix)
                && msg.getContent().toLowerCase().contains(lowerId));

        appendLog("SKILL_CONTEXT", "Unloaded skill: " + skillId);
        return true;
    }

    /**
     * 获取 Skill 内容（用于工具调用时动态获取）
     * 需要外部传入 SkillManager
     */
    public String getSkillContent(String skillId, org.YanPl.manager.SkillManager skillManager) {
        if (skillManager == null) {
            return "";
        }
        org.YanPl.model.Skill skill = skillManager.getSkill(skillId);
        if (skill == null) {
            return "";
        }
        return skill.getFormattedContent();
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
