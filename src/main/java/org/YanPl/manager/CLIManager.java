package org.YanPl.manager;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.YanPl.FancyHelper;
import org.YanPl.api.CloudFlareAI;
import org.YanPl.model.AIResponse;
import org.YanPl.model.DialogueSession;
import org.YanPl.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CLI 模式管理器，负责管理玩家的 CLI 状态和对话流
 */
public class CLIManager {
    private final FancyHelper plugin;
    private final CloudFlareAI ai;
    private final PromptManager promptManager;
    private final ToolExecutor toolExecutor;
    private final Set<UUID> activeCLIPayers = new HashSet<>();
    private final Set<UUID> pendingAgreementPlayers = new HashSet<>();
    private final Set<UUID> agreedPlayers = new HashSet<>();
    private final Set<UUID> yoloAgreedPlayers = new HashSet<>();
    private final Set<UUID> yoloModePlayers = new HashSet<>();
    private final Set<UUID> pendingYoloAgreementPlayers = new HashSet<>();
    private final Set<UUID> smartModePlayers = new HashSet<>();
    private final File agreedPlayersFile;
    private final File yoloAgreedPlayersFile;
    private final File yoloModePlayersFile;
    private final File smartModePlayersFile;
    private final Map<UUID, PendingSmartAction> pendingSmartActions = new ConcurrentHashMap<>();
    private final Map<UUID, DialogueSession> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> isGenerating = new ConcurrentHashMap<>();
    private final Map<UUID, GenerationStatus> generationStates = new ConcurrentHashMap<>();
    private final Map<UUID, Long> generationStartTimes = new ConcurrentHashMap<>();
    private final Map<UUID, String> pendingCommands = new ConcurrentHashMap<>();
    private final Map<UUID, String> interruptedToolCalls = new ConcurrentHashMap<>();
    private final Map<UUID, RetryInfo> retryInfoMap = new ConcurrentHashMap<>();

    /**
     * 重试信息类
     */
    private static class RetryInfo {
        final DialogueSession session;
        final String systemPrompt;
        final String lastMessage;
        final boolean isUserMessage;

        RetryInfo(DialogueSession session, String systemPrompt, String lastMessage, boolean isUserMessage) {
            this.session = session;
            this.systemPrompt = systemPrompt;
            this.lastMessage = lastMessage;
            this.isUserMessage = isUserMessage;
        }
    }

    /**
     * 待处理的SMART操作信息
     */
    private static class PendingSmartAction {
        final String actionType;
        final String actionContent;
        PendingSmartAction(String actionType, String actionContent, RiskAssessmentManager.RiskAssessment assessment) {
            this.actionType = actionType;
            this.actionContent = actionContent;
        }
    }

    public enum GenerationStatus {
        THINKING,
        EXECUTING_TOOL,
        WAITING_CONFIRM,
        WAITING_CHOICE,
        COMPLETED,
        CANCELLED,
        ERROR,
        IDLE
    }

    public CLIManager(FancyHelper plugin) {
        this.plugin = plugin;
        this.ai = new CloudFlareAI(plugin);
        this.promptManager = new PromptManager(plugin);
        this.toolExecutor = new ToolExecutor(plugin, this);
        this.agreedPlayersFile = new File(plugin.getDataFolder(), "agreed_players.txt");
        this.yoloAgreedPlayersFile = new File(plugin.getDataFolder(), "yolo_agreed_players.txt");
        this.yoloModePlayersFile = new File(plugin.getDataFolder(), "yolo_mode_players.txt");
        this.smartModePlayersFile = new File(plugin.getDataFolder(), "smart_mode_players.txt");
        loadAgreedPlayers();
        loadYoloAgreedPlayers();
        loadYoloModePlayers();
        loadSmartModePlayers();
        startTimeoutTask();
        startThinkingTask();
        startLogCleanupTask();
        loadAvailableSessionHistories();
    }

    public void loadAgreedPlayers() {
        agreedPlayers.clear();
        if (!agreedPlayersFile.exists()) return;
        try {
            List<String> lines = java.nio.file.Files.readAllLines(agreedPlayersFile.toPath());
            for (String line : lines) {
                try {
                    agreedPlayers.add(UUID.fromString(line.trim()));
                } catch (IllegalArgumentException ignored) {}
            }
        } catch (IOException e) {
            plugin.getLogger().warning("无法加载已同意协议的玩家列表: " + e.getMessage());
            plugin.getCloudErrorReport().report(e);
        }
    }

    public void loadYoloAgreedPlayers() {
        yoloAgreedPlayers.clear();
        if (!yoloAgreedPlayersFile.exists()) return;
        try {
            List<String> lines = java.nio.file.Files.readAllLines(yoloAgreedPlayersFile.toPath());
            for (String line : lines) {
                try {
                    yoloAgreedPlayers.add(UUID.fromString(line.trim()));
                } catch (IllegalArgumentException ignored) {}
            }
        } catch (IOException e) {
            plugin.getLogger().warning("无法加载已同意 YOLO 协议的玩家列表: " + e.getMessage());
            plugin.getCloudErrorReport().report(e);
        }
    }

    private void saveAgreedPlayer(UUID uuid) {
        agreedPlayers.add(uuid);
        try {
            java.nio.file.Files.write(agreedPlayersFile.toPath(), 
                (uuid.toString() + "\n").getBytes(), 
                java.nio.file.StandardOpenOption.CREATE, 
                java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException e) {
            plugin.getLogger().warning("无法保存已同意协议的玩家: " + e.getMessage());
            plugin.getCloudErrorReport().report(e);
        }
    }

    private void saveYoloAgreedPlayer(UUID uuid) {
        yoloAgreedPlayers.add(uuid);
        try {
            java.nio.file.Files.write(yoloAgreedPlayersFile.toPath(), 
                (uuid.toString() + "\n").getBytes(), 
                java.nio.file.StandardOpenOption.CREATE, 
                java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException e) {
            plugin.getLogger().warning("无法保存已同意 YOLO 协议的玩家: " + e.getMessage());
            plugin.getCloudErrorReport().report(e);
        }
    }

    public void loadYoloModePlayers() {
        yoloModePlayers.clear();
        if (!yoloModePlayersFile.exists()) return;
        try {
            List<String> lines = java.nio.file.Files.readAllLines(yoloModePlayersFile.toPath());
            for (String line : lines) {
                try {
                    yoloModePlayers.add(UUID.fromString(line.trim()));
                } catch (IllegalArgumentException ignored) {}
            }
        } catch (IOException e) {
            plugin.getLogger().warning("无法加载处于 YOLO 模式的玩家列表: " + e.getMessage());
            plugin.getCloudErrorReport().report(e);
        }
    }

    private void saveYoloModeState(UUID uuid, boolean isYolo) {
        if (isYolo) {
            if (yoloModePlayers.add(uuid)) {
                writeYoloModePlayers();
            }
        } else {
            if (yoloModePlayers.remove(uuid)) {
                writeYoloModePlayers();
            }
        }
    }

    private void writeYoloModePlayers() {
        try {
            List<String> lines = new ArrayList<>();
            for (UUID uuid : yoloModePlayers) {
                lines.add(uuid.toString());
            }
            java.nio.file.Files.write(yoloModePlayersFile.toPath(), lines);
        } catch (IOException e) {
            plugin.getLogger().warning("无法保存 YOLO 模式玩家列表: " + e.getMessage());
            plugin.getCloudErrorReport().report(e);
        }
    }

    public void loadSmartModePlayers() {
        smartModePlayers.clear();
        if (!smartModePlayersFile.exists()) return;
        try {
            List<String> lines = java.nio.file.Files.readAllLines(smartModePlayersFile.toPath());
            for (String line : lines) {
                try {
                    smartModePlayers.add(UUID.fromString(line.trim()));
                } catch (IllegalArgumentException ignored) {}
            }
        } catch (IOException e) {
            plugin.getLogger().warning("无法加载处于 SMART 模式的玩家列表: " + e.getMessage());
            plugin.getCloudErrorReport().report(e);
        }
    }

    private void saveSmartModeState(UUID uuid, boolean isSmart) {
        if (isSmart) {
            if (smartModePlayers.add(uuid)) {
                writeSmartModePlayers();
            }
        } else {
            if (smartModePlayers.remove(uuid)) {
                writeSmartModePlayers();
            }
        }
    }

    private void writeSmartModePlayers() {
        try {
            List<String> lines = new ArrayList<>();
            for (UUID uuid : smartModePlayers) {
                lines.add(uuid.toString());
            }
            java.nio.file.Files.write(smartModePlayersFile.toPath(), lines, 
                java.nio.file.StandardOpenOption.CREATE, 
                java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            plugin.getLogger().warning("无法保存 SMART 模式玩家列表: " + e.getMessage());
            plugin.getCloudErrorReport().report(e);
        }
    }



    private void startTimeoutTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                long timeoutMs = plugin.getConfigManager().getTimeoutMinutes() * 60 * 1000L;
                
                for (UUID uuid : new ArrayList<>(activeCLIPayers)) {
                    DialogueSession session = sessions.get(uuid);
                    if (session != null && (now - session.getLastActivityTime()) > timeoutMs) {
                        Player player = Bukkit.getPlayer(uuid);
                        if (player != null) {
                            player.sendMessage(ColorUtil.translateCustomColors("§zFancyHelper§b§r §7> §f由于长时间未活动，已自动退出 FancyHelper。"));
                            exitCLI(player);
                        } else {
                            activeCLIPayers.remove(uuid);
                            sessions.remove(uuid);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 20L * 60, 20L * 60); // 每分钟检查一次
    }
    
    /**
     * 启动日志清理任务，定期删除超过配置天数的日志文件
     */
    private void startLogCleanupTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    Path logDir = plugin.getDataFolder().toPath().resolve("logs");
                    if (!Files.exists(logDir)) {
                        return;
                    }
                    
                     int retentionDays = plugin.getConfigManager().getLogRetentionDays();
                    long cutoffTime = System.currentTimeMillis() - (retentionDays * 24 * 60 * 60 * 1000L);
                    
                    Files.list(logDir)
                        .filter(path -> path.toString().endsWith(".log"))
                        .filter(path -> {
                            try {
                                return Files.getLastModifiedTime(path).toMillis() < cutoffTime;
                            } catch (IOException e) {
                                return false;
                            }
                        })
                        .forEach(path -> {
                            try {
                                Files.delete(path);
                                if (plugin.getConfigManager().isDebug()) {
                                    plugin.getLogger().info("[CLI] 已删除过期日志文件: " + path.getFileName());
                                }
                            } catch (IOException e) {
                                plugin.getLogger().warning("[CLI] 删除日志文件失败: " + path.getFileName() + " - " + e.getMessage());
                            }
                        });
                } catch (IOException e) {
                    plugin.getLogger().warning("[CLI] 日志清理任务出错: " + e.getMessage());
                }
            }
        }.runTaskTimerAsynchronously(plugin, 0L, 20L * 60 * 60); // 每小时检查一次
    }

    /**
     * 加载所有可用的会话历史
     * 在插件启动时自动检测并加载有效的会话历史文件
     */
    private void loadAvailableSessionHistories() {
        try {
            Path historyDir = plugin.getDataFolder().toPath().resolve("temp").resolve("history");
            if (!Files.exists(historyDir)) {
                return;
            }

            long now = System.currentTimeMillis();
            long thirtyMinutesMs = 30 * 60 * 1000;

            Files.list(historyDir)
                .filter(path -> path.toString().endsWith(".json"))
                .forEach(path -> {
                    try {
                        String fileName = path.getFileName().toString();
                        String uuidStr = fileName.substring(0, fileName.length() - 5); // 去掉.json后缀
                        
                        try {
                            UUID uuid = UUID.fromString(uuidStr);
                            
                            // 读取并解析JSON
                            Gson gson = new Gson();
                            String json = Files.readString(path, StandardCharsets.UTF_8);
                            Map<String, Object> sessionData = gson.fromJson(json, new TypeToken<Map<String, Object>>(){}.getType());
                            
                            // 检查时间戳
                            Object timestampObj = sessionData.getOrDefault("timestamp", 0);
                            long timestamp;
                            if (timestampObj instanceof Double) {
                                timestamp = ((Double) timestampObj).longValue();
                            } else if (timestampObj instanceof Long) {
                                timestamp = (Long) timestampObj;
                            } else if (timestampObj instanceof Integer) {
                                timestamp = (Integer) timestampObj;
                            } else {
                                timestamp = 0;
                            }
                            
                            // 检查是否在30分钟内
                            if (now - timestamp <= thirtyMinutesMs) {
                                // 创建新会话并恢复数据
                                DialogueSession session = new DialogueSession();
                                
                                // 恢复对话模式
                                String modeStr = (String) sessionData.getOrDefault("mode", "NORMAL");
                                try {
                                    DialogueSession.Mode mode = DialogueSession.Mode.valueOf(modeStr);
                                    session.setMode(mode);
                                } catch (IllegalArgumentException e) {
                                    session.setMode(DialogueSession.Mode.NORMAL);
                                }
                                
                                // 恢复对话历史
                                Object messagesObj = sessionData.getOrDefault("messages", new ArrayList<>());
                                if (messagesObj instanceof List<?>) {
                                    List<?> messagesList = (List<?>) messagesObj;
                                    for (Object msgObj : messagesList) {
                                        if (msgObj instanceof Map<?, ?>) {
                                            Map<?, ?> msgMap = (Map<?, ?>) msgObj;
                                            Object roleObj = msgMap.get("role");
                                            String role = roleObj != null ? roleObj.toString() : "user";
                                            Object contentObj = msgMap.get("content");
                                            String content = contentObj != null ? contentObj.toString() : "";
                                            Object thoughtObj = msgMap.get("thought");
                                            String thought = thoughtObj != null ? thoughtObj.toString() : null;
                                            session.addMessage(role, content, thought);
                                        }
                                    }
                                }
                                
                                // 恢复工具调用历史
                                Object toolCallsObj = sessionData.getOrDefault("toolCalls", new ArrayList<>());
                                if (toolCallsObj instanceof List<?>) {
                                    List<?> toolCallsList = (List<?>) toolCallsObj;
                                    for (Object toolCallObj : toolCallsList) {
                                        if (toolCallObj instanceof String) {
                                            session.addToolCall((String) toolCallObj);
                                        }
                                    }
                                }
                                
                                // 将会话存储到内存中，等待玩家上线时恢复
                                sessions.put(uuid, session);
                                
                                if (plugin.getConfigManager().isDebug()) {
                                    plugin.getLogger().info("[CLI] 已预加载会话历史: " + path.getFileName());
                                }
                                
                                // 加载完成后删除历史文件
                                Files.deleteIfExists(path);
                                if (plugin.getConfigManager().isDebug()) {
                                    plugin.getLogger().info("[CLI] 已删除会话历史文件: " + path.getFileName());
                                }
                            } else {
                                // 超过30分钟，删除过期文件
                                Files.deleteIfExists(path);
                            }
                        } catch (IllegalArgumentException e) {
                            // UUID解析失败，删除无效文件
                            if (plugin.getConfigManager().isDebug()) {
                                plugin.getLogger().warning("[CLI] 无效的历史文件名: " + fileName);
                            }
                            Files.deleteIfExists(path);
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning("[CLI] 加载会话历史失败: " + path.getFileName() + " - " + e.getMessage());
                    }
                });
        } catch (IOException e) {
            plugin.getLogger().warning("[CLI] 扫描会话历史目录失败: " + e.getMessage());
        }
    }

    /**
     * 启动 AI 思考状态显示任务
     */
    private void startThinkingTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!plugin.isEnabled()) {
                    this.cancel();
                    return;
                }
                long now = System.currentTimeMillis();
                
                for (UUID uuid : activeCLIPayers) {
                    Player player = Bukkit.getPlayer(uuid);
                    if (player == null || !player.isOnline()) continue;

                    GenerationStatus status = generationStates.getOrDefault(uuid, GenerationStatus.IDLE);
                    if (status == GenerationStatus.IDLE) continue;

                    String message = "";
                    switch (status) {
                        case THINKING:
                            Long startTime = generationStartTimes.get(uuid);
                            if (startTime == null) {
                                // 如果开始时间为空，可能是竞态条件导致的（状态已变更但计时器还未检测到）
                                // 跳过显示，避免重新设置时间导致计时继续
                                continue;
                            }
                            long elapsed = (now - startTime) / 1000;
                            message = ChatColor.GRAY + "- 思考中 " + elapsed + "s -";
                            sendStatusMessage(player, message);
                            break;
                        case EXECUTING_TOOL:
                            message = ChatColor.GRAY + "....";
                            sendStatusMessage(player, message);
                            break;
                        case WAITING_CONFIRM:
                            message = ChatColor.YELLOW + "正在征求您的许可...";
                            sendStatusMessage(player, message);
                            break;
                        case WAITING_CHOICE:
                            message = ChatColor.AQUA + "正在征求您的意见...";
                            sendStatusMessage(player, message);
                            break;
                        case COMPLETED:
                            message = ChatColor.GREEN + "- ✓ -";
                            sendStatusMessage(player, message);
                            // 清除显示，2秒后清除 (40 ticks)
                            if (plugin.isEnabled()) {
                                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                                    clearStatusMessage(player);
                                }, 40L);
                            }
                            generationStates.put(uuid, GenerationStatus.IDLE);
                            generationStartTimes.remove(uuid);
                            break;
                        case CANCELLED:
                            message = ChatColor.RED + "- ✕ -";
                            sendStatusMessage(player, message);
                            // 清除显示，2秒后清除 (40 ticks)
                            if (plugin.isEnabled()) {
                                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                                    clearStatusMessage(player);
                                }, 40L);
                            }
                            generationStates.put(uuid, GenerationStatus.IDLE);
                            generationStartTimes.remove(uuid);
                            break;
                        case ERROR:
                            message = ChatColor.RED + "- ERROR -";
                            sendStatusMessage(player, message);
                            // 清除显示，2秒后清除 (40 ticks)
                            if (plugin.isEnabled()) {
                                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                                    clearStatusMessage(player);
                                }, 40L);
                            }
                            generationStates.put(uuid, GenerationStatus.IDLE);
                            generationStartTimes.remove(uuid);
                            break;
                        default:
                            break;
                    }
                }
            }
        }.runTaskTimer(plugin, 5L, 5L); // 提高更新频率到 0.25s，让计时更平滑
    }

    /**
     * 停止当前的思考计时并记录时长
     */
    private void recordThinkingTime(UUID uuid) {
        DialogueSession session = sessions.get(uuid);
        if (session == null) return;

        Long startTime = generationStartTimes.get(uuid);
        GenerationStatus status = generationStates.get(uuid);

        if (startTime != null && status == GenerationStatus.THINKING) {
            session.addThinkingTime(System.currentTimeMillis() - startTime);
        }
    }

    /**
     * 向玩家发送状态消息（根据玩家配置选择 Actionbar 或 Subtitle）
     */
    private void sendStatusMessage(Player player, String message) {
        String position = plugin.getConfigManager().getPlayerDisplayPosition(player);
        if ("subtitle".equalsIgnoreCase(position)) {
            // 为了保证 subtitle 显示，发送空内容的 title
            player.sendTitle("", message, 0, 20, 0);
        } else {
            player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR, new TextComponent(message));
        }
    }

    /**
     * 清除玩家的状态消息显示
     */
    private void clearStatusMessage(Player player) {
        String position = plugin.getConfigManager().getPlayerDisplayPosition(player);
        if ("subtitle".equalsIgnoreCase(position)) {
            player.sendTitle("", "", 0, 0, 0);
        } else {
            player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR, new TextComponent(""));
        }
    }

    /**
     * 检查玩家是否有预加载的会话
     * @param uuid 玩家UUID
     * @return 是否有预加载的会话
     */
    public boolean hasPreloadedSession(UUID uuid) {
        return sessions.containsKey(uuid);
    }

    /**
     * 切换玩家的 CLI 模式
     */
    public void toggleCLI(Player player) {
        UUID uuid = player.getUniqueId();
        if (activeCLIPayers.contains(uuid) || pendingAgreementPlayers.contains(uuid)) {
            exitCLI(player);
        } else {
            enterCLI(player);
        }
    }

    /**
     * 保存会话历史到文件
     * @param uuid 玩家UUID
     * @param session 对话会话
     */
    public void saveSessionHistory(UUID uuid, DialogueSession session) {
        try {
            // 创建历史记录目录
            Path historyDir = plugin.getDataFolder().toPath().resolve("temp").resolve("history");
            Files.createDirectories(historyDir);
            
            // 创建保存文件
            Path historyFile = historyDir.resolve(uuid.toString() + ".json");
            
            // 构建会话数据
            Map<String, Object> sessionData = new HashMap<>();
            sessionData.put("timestamp", System.currentTimeMillis());
            sessionData.put("mode", session.getMode().name());
            
            // 保存对话历史
            List<Map<String, Object>> messages = new ArrayList<>();
            for (DialogueSession.Message message : session.getHistory()) {
                Map<String, Object> msgData = new HashMap<>();
                msgData.put("role", message.getRole());
                msgData.put("content", message.getContent());
                if (message.getThought() != null) {
                    msgData.put("thought", message.getThought());
                }
                messages.add(msgData);
            }
            sessionData.put("messages", messages);
            
            // 保存工具调用历史
            sessionData.put("toolCalls", session.getToolCallHistory());
            
            // 序列化为JSON并保存
            Gson gson = new Gson();
            String json = gson.toJson(sessionData);
            Files.write(historyFile, json.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            
            if (plugin.getConfigManager().isDebug()) {
                plugin.getLogger().info("[CLI] 已保存会话历史: " + historyFile.getFileName());
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[CLI] 保存会话历史失败: " + e.getMessage());
            plugin.getCloudErrorReport().report(e);
        }
    }

    /**
     * 加载会话历史
     * @param uuid 玩家UUID
     * @return 恢复的对话会话，null如果没有有效的历史记录
     */
    public DialogueSession loadSessionHistory(UUID uuid) {
        Path historyFile = null;
        try {
            // 检查历史记录目录
            Path historyDir = plugin.getDataFolder().toPath().resolve("temp").resolve("history");
            if (!Files.exists(historyDir)) {
                return null;
            }

            // 检查历史文件
            historyFile = historyDir.resolve(uuid.toString() + ".json");
            if (!Files.exists(historyFile)) {
                return null;
            }

            // 读取并解析JSON
            Gson gson = new Gson();
            String json = Files.readString(historyFile, StandardCharsets.UTF_8);
            Map<String, Object> sessionData = gson.fromJson(json, new TypeToken<Map<String, Object>>(){}.getType());

            // 检查时间戳
            Object timestampObj = sessionData.getOrDefault("timestamp", 0);
            long timestamp;
            if (timestampObj instanceof Double) {
                timestamp = ((Double) timestampObj).longValue();
            } else if (timestampObj instanceof Long) {
                timestamp = (Long) timestampObj;
            } else if (timestampObj instanceof Integer) {
                timestamp = (Integer) timestampObj;
            } else {
                timestamp = 0;
            }
            long now = System.currentTimeMillis();
            long thirtyMinutesMs = 30 * 60 * 1000;

            if (now - timestamp > thirtyMinutesMs) {
                // 超过30分钟，删除过期文件
                Files.deleteIfExists(historyFile);
                return null;
            }

            // 创建新会话并恢复数据
            DialogueSession session = new DialogueSession();

            // 恢复对话模式
            String modeStr = (String) sessionData.getOrDefault("mode", "NORMAL");
            try {
                DialogueSession.Mode mode = DialogueSession.Mode.valueOf(modeStr);
                session.setMode(mode);
            } catch (IllegalArgumentException e) {
                // 模式解析失败，使用默认模式
                session.setMode(DialogueSession.Mode.NORMAL);
            }

            // 恢复对话历史
            Object messagesObj = sessionData.getOrDefault("messages", new ArrayList<>());
            if (messagesObj instanceof List<?>) {
                List<?> messagesList = (List<?>) messagesObj;
                for (Object msgObj : messagesList) {
                    if (msgObj instanceof Map<?, ?>) {
                        Map<?, ?> msgMap = (Map<?, ?>) msgObj;
                        Object roleObj = msgMap.get("role");
                        String role = roleObj != null ? roleObj.toString() : "user";
                        Object contentObj = msgMap.get("content");
                        String content = contentObj != null ? contentObj.toString() : "";
                        Object thoughtObj = msgMap.get("thought");
                        String thought = thoughtObj != null ? thoughtObj.toString() : null;
                        session.addMessage(role, content, thought);
                    }
                }
            }

            // 恢复工具调用历史
            Object toolCallsObj = sessionData.getOrDefault("toolCalls", new ArrayList<>());
            if (toolCallsObj instanceof List<?>) {
                List<?> toolCallsList = (List<?>) toolCallsObj;
                for (Object toolCallObj : toolCallsList) {
                    if (toolCallObj instanceof String) {
                        session.addToolCall((String) toolCallObj);
                    }
                }
            }

            if (plugin.getConfigManager().isDebug()) {
                plugin.getLogger().info("[CLI] 已加载会话历史: " + historyFile.getFileName());
            }

            return session;
        } catch (Exception e) {
            plugin.getLogger().warning("[CLI] 加载会话历史失败: " + e.getMessage());
            plugin.getCloudErrorReport().report(e);
            return null;
        } finally {
            // 确保历史文件被删除
            if (historyFile != null) {
                try {
                    Files.deleteIfExists(historyFile);
                    if (plugin.getConfigManager().isDebug()) {
                        plugin.getLogger().info("[CLI] 已删除历史文件: " + historyFile.getFileName());
                    }
                } catch (IOException e) {
                    plugin.getLogger().warning("[CLI] 删除历史文件失败: " + e.getMessage());
                }
            }
        }
    }

    /**
     * 发送插件卸载提示
     * @param player 玩家
     */
    public void sendUnloadMessage(Player player) {
        // 创建主消息 - 使用自定义颜色
        @SuppressWarnings("deprecation")
        net.md_5.bungee.api.chat.BaseComponent[] components = net.md_5.bungee.api.chat.TextComponent.fromLegacyText(
            ColorUtil.translateCustomColors("§zFancyHelper§b§r §7> §f与Fancy的会话已被挂起 ")
        );
        TextComponent message = new TextComponent(components);

        // 创建了解更多按钮
        TextComponent learnMoreBtn = new TextComponent("[了解更多]");
        learnMoreBtn.setColor(net.md_5.bungee.api.ChatColor.BLUE);
        learnMoreBtn.setUnderlined(true);
        learnMoreBtn.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://blog.baicaizhale.top/post/whyusee1"));
        learnMoreBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("为什么FancyHelper会话会被挂起")));

        message.addExtra(learnMoreBtn);
        player.spigot().sendMessage(message);
    }

    /**
     * 获取所有会话
     * @return 会话映射
     */
    public Map<UUID, DialogueSession> getSessions() {
        return sessions;
    }

    /**
     * 关闭管理器，清理资源
     */
    public void shutdown() {
        if (plugin.getConfigManager().isDebug()) {
            plugin.getLogger().info("[CLI] 正在关闭 CLIManager...");
        }
        
        // 移除所有活跃的CLI玩家
        for (UUID uuid : new ArrayList<>(activeCLIPayers)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                sendUnloadMessage(player);
            }
            DialogueSession session = sessions.get(uuid);
            if (session != null) {
                saveSessionHistory(uuid, session);
            }
            sessions.remove(uuid);
        }
        activeCLIPayers.clear();
        pendingAgreementPlayers.clear();
        sessions.clear();
        isGenerating.clear();
        pendingCommands.clear();
        generationStates.clear();
        generationStartTimes.clear();
        
        // 关闭AI客户端（这会处理OkHttp的cleanup）
        if (ai != null) {
            ai.shutdown();
        }
        
        if (plugin.getConfigManager().isDebug()) {
            plugin.getLogger().info("[CLI] CLIManager 已完成关闭。");
        }
    }

    /**
     * 进入 CLI 模式
     */
    public void enterCLI(Player player) {
        UUID uuid = player.getUniqueId();

        // 检查 EULA 文件状态
        if (!plugin.getEulaManager().isEulaValid()) {
            player.sendMessage(ColorUtil.translateCustomColors("§zFancyHelper§b§r §7> §f错误：EULA 文件缺失或被非法改动且无法还原，请联系管理员检查权限设置。"));
            plugin.getLogger().warning("[CLI] 由于 EULA 文件无效，拒绝了 " + player.getName() + " 的访问。");
            return;
        }

        if (plugin.getConfigManager().isDebug()) {
            plugin.getLogger().info("[CLI] 玩家 " + player.getName() + " 正在进入 FancyHelper。");
        }
        
        // 检查用户协议
        if (!agreedPlayers.contains(uuid)) {
            if (plugin.getConfigManager().isDebug()) {
                plugin.getLogger().info("[CLI] 玩家 " + player.getName() + " 需要同意协议。");
            }
            sendAgreement(player);
            pendingAgreementPlayers.add(uuid);
            return;
        }
        
        // 检查是否已经有预加载的会话
        DialogueSession session = sessions.get(uuid);
        // 如果没有预加载的会话，尝试加载历史会话
        if (session == null) {
            session = loadSessionHistory(uuid);
        }
        
        // 如果仍然没有会话，创建新会话
        if (session == null) {
            session = new DialogueSession();
            // 恢复上次的模式
            if (yoloModePlayers.contains(uuid)) {
                session.setMode(DialogueSession.Mode.YOLO);
            } else if (smartModePlayers.contains(uuid)) {
                session.setMode(DialogueSession.Mode.SMART);
            }
            
            // 先将会话放入 Map，确保后续操作能获取到正确的模式
            sessions.put(uuid, session);
            
            // 创建日志文件
            try {
                Path logDir = plugin.getDataFolder().toPath().resolve("logs");
                Files.createDirectories(logDir);
                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss-SSS"));
                String logFileName = timestamp + ".log";
                Path logFilePath = logDir.resolve(logFileName);
                session.setLogFilePath(logFilePath.toString());
                // 根据调试模式设置详细日志级别
                session.setVerboseLogging(plugin.getConfigManager().isDebug());
                if (plugin.getConfigManager().isDebug()) {
                    plugin.getLogger().info("[CLI] 创建日志文件: " + logFileName);
                }
            } catch (IOException e) {
                plugin.getLogger().warning("[CLI] 创建日志文件失败: " + e.getMessage());
            }
            
            // 发送进入消息和问候
            sendEnterMessage(player);
            triggerGreeting(player);
        } else {
            // 有预加载的会话，静默进入CLI模式
            if (plugin.getConfigManager().isDebug()) {
                plugin.getLogger().info("[CLI] 玩家 " + player.getName() + " 静默进入CLI模式，会话已恢复");
            }
            // 显示恢复提示
            player.sendMessage(ColorUtil.translateCustomColors("§zFancyHelper§b§r §7> §f会话已恢复，您可以继续了"));
        }
        
        activeCLIPayers.add(uuid);
        // 注意：新会话已经在上面放入 Map，这里只处理恢复会话的情况
        if (session != null) {
            sessions.put(uuid, session);
        }
    }

    /**
     * 触发硬编码的初始问候语（根据时间、随机短语生成）
     * 
     * @param player 接收问候的玩家
     */
    private void triggerGreeting(Player player) {
        UUID uuid = player.getUniqueId();
        DialogueSession session = sessions.get(uuid);
        if (session == null) return;

        isGenerating.put(uuid, true); // 设置生成状态，防止在此期间玩家输入触发新的 AI 调用

        // 进入 CLI 后 0.3s 延迟展示 (约 6 ticks)
        if (!plugin.isEnabled()) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try {
                // 检查玩家是否仍在线且在 CLI 模式中
                if (!plugin.isEnabled() || !activeCLIPayers.contains(uuid) || !player.isOnline()) return;

                // 1. 获取基于时间的问候语
                int hour = java.time.LocalDateTime.now().getHour();
                String timeGreeting;
                if (hour >= 5 && hour < 11) {
                    timeGreeting = "早上好";
                } else if (hour >= 11 && hour < 14) {
                    timeGreeting = "中午好";
                } else if (hour >= 14 && hour < 18) {
                    timeGreeting = "下午好";
                } else {
                    timeGreeting = "晚上好";
                }

                // 2. 获取随机帮助短语
                String[] helpPhrases = {
                    "有什么需要我帮助的吗？",
                    "需要我帮忙吗？",
                    "有困难就告诉我吧！",
                    "乐意为您效劳。"
                };
                String randomHelp = helpPhrases[new Random().nextInt(helpPhrases.length)];

                // 3. 构建并发送消息
                // 格式：◆ [问候语]，[自定义亮蓝色玩家名]。[随机短语]
                TextComponent message = new TextComponent(ChatColor.WHITE + "◆ " + timeGreeting + "，");
                
                TextComponent playerName = new TextComponent(player.getName());
                playerName.setColor(net.md_5.bungee.api.ChatColor.of(ColorUtil.getColorZ())); // 自定义亮蓝色
                
                message.addExtra(playerName);
                message.addExtra(new TextComponent(ChatColor.WHITE + "。" + randomHelp));

                player.spigot().sendMessage(message);

                // 4. 将问候语记录到对话历史中，让 AI 知道已经打过招呼了
                String fullGreeting = timeGreeting + "，" + player.getName() + "。" + randomHelp;
                session.addMessage("assistant", fullGreeting);
            } finally {
                // 确保生成状态为 false，允许玩家开始输入
                isGenerating.put(uuid, false);
            }
        }, 6L);
    }

    /**
     * 退出 CLI 模式
     */
    public void exitCLI(Player player) {
        UUID uuid = player.getUniqueId();
        
        if (!activeCLIPayers.contains(uuid)) {
            return;
        }
        
        if (plugin.getConfigManager().isDebug()) {
            plugin.getLogger().info("[CLI] 玩家 " + player.getName() + " 正在退出 FancyHelper。");
        }
        
        // 退出前自动取消待确认的工具调用
        if (pendingCommands.containsKey(uuid)) {
            pendingCommands.remove(uuid);
            player.sendMessage(ChatColor.GRAY + "⇒ 已取消待处理的操作");
        }

        // 清空玩家的待办列表
        plugin.getTodoManager().clearTodos(uuid);

        // 清空重试信息
        retryInfoMap.remove(uuid);

        recordThinkingTime(uuid);
        sendExitMessage(player);
        
        // 保存会话历史 - 仅在插件卸载时保存
        // DialogueSession session = sessions.get(uuid);
        // if (session != null) {
        //     saveSessionHistory(uuid, session);
        // }
        
        activeCLIPayers.remove(uuid);
        pendingAgreementPlayers.remove(uuid);
        pendingYoloAgreementPlayers.remove(uuid);
        sessions.remove(uuid);
        isGenerating.remove(uuid);
        generationStates.remove(uuid);
        generationStartTimes.remove(uuid);
    }

    public void switchMode(Player player, DialogueSession.Mode targetMode) {
        UUID uuid = player.getUniqueId();
        DialogueSession session = sessions.get(uuid);
        if (session == null) return;

        if (targetMode == DialogueSession.Mode.YOLO) {
            if (!yoloAgreedPlayers.contains(uuid)) {
                sendYoloWarning(player);
                pendingYoloAgreementPlayers.add(uuid);
                return;
            }
            session.setMode(DialogueSession.Mode.YOLO);
            saveYoloModeState(uuid, true);
            saveSmartModeState(uuid, false);
            player.sendMessage(ChatColor.GRAY + "------------------");
            player.sendMessage(ChatColor.WHITE + "⨀ 已切换至 YOLO 模式。在该模式下，Fancy 执行命令将不再请求您的确认。");
        } else if (targetMode == DialogueSession.Mode.SMART) {
            session.setMode(DialogueSession.Mode.SMART);
            saveSmartModeState(uuid, true);
            saveYoloModeState(uuid, false);
            player.sendMessage(ChatColor.GRAY + "------------------");
            player.sendMessage(ChatColor.WHITE + "⨀ 已切换至 SMART 模式。在该模式下，Fancy 会先评估操作风险，高风险操作需要您的确认。");
        } else {
            session.setMode(DialogueSession.Mode.NORMAL);
            saveYoloModeState(uuid, false);
            saveSmartModeState(uuid, false);
            player.sendMessage(ChatColor.GRAY + "------------------");
            player.sendMessage(ChatColor.WHITE + "⨀ 已切换至 Normal 模式。");
        }
        sendEnterMessage(player);
    }

    private void sendYoloWarning(Player player) {
        player.sendMessage(ChatColor.RED + "===============");
        player.sendMessage(ChatColor.DARK_RED + "WARNING: You only live once");
        player.sendMessage(ChatColor.GRAY + "在此模式下，Fancy 将拥有自动执行服务器命令的权限。");
        player.sendMessage(ChatColor.GRAY + "这意味着它可能会在未经您确认的情况下执行任何操作。");
        player.sendMessage(ChatColor.GRAY + "请确保您信任 AI 的决定，并承担由此产生的风险。");

        TextComponent message = new TextComponent(ChatColor.WHITE + "发送 ");
        TextComponent agreeBtn = new TextComponent(ChatColor.RED + "agree");
        agreeBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cli agree"));
        agreeBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(ChatColor.RED + "点击确认并进入 YOLO 模式")));

        message.addExtra(agreeBtn);
        message.addExtra(new TextComponent(ChatColor.WHITE + " 表示确认并进入 YOLO 模式。"));

        player.spigot().sendMessage(message);
        player.sendMessage(ChatColor.RED + "===============");
    }

    /**
     * 发送SMART模式风险确认界面
     */
    public void sendSmartRiskConfirm(Player player, String actionType, String actionContent, 
                                    RiskAssessmentManager.RiskAssessment assessment) {
        UUID uuid = player.getUniqueId();
        
        pendingSmartActions.put(uuid, new PendingSmartAction(actionType, actionContent, assessment));
        setGenerating(uuid, false, GenerationStatus.WAITING_CONFIRM);
        
        player.sendMessage(ChatColor.WHITE + "⁕ " + ChatColor.GRAY + "检测到风险操作，是否继续？");
        
        ChatColor riskColor;
        if (assessment.level < 25) {
            riskColor = ChatColor.GREEN;
        } else if (assessment.level < 50) {
            riskColor = ChatColor.YELLOW;
        } else if (assessment.level < 75) {
            riskColor = ChatColor.GOLD;
        } else {
            riskColor = ChatColor.RED;
        }
        player.sendMessage(ChatColor.WHITE + "  风险值: " + riskColor + assessment.level + "/100");
        
        if ("run".equals(actionType)) {
            player.sendMessage(ChatColor.WHITE + "  运行命令 " + ChatColor.YELLOW + actionContent);
        } else if ("edit".equals(actionType)) {
            String[] parts = actionContent.split("\\|", 3);
            String path = parts.length > 0 ? parts[0].trim() : "";
            player.sendMessage(ChatColor.WHITE + "  修改文件 " + ChatColor.YELLOW + path);
            if (parts.length >= 3) {
                player.sendMessage(ChatColor.WHITE + "  From " + ChatColor.GRAY + parts[1]);
                player.sendMessage(ChatColor.WHITE + "  To " + ChatColor.GRAY + parts[2]);
            }
        }
        
        if (assessment.reason != null && !assessment.reason.isEmpty()) {
            player.sendMessage(ChatColor.WHITE + "  原因 " + ChatColor.GRAY + assessment.reason);
        }
        
        TextComponent message = new TextComponent("  ");
        
        TextComponent allowBtn = new TextComponent(ChatColor.GREEN + "[ ✓ ]");
        allowBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cli smart_allow"));
        allowBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("允许本次操作")));
        
        TextComponent spacer1 = new TextComponent(" ");
        
        TextComponent denyBtn = new TextComponent(ChatColor.RED + "[ ✕ ]");
        denyBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cli smart_deny"));
        denyBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("拒绝此操作")));
        
        TextComponent spacer2 = new TextComponent(" ");
        
        TextComponent neverAskBtn = new TextComponent(ChatColor.GRAY + "[ 不再询问 ]");
        neverAskBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cli smart_never"));
        neverAskBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("允许本次操作并不再询问")));
        
        message.addExtra(allowBtn);
        message.addExtra(spacer1);
        message.addExtra(denyBtn);
        message.addExtra(spacer2);
        message.addExtra(neverAskBtn);
        
        player.spigot().sendMessage(message);
    }

    public void handleConfirm(Player player) {
        UUID uuid = player.getUniqueId();
        DialogueSession session = sessions.get(uuid);
        if (session != null && pendingCommands.containsKey(uuid)) {
            session.appendLog("USER_ACTION", "Confirmed command: " + pendingCommands.get(uuid));
        }

        if (pendingCommands.containsKey(uuid)) {
            String cmd = pendingCommands.get(uuid);
            if (!"CHOOSING".equals(cmd)) {
                pendingCommands.remove(uuid);
                generationStates.put(uuid, GenerationStatus.EXECUTING_TOOL);
                if (cmd.startsWith("LS:") || cmd.startsWith("READ:") || cmd.startsWith("DIFF:") || cmd.startsWith("EDIT:")) {
                    String[] parts = cmd.split(":", 2);
                    String type = parts[0].toLowerCase();
                    String args = parts[1];
                    // 将内部类型映射到配置中的工具名称
                    String toolName = mapTypeToToolName(type);
                    checkVerificationAndExecute(player, type, toolName, args);
                } else {
                    toolExecutor.executeCommand(player, cmd);
                }
            }
        }
    }

    private void checkVerificationAndExecute(Player player, String type, String toolName, String args) {
        // 检查是否被冻结
        long freezeRemaining = plugin.getVerificationManager().getPlayerFreezeRemaining(player);
        if (freezeRemaining > 0) {
            player.sendMessage(ChatColor.RED + "验证已冻结，请在 " + freezeRemaining + " 秒后重试。");
            return;
        }

        if (plugin.getConfigManager().isPlayerToolEnabled(player, toolName)) {
            toolExecutor.executeFileOperation(player, type, args);
        } else {
            player.sendMessage(ChatColor.YELLOW + "首次使用 " + toolName + " 工具需要完成安全验证。");
            plugin.getVerificationManager().startVerification(player, toolName, () -> {
                plugin.getConfigManager().setPlayerToolEnabled(player, toolName, true);
                toolExecutor.executeFileOperation(player, type, args);
            });
        }
    }

    /**
     * 将内部类型映射到配置中的工具名称
     * @param type 内部类型（ls, read, edit, diff）
     * @return 配置中的工具名称（ls, read, edit）
     */
    private String mapTypeToToolName(String type) {
        return switch (type.toLowerCase()) {
            case "ls" -> "ls";
            case "read" -> "read";
            case "edit", "diff" -> "edit";
            default -> type;
        };
    }

    public void handleCancel(Player player) {
        UUID uuid = player.getUniqueId();
        DialogueSession session = sessions.get(uuid);
        if (session != null && pendingCommands.containsKey(uuid)) {
            session.appendLog("USER_ACTION", "Cancelled command: " + pendingCommands.get(uuid));
        }

        if (pendingCommands.containsKey(uuid)) {
            pendingCommands.remove(uuid);
            player.sendMessage(ChatColor.GRAY + "⇒ 命令已取消");
            isGenerating.put(uuid, false);
            generationStates.put(uuid, GenerationStatus.CANCELLED);
            generationStartTimes.put(uuid, System.currentTimeMillis());
        }
    }

    /**
     * 处理SMART模式 - 本次允许
     */
    public void handleSmartAllow(Player player) {
        UUID uuid = player.getUniqueId();
        PendingSmartAction action = pendingSmartActions.get(uuid);
        if (action == null) {
            return;
        }
        
        pendingSmartActions.remove(uuid);
        executeSmartAction(player, action);
    }

    /**
     * 处理SMART模式 - 拒绝
     */
    public void handleSmartDeny(Player player) {
        UUID uuid = player.getUniqueId();
        PendingSmartAction action = pendingSmartActions.get(uuid);
        if (action == null) {
            return;
        }
        
        pendingSmartActions.remove(uuid);
        player.sendMessage(ChatColor.GRAY + "⇒ 操作已拒绝");
        isGenerating.put(uuid, false);
        generationStates.put(uuid, GenerationStatus.CANCELLED);
        generationStartTimes.put(uuid, System.currentTimeMillis());
        
        feedbackToAI(player, "#error: 用户拒绝了此操作。");
    }

    /**
     * 处理SMART模式 - 不再询问
     */
    public void handleSmartNever(Player player) {
        UUID uuid = player.getUniqueId();
        PendingSmartAction action = pendingSmartActions.get(uuid);
        if (action == null) {
            return;
        }
        
        pendingSmartActions.remove(uuid);
        
        // 显式切换到 YOLO 模式
        switchMode(player, DialogueSession.Mode.YOLO);
        
        executeSmartAction(player, action);
    }

    /**
     * 执行SMART模式的操作
     */
    private void executeSmartAction(Player player, PendingSmartAction action) {
        UUID uuid = player.getUniqueId();
        
        if ("run".equals(action.actionType)) {
            player.sendMessage(ChatColor.GOLD + ">> SMART RUN " + ChatColor.WHITE + action.actionContent);
            setGenerating(uuid, false, GenerationStatus.EXECUTING_TOOL);
            toolExecutor.executeCommand(player, action.actionContent);
        } else if ("edit".equals(action.actionType)) {
            String pendingStr = "DIFF:" + action.actionContent;
            setPendingCommand(uuid, pendingStr);
            setGenerating(uuid, false, GenerationStatus.WAITING_CONFIRM);
            toolExecutor.sendConfirmButtons(player, "");
        }
    }

    /**
     * 处理玩家发送的消息
     */
    public boolean handleChat(Player player, String message) {
        UUID uuid = player.getUniqueId();

        // 优先处理验证逻辑
        if (plugin.getVerificationManager().isVerifying(player)) {
            if (plugin.getVerificationManager().handleVerification(player, message)) {
                return true;
            }
        }

        // 如果玩家在等待协议同意
        if (pendingAgreementPlayers.contains(uuid)) {
            if (plugin.getConfigManager().isDebug()) {
                plugin.getLogger().info("[CLI] 玩家 " + player.getName() + " 发送了协议同意消息: " + message);
            }
            if (message.equalsIgnoreCase("agree")) {
                // 再次检查 EULA 状态
                if (!plugin.getEulaManager().isEulaValid()) {
                    player.sendMessage(ChatColor.RED + "系统错误：EULA 文件状态异常，无法完成同意。");
                    return true;
                }
                pendingAgreementPlayers.remove(uuid);
                saveAgreedPlayer(uuid);
                enterCLI(player);
            } else {
                player.sendMessage(ChatColor.RED + "请发送 agree 以同意协议，或发送 /cli 退出。");
            }
            return true;
        }

        // 如果玩家在等待 YOLO 协议同意
        if (pendingYoloAgreementPlayers.contains(uuid)) {
            if (message.equalsIgnoreCase("agree")) {
                pendingYoloAgreementPlayers.remove(uuid);
                saveYoloAgreedPlayer(uuid);
                switchMode(player, DialogueSession.Mode.YOLO);
            } else if (message.equalsIgnoreCase("stop")) {
                pendingYoloAgreementPlayers.remove(uuid);
                player.sendMessage(ChatColor.GRAY + "⇒ 已取消进入 YOLO 模式。");
            } else {
                player.sendMessage(ChatColor.RED + "请发送 agree 以进入 YOLO 模式，或发送 stop 取消。");
            }
            return true;
        }

        // 如果玩家处于 CLI 模式
        if (activeCLIPayers.contains(uuid)) {
            if (message.startsWith("！") || message.startsWith("!")) {
                return false;
            }
            if (plugin.getConfigManager().isDebug()) {
                plugin.getLogger().info("[CLI] 拦截到来自 " + player.getName() + " 的消息: " + message);
            }
            if (message.equalsIgnoreCase("exit")) {
                exitCLI(player);
                return true;
            }
            if (message.equalsIgnoreCase("stop")) {
                boolean interrupted = false;
                interruptedToolCalls.remove(uuid);
                if (isGenerating.getOrDefault(uuid, false)) {
                    isGenerating.put(uuid, false);
                    recordThinkingTime(uuid);
                    generationStates.put(uuid, GenerationStatus.CANCELLED);
                    generationStartTimes.put(uuid, System.currentTimeMillis());
                    player.sendMessage(ChatColor.YELLOW + "✕" + ChatColor.WHITE + " 已打断 Fancy 生成");
                    interrupted = true;
                }
                if (pendingCommands.containsKey(uuid)) {
                    pendingCommands.remove(uuid);
                    player.sendMessage(ChatColor.YELLOW + "✕" + ChatColor.WHITE + " 已取消当前待处理的操作");
                    isGenerating.put(uuid, false);
                    generationStates.put(uuid, GenerationStatus.CANCELLED);
                    generationStartTimes.put(uuid, System.currentTimeMillis());
                    interrupted = true;
                }
                if (!interrupted) {
                    player.sendMessage(ChatColor.GRAY + "当前没有正在进行的操作。输入 exit 退出 CLI 模式。");
                }
                return true;
            }

            if (message.equalsIgnoreCase("/cli exempt_anti_loop")) {
                DialogueSession session = sessions.get(uuid);
                if (session != null) {
                    session.setAntiLoopExempted(true);
                    player.sendMessage(ChatColor.WHITE + "✔ 已为本次对话开启豁免模式，Fancy 将不再被自动打断。");

                    // 恢复执行之前被打断的工具
                    String interruptedCall = interruptedToolCalls.get(uuid);
                    if (interruptedCall != null) {
                        player.sendMessage(ChatColor.GRAY + "⇒ 正在恢复执行之前被打断的操作...");
                        isGenerating.put(uuid, true);
                        generationStates.put(uuid, GenerationStatus.EXECUTING_TOOL);
                        generationStartTimes.put(uuid, System.currentTimeMillis());
                        executeTool(player, interruptedCall);
                    }
                }
                return true;
            }

            if (message.equalsIgnoreCase("/cli retry")) {
                handleRetry(player);
                return true;
            }

            // 处理待确认的命令或选择
            if (pendingCommands.containsKey(uuid)) {
                String pending = pendingCommands.get(uuid);
                if (pending.equals("CHOOSING")) {
                    pendingCommands.remove(uuid);
                    player.sendMessage(ChatColor.GRAY + "◇ " + message);
                    feedbackToAI(player, "#ask_result: " + message);
                    return true;
                }
                
                if (message.equalsIgnoreCase("y") || message.equalsIgnoreCase("/fancyhelper confirm")) {
                    handleConfirm(player);
                } else if (message.equalsIgnoreCase("n") || message.equalsIgnoreCase("/fancyhelper cancel")) {
                    handleCancel(player);
                } else {
                    player.sendMessage(ChatColor.RED + "请确认命令 [Y/N]");
                }
                return true;
            }
            
            if (isGenerating.getOrDefault(uuid, false)) {
                player.sendMessage(ChatColor.RED + "⨀ 请不要在 Fancy 生成内容时发送消息，如需打断请输入 stop");
                return true;
            }

            DialogueSession session = sessions.get(uuid);
            // 用户发送了消息，重置工具链计数
            if (session != null) {
                session.resetToolChain();
            }

            processAIMessage(player, message);
            return true;
        }

        return false;
    }

    /**
     * 处理重试操作
     */
    public void handleRetry(Player player) {
        UUID uuid = player.getUniqueId();
        DialogueSession session = sessions.get(uuid);
        if (session != null) {
            session.appendLog("USER_ACTION", "Retrying AI call");
        }
        
        RetryInfo retryInfo = retryInfoMap.get(uuid);
        if (retryInfo == null) {
            player.sendMessage(ChatColor.GRAY + "没有可重试的操作。");
            return;
        }

        player.sendMessage(ChatColor.GRAY + "⇒ 正在重试 AI 调用...");
        isGenerating.put(uuid, true);
        generationStates.put(uuid, GenerationStatus.THINKING);
        generationStartTimes.put(uuid, System.currentTimeMillis());
        retryInfoMap.remove(uuid);

        // 使用异步任务重试
        if (!plugin.isEnabled()) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            // 设置重试回调，向玩家显示重试提示
            ai.setRetryCallback((statusCode, retryMessage) -> {
                if (!plugin.isEnabled()) return;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        TextComponent retryMsg;
                        if (statusCode == 429) {
                            // 429 错误使用黄色⁕ 白色
                            retryMsg = new TextComponent(ChatColor.YELLOW + "⁕ " + ChatColor.WHITE + retryMessage);
                        } else {
                            // 其他错误使用灰色⁕
                            retryMsg = new TextComponent(ChatColor.GRAY + "⁕ " + retryMessage);
                        }
                        player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.CHAT, retryMsg);
                    }
                });
            });
            
            try {
                // 如果有最后一条消息，重新加入会话（因为失败时会被移除）
                if (retryInfo.lastMessage != null) {
                    retryInfo.session.addMessage("user", retryInfo.lastMessage);
                }

                AIResponse response = ai.chat(retryInfo.session, retryInfo.systemPrompt);
                if (!plugin.isEnabled()) return;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    handleAIResponse(player, response);
                });
            } catch (IOException e) {
                plugin.getCloudErrorReport().report(e);
                if (!plugin.isEnabled()) return;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    // 再次失败，重新移除最后一条消息并保存重试信息
                    if (retryInfo.lastMessage != null) {
                        retryInfo.session.removeLastMessage();
                    }
                    retryInfoMap.put(uuid, new RetryInfo(retryInfo.session, retryInfo.systemPrompt, retryInfo.lastMessage, retryInfo.isUserMessage));

                    String errorMsg = e.getMessage();
                    // 只显示友好的提示消息（FancyHelper > 开头的）
                    if (errorMsg != null && errorMsg.startsWith("§zFancyHelper§b§r §7> §f")) {
                        player.sendMessage(ColorUtil.translateCustomColors(errorMsg));
                    } else if (errorMsg != null && errorMsg.startsWith("FancyHelper > ")) {
                        player.sendMessage(ColorUtil.translateCustomColors("§zFancyHelper§b§r §7> §f" + errorMsg.substring("FancyHelper > ".length())));
                    } else {
                        player.sendMessage(ChatColor.RED + "⨀ AI 调用失败（重试）: " + errorMsg);
                    }
                    // 控制台日志已在 CloudFlareAI.java 中输出

                    // 显示重试按钮（与报错信息同一行）
                    TextComponent retryBtn = new TextComponent(ChatColor.WHITE + "(");
                    retryBtn.addExtra(new TextComponent(ChatColor.GREEN + "🔄"));
                    retryBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cli retry"));
                    String hoverText = "§b§l=======\n§b§l|  重试  |\n§b§l=======";
                    retryBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(ChatColor.translateAlternateColorCodes('&', hoverText))));
                    retryBtn.addExtra(new TextComponent(ChatColor.WHITE + ")"));

                    player.spigot().sendMessage(retryBtn);

                    isGenerating.put(uuid, false);
                    recordThinkingTime(uuid);
                    generationStates.put(uuid, GenerationStatus.ERROR);
                    generationStartTimes.remove(uuid);
                    player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR, new TextComponent(""));
                });
            } catch (Throwable t) {
                plugin.getCloudErrorReport().report(t);
                if (!plugin.isEnabled()) return;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    // 再次失败，重新移除最后一条消息并保存重试信息
                    if (retryInfo.lastMessage != null) {
                        retryInfo.session.removeLastMessage();
                    }
                    retryInfoMap.put(uuid, new RetryInfo(retryInfo.session, retryInfo.systemPrompt, retryInfo.lastMessage, retryInfo.isUserMessage));

                    player.sendMessage(ChatColor.RED + "⨀ 系统内部错误（重试）: " + t.getMessage());

                    // 显示重试按钮
                    TextComponent retryMsg = new TextComponent(ChatColor.YELLOW + "点击 ");
                    TextComponent retryBtn = new TextComponent(ChatColor.GREEN + "[ 重试 ]");
                    retryBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cli retry"));
                    retryBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(ChatColor.GREEN + "点击再次重试")));
                    retryMsg.addExtra(retryBtn);
                    retryMsg.addExtra(new TextComponent(ChatColor.YELLOW + " 来重新尝试"));

                    player.spigot().sendMessage(retryMsg);

                    isGenerating.put(uuid, false);
                    recordThinkingTime(uuid);
                    generationStates.put(uuid, GenerationStatus.ERROR);
                    generationStartTimes.remove(uuid);
                    player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR, new TextComponent(""));
                });
            } finally {
                // 清除重试回调
                ai.clearRetryCallback();
            }
        });
    }

    /**
     * 使用AI智能压缩上下文
     * @param player 玩家
     * @param session 对话会话
     */
    private void compressContextWithAI(Player player, DialogueSession session) {
        UUID uuid = player.getUniqueId();
        
        try {
            // 准备要压缩的上下文
            List<DialogueSession.Message> history = session.getHistory();
            int keepRecent = 10;
            
            if (history.size() <= keepRecent * 2) {
                return; // 消息数量不足，不需要压缩
            }
            
            // 提取要压缩的旧消息
            List<DialogueSession.Message> oldMessages = new ArrayList<>(history.subList(0, history.size() - keepRecent));
            
            // 构建压缩输入
            StringBuilder contextBuilder = new StringBuilder();
            for (DialogueSession.Message msg : oldMessages) {
                if (msg.getRole().equals("user")) {
                    contextBuilder.append("用户: ").append(msg.getContent()).append("\n");
                } else if (msg.getRole().equals("assistant")) {
                    contextBuilder.append("助手: ").append(msg.getContent()).append("\n");
                }
            }
            
            String contextToCompress = contextBuilder.toString();
            
            if (plugin.getConfigManager().isDebug()) {
                plugin.getLogger().info("[CLI] 正在使用AI压缩上下文，原始长度: " + contextToCompress.length() + " 字符");
            }
            
            // 调用AI进行压缩
            String compressedSummary = ai.compressContext(contextToCompress);
            
            // 执行压缩
            session.compressContextWithSummary(keepRecent, compressedSummary);
            
            if (plugin.getConfigManager().isDebug()) {
                plugin.getLogger().info("[CLI] 已使用AI压缩上下文，摘要长度: " + compressedSummary.length() + " 字符，新的历史记录大小: " + session.getHistory().size());
            }
            
        } catch (Exception e) {
            // 压缩失败时回退到简单压缩
            plugin.getLogger().warning("[CLI] AI压缩失败，使用简单压缩: " + e.getMessage());
            session.compressContext(10);
        }
    }

    private void processAIMessage(Player player, String message) {
        UUID uuid = player.getUniqueId();
        interruptedToolCalls.remove(uuid);
        DialogueSession session = sessions.get(uuid);
        if (session == null) return;

        // 记录用户消息
        session.appendLog("USER_INPUT", message);

        session.addMessage("user", message);
        isGenerating.put(uuid, true);
        generationStates.put(uuid, GenerationStatus.THINKING);
        generationStartTimes.put(uuid, System.currentTimeMillis());

        TextComponent playerMsg = new TextComponent(ChatColor.GRAY + "◇ " + message);
        playerMsg.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cli stop"));
        playerMsg.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("点击打断")));
        player.spigot().sendMessage(playerMsg);

        if (plugin.getConfigManager().isDebug()) {
            plugin.getLogger().info("[CLI] 会话 " + player.getName() + " - 历史记录大小: " + session.getHistory().size() + ", 预计 Token: " + calculateTotalEstimatedTokens(player, session));
        }

        // 检查并压缩上下文
        if (session.getHistory().size() > 15) {
            compressContextWithAI(player, session);
        }

        if (!plugin.isEnabled()) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            // 设置重试回调，向玩家显示重试提示
            ai.setRetryCallback((statusCode, retryMessage) -> {
                if (!plugin.isEnabled()) return;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        TextComponent retryMsg;
                        if (statusCode == 429) {
                            // 429 错误使用黄色⁕ 白色
                            retryMsg = new TextComponent(ChatColor.YELLOW + "⁕ " + ChatColor.WHITE + retryMessage);
                        } else {
                            // 其他错误使用灰色⁕
                            retryMsg = new TextComponent(ChatColor.GRAY + "⁕ " + retryMessage);
                        }
                        player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.CHAT, retryMsg);
                    }
                });
            });
            
            try {
                AIResponse response = ai.chat(session, promptManager.getBaseSystemPrompt(player));
                if (!plugin.isEnabled()) return;
                Bukkit.getScheduler().runTask(plugin, () -> handleAIResponse(player, response));
            } catch (IOException e) {
                plugin.getCloudErrorReport().report(e);
                if (!plugin.isEnabled()) return;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    // 保存重试信息
                    retryInfoMap.put(uuid, new RetryInfo(session, promptManager.getBaseSystemPrompt(player), message, true));

                    String errorMsg = e.getMessage();
                    // 只显示友好的提示消息（FancyHelper > 开头的）
                    if (errorMsg != null && errorMsg.startsWith("§zFancyHelper§b§r §7> §f")) {
                        player.sendMessage(ColorUtil.translateCustomColors(errorMsg));
                    } else if (errorMsg != null && errorMsg.startsWith("FancyHelper > ")) {
                        player.sendMessage(ColorUtil.translateCustomColors("§zFancyHelper§b§r §7> §f" + errorMsg.substring("FancyHelper > ".length())));
                    } else {
                        player.sendMessage(ChatColor.RED + "⨀ AI 调用出错: " + errorMsg);
                    }
                    // 控制台日志已在 CloudFlareAI.java 中输出

                    // 显示重试按钮（与报错信息同一行）
                    TextComponent retryBtn = new TextComponent(ChatColor.WHITE + "(");
                    retryBtn.addExtra(new TextComponent(ChatColor.GREEN + "🔄"));
                    retryBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cli retry"));
                    String hoverText = "§b§l=======\n§b§l|  重试  |\n§b§l=======";
                    retryBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(ChatColor.translateAlternateColorCodes('&', hoverText))));
                    retryBtn.addExtra(new TextComponent(ChatColor.WHITE + ")"));

                    player.spigot().sendMessage(retryBtn);

                    isGenerating.put(uuid, false);
                    recordThinkingTime(uuid);
                    generationStates.put(uuid, GenerationStatus.ERROR);
                    generationStartTimes.remove(uuid);
                    // 立即清除动作栏
                    player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR, new TextComponent(""));
                    // 移除导致失败的消息，防止污染后续对话
                    session.removeLastMessage();
                });
            } catch (Throwable t) {
                plugin.getCloudErrorReport().report(t);
                if (!plugin.isEnabled()) return;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    // 保存重试信息
                    retryInfoMap.put(uuid, new RetryInfo(session, promptManager.getBaseSystemPrompt(player), message, true));

                    player.sendMessage(ChatColor.RED + "⨀ 系统内部错误: " + t.getMessage());
                    plugin.getLogger().warning("系统内部错误: " + t.getMessage());

                    // 显示重试按钮（与报错信息同一行）
                    TextComponent retryBtn = new TextComponent(ChatColor.WHITE + "(");
                    retryBtn.addExtra(new TextComponent(ChatColor.GREEN + "🔄"));
                    retryBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cli retry"));
                    String hoverText = "§b§l=======\n§b§l|  重试  |\n§b§l=======";
                    retryBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(ChatColor.translateAlternateColorCodes('&', hoverText))));
                    retryBtn.addExtra(new TextComponent(ChatColor.WHITE + ")"));

                    player.spigot().sendMessage(retryBtn);

                    isGenerating.put(uuid, false);
                    recordThinkingTime(uuid);
                    generationStates.put(uuid, GenerationStatus.ERROR);
                    generationStartTimes.remove(uuid);
                    // 立即清除动作栏
                    player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR, new TextComponent(""));
                    // 移除导致失败的消息，防止污染后续对话
                    session.removeLastMessage();
                });
            } finally {
                // 清除重试回调
                ai.clearRetryCallback();
            }
        });
    }

    private void handleAIResponse(Player player, AIResponse aiResponse) {
        UUID uuid = player.getUniqueId();
        DialogueSession session = sessions.get(uuid);
        if (session == null) return;

        // 更新 Token 统计
        if (aiResponse.getPromptTokens() > 0 || aiResponse.getCompletionTokens() > 0) {
            session.addInputTokens(aiResponse.getPromptTokens());
            session.addOutputTokens(aiResponse.getCompletionTokens());
            if (plugin.getConfigManager().isDebug()) {
                plugin.getLogger().info("[CLI] Token Usage - Input: " + aiResponse.getPromptTokens() + 
                    ", Output: " + aiResponse.getCompletionTokens() + 
                    ", Total Input: " + session.getTotalInputTokens() + 
                    ", Total Output: " + session.getTotalOutputTokens());
            }
        }

        // 如果生成已被打断，则丢弃响应
        if (!isGenerating.getOrDefault(uuid, false)) {
            if (plugin.getConfigManager().isDebug()) {
                plugin.getLogger().info("[CLI] 由于被中断，丢弃了 " + player.getName() + " 的 AI 响应。");
            }
            return;
        }

        // 收到 AI 回复，立即停止计时
        recordThinkingTime(uuid);
        generationStates.put(uuid, GenerationStatus.COMPLETED);
        generationStartTimes.remove(uuid);

        String response = aiResponse.getContent();
        String thoughtContent = aiResponse.getThought() != null ? aiResponse.getThought() : "";

        if (plugin.getConfigManager().isDebug()) {
            plugin.getLogger().info("[CLI] 已收到 " + player.getName() + " 的 AI 响应 (长度: " + response.length() + ")");
        }

        // 如果 response 里面还有 <thought>、<thinking> 或 <think> 标签（API 可能没拆分出来），则继续尝试提取
        java.util.regex.Matcher thoughtMatcher = java.util.regex.Pattern.compile("(?s)<(thought|thinking)>(.*?)</\\1>").matcher(response);
        if (thoughtMatcher.find()) {
            if (thoughtContent.isEmpty()) {
                thoughtContent = thoughtMatcher.group(2);
            }
            response = response.replaceAll("(?s)<(thought|thinking)>.*?</\\1>", "");
        } else {
            // 针对某些模型可能使用 <think></think> 标签
            java.util.regex.Matcher thinkTagMatcher = java.util.regex.Pattern.compile("(?s)<think>(.*?)</think>").matcher(response);
            if (thinkTagMatcher.find()) {
                if (thoughtContent.isEmpty()) {
                    thoughtContent = thinkTagMatcher.group(1);
                }
                response = response.replaceAll("(?s)<think>.*?</think>", "");
            } else {
                // 针对某些模型可能直接在正文中用 Markdown 块或特定标记显示思考过程
                // 尝试匹配 ```thought ... ``` 块
                java.util.regex.Matcher mdThoughtMatcher = java.util.regex.Pattern.compile("(?s)```thought\n?(.*?)\n?```").matcher(response);
                if (mdThoughtMatcher.find()) {
                    if (thoughtContent.isEmpty()) {
                        thoughtContent = mdThoughtMatcher.group(1);
                    }
                    response = response.replaceAll("(?s)```thought\n?.*?\n?```", "");
                }
            }
        }
        
        // 移除 Markdown 风格的 Thought: 块或类似文本
        String cleanResponse = response.replaceAll("(?i)^Thought:.*?\n", "");
        cleanResponse = cleanResponse.replaceAll("(?i)^思考过程:.*?\n", "");
        cleanResponse = cleanResponse.trim();
        
        // 更新 session 中的最后一次思考内容
        String finalThought = thoughtContent.isEmpty() ? null : thoughtContent;
        session.setLastThought(finalThought);

        // 将 AI 的回复加入历史记录，并关联当前的思考内容
        session.addMessage("assistant", cleanResponse, finalThought);
        
        // 计算思考内容的 Token
        if (!thoughtContent.isEmpty()) {
            String modelName = plugin.getConfigManager().getCloudflareModel();
            int thoughtTokens = DialogueSession.calculateTokens(thoughtContent, modelName);
            session.addThoughtTokens(thoughtTokens);
        }
        
        // 增强的工具调用提取逻辑：寻找第一个处于行首的工具调用
        // 这样可以避免 AI 在回复末尾多加一个 #over 导致前面的主要工具（如 #edit）被忽略
        String content = cleanResponse;
        String toolCall = "";

        // 定义已知工具列表
        List<String> knownTools = Arrays.asList("#end", "#exit", "#run", "#getpreset", "#ask", "#search", "#list", "#read", "#edit", "#todo", "#remember", "#forget", "#edit_memory", "#webread");

        int currentPos = 0;
        boolean foundTool = false;
        while (currentPos < cleanResponse.length()) {
            int hashIndex = cleanResponse.indexOf("#", currentPos);
            if (hashIndex == -1) break;

            // 检查是否为有效的工具调用起始位置
            // 为了增加鲁棒性，不再强制要求必须在行首，但要求前面不能是字母或数字（防止误触发，如 CSS#id）
            boolean isValidStart = true;
            if (hashIndex > 0) {
                char prev = cleanResponse.charAt(hashIndex - 1);
                if (Character.isLetterOrDigit(prev)) {
                    isValidStart = false;
                }
            }

            if (isValidStart) {
                String potentialToolPart = cleanResponse.substring(hashIndex).trim();
                for (String tool : knownTools) {
                    if (potentialToolPart.toLowerCase().startsWith(tool)) {
                        // 提取完整的工具调用，直到遇到换行符或下一个工具
                        String remainingAfterTool = potentialToolPart.substring(tool.length()).trim();

                        // 如果有冒号或空格，提取参数部分
                        if (remainingAfterTool.startsWith(":") || remainingAfterTool.startsWith(" ")) {
                            int splitIndex = remainingAfterTool.startsWith(":") ? 1 : 0;
                            remainingAfterTool = remainingAfterTool.substring(splitIndex).trim();

                            // 对于 JSON 参数（如 #todo: [...]），需要找到匹配的闭合括号
                            if (remainingAfterTool.startsWith("[")) {
                                int bracketDepth = 0;
                                int endIndex = -1;
                                for (int i = 0; i < remainingAfterTool.length(); i++) {
                                    char c = remainingAfterTool.charAt(i);
                                    if (c == '[') bracketDepth++;
                                    else if (c == ']') bracketDepth--;

                                    if (bracketDepth == 0) {
                                        endIndex = i + 1;
                                        break;
                                    }
                                }
                                if (endIndex != -1) {
                                    toolCall = tool + ":" + remainingAfterTool.substring(0, endIndex);
                                } else {
                                    // 没有找到闭合括号，提取到行尾
                                    int lineEnd = remainingAfterTool.indexOf('\n');
                                    if (lineEnd != -1) {
                                        toolCall = tool + ":" + remainingAfterTool.substring(0, lineEnd);
                                    } else {
                                        toolCall = potentialToolPart;
                                    }
                                }
                            } else {
                                // 对于普通参数，提取到行尾或遇到下一个工具
                                int lineEnd = remainingAfterTool.indexOf('\n');
                                int nextToolPos = -1;
                                for (String nextTool : knownTools) {
                                    int pos = remainingAfterTool.toLowerCase().indexOf(nextTool);
                                    if (pos != -1 && (nextToolPos == -1 || pos < nextToolPos)) {
                                        nextToolPos = pos;
                                    }
                                }

                                int paramEnd = lineEnd;
                                if (nextToolPos != -1 && (paramEnd == -1 || nextToolPos < paramEnd)) {
                                    paramEnd = nextToolPos;
                                }

                                if (paramEnd != -1) {
                                    toolCall = tool + ":" + remainingAfterTool.substring(0, paramEnd).trim();
                                } else {
                                    toolCall = potentialToolPart;
                                }
                            }
                        } else {
                            toolCall = tool;
                        }
                        content = cleanResponse.substring(0, hashIndex).trim();
                        foundTool = true;
                        break;
                    }
                }
            }
            if (foundTool) break;
            currentPos = hashIndex + 1;
        }

        // 展示 Fancy 内容
        if (!content.isEmpty()) {
            displayFancyContent(player, content, finalThought);
        } else if (finalThought != null) {
            // 如果只有思考过程而没有正文内容（例如纯工具调用前的思考），也显示思考按钮
            displayFancyContent(player, "", finalThought);
        }

        // 处理工具调用
        if (!toolCall.isEmpty()) {
            executeTool(player, toolCall);
        } else {
            // 检查响应是否被截断
            if (aiResponse.isTruncated()) {
                // 显示截断提示
                player.sendMessage(ChatColor.YELLOW + "⨀ 响应被截断，正在继续生成...");
                // 自动继续生成
                continueGeneration(player, session);
            } else {
                isGenerating.put(uuid, false);
                generationStates.put(uuid, GenerationStatus.COMPLETED);
                checkTokenWarning(player, session);
            }
        }
    }

    /**
     * 继续生成被截断的响应
     */
    private void continueGeneration(Player player, DialogueSession session) {
        UUID uuid = player.getUniqueId();
        
        // 设置生成状态
        isGenerating.put(uuid, true);
        generationStates.put(uuid, GenerationStatus.THINKING);
        generationStartTimes.put(uuid, System.currentTimeMillis());
        
        // 添加一个提示消息，让AI知道继续生成
        session.addMessage("user", "请继续生成剩余的内容");
        
        // 异步调用 AI 继续生成
        if (!plugin.isEnabled()) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            // 设置重试回调，向玩家显示重试提示
            ai.setRetryCallback((statusCode, retryMessage) -> {
                if (!plugin.isEnabled()) return;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        TextComponent retryMsg;
                        if (statusCode == 429) {
                            // 429 错误使用黄色⁕ 白色
                            retryMsg = new TextComponent(ChatColor.YELLOW + "⁕ " + ChatColor.WHITE + retryMessage);
                        } else {
                            // 其他错误使用灰色⁕
                            retryMsg = new TextComponent(ChatColor.GRAY + "⁕ " + retryMessage);
                        }
                        player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.CHAT, retryMsg);
                    }
                });
            });
            
            try {
                AIResponse response = ai.chat(session, promptManager.getBaseSystemPrompt(player));
                if (!plugin.isEnabled()) return;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    handleAIResponse(player, response);
                });
            } catch (IOException e) {
                plugin.getCloudErrorReport().report(e);
                if (!plugin.isEnabled()) return;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    String errorMsg = e.getMessage();
                    // 只显示友好的提示消息（FancyHelper > 开头的）
                    if (errorMsg != null && errorMsg.startsWith("§zFancyHelper§b§r §7> §f")) {
                        player.sendMessage(ColorUtil.translateCustomColors(errorMsg));
                    } else if (errorMsg != null && errorMsg.startsWith("FancyHelper > ")) {
                        player.sendMessage(ColorUtil.translateCustomColors("§zFancyHelper§b§r §7> §f" + errorMsg.substring("FancyHelper > ".length())));
                    } else {
                        player.sendMessage(ChatColor.RED + "⨀ 继续生成失败: " + errorMsg);
                    }
                    // 控制台日志已在 CloudFlareAI.java 中输出
                    isGenerating.put(uuid, false);
                    generationStates.put(uuid, GenerationStatus.ERROR);
                    generationStartTimes.remove(uuid);
                });
            } finally {
                // 清除重试回调
                ai.clearRetryCallback();
            }
        });
    }

    private void checkTokenWarning(Player player, DialogueSession session) {
        int estimatedTokens = calculateTotalEstimatedTokens(player, session);
        int maxTokens = 12800;
        int remaining = maxTokens - estimatedTokens;

        if (remaining < plugin.getConfigManager().getTokenWarningThreshold()) {
            player.sendMessage(ChatColor.YELLOW + "⨀ 剩余上下文长度不足 ，Fancy 可能会遗忘较早的对话内容来保证对话继续。");
        }
    }

    /**
     * 计算当前会话的预计总 Token 数（包括 System Prompt 和历史记录）
     */
    private int calculateTotalEstimatedTokens(Player player, DialogueSession session) {
        String modelName = plugin.getConfigManager().getCloudflareModel();

        // 1. 计算 System Prompt Token
        String systemPrompt = promptManager.getBaseSystemPrompt(player);
        // System Prompt 是一条完整的消息: <|im_start|>system\n{content}<|im_end|>\n
        int systemPromptTokens = DialogueSession.calculateTokens(systemPrompt, modelName);
        systemPromptTokens += DialogueSession.calculateTokens("system", modelName);
        systemPromptTokens += 3; // per-message overhead

        // 2. 获取历史记录 Token
        int historyTokens = session.getEstimatedTokens(modelName);
        
        // 3. 回复引导 (Reply Primer): <|im_start|>assistant\n
        int replyPrimerTokens = 3;

        return systemPromptTokens + historyTokens + replyPrimerTokens;
    }

    private void executeTool(Player player, String toolCall) {
        UUID uuid = player.getUniqueId();
        DialogueSession session = sessions.get(uuid);
        if (session == null) return;

        // --- 防死循环检测逻辑 ---
        if (session.isAntiLoopExempted()) {
            // 已豁免，仅记录
            session.addToolCall(toolCall);
        } else {
            List<String> toolHistory = session.getToolCallHistory();
            int thresholdCount = plugin.getConfigManager().getAntiLoopThresholdCount();
            double similarityThreshold = plugin.getConfigManager().getAntiLoopSimilarityThreshold();
            int maxChainCount = plugin.getConfigManager().getAntiLoopMaxChainCount();

            // 1. 连续相似调用检测
            if (toolHistory.size() >= thresholdCount - 1) {
                int similarCount = 1; // 当前这次调用算作第 1 个
                for (int i = toolHistory.size() - 1; i >= 0 && similarCount < thresholdCount; i--) {
                    double similarity = calculateSimilarity(toolCall, toolHistory.get(i));
                    if (similarity >= similarityThreshold) {
                        similarCount++;
                    } else {
                        break; // 必须是连续的
                    }
                }

                if (similarCount >= thresholdCount) {
                    plugin.getLogger().warning("[CLI] 检测到 " + player.getName() + " 的潜在死循环: 连续 " + thresholdCount + " 次相似的工具调用。");
                    
                    // 仍然显示本次工具调用
                    String toolName = toolCall.split(":", 2)[0];
                    String args = toolCall.contains(":") ? toolCall.split(":", 2)[1] : "";
                    if ("#webread".equals(toolName)) {
                        player.sendMessage(ChatColor.GOLD + "⇒ Fancy 尝试调用: " + ChatColor.WHITE + args.trim());
                    } else {
                        player.sendMessage(ChatColor.GOLD + "⇒ Fancy 尝试调用: " + ChatColor.WHITE + toolName + (args.isEmpty() ? "" : " " + args));
                    }

                    player.sendMessage(ChatColor.RED + "⨀ 检测到 Fancy 可能陷入了重复操作的死循环。");
                    
                    // 显示“不再打断”按钮
                    TextComponent exemptMsg = new TextComponent(ChatColor.YELLOW + "⇒ 已自动打断。如果您确认这是正常操作，点击 ");
                    TextComponent btn = new TextComponent(ChatColor.GREEN + "[ 本次对话不再打断 ]");
                    btn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cli exempt_anti_loop"));
                    btn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("点击后本次会话将不再触发自动打断")));
                    exemptMsg.addExtra(btn);
                    player.spigot().sendMessage(exemptMsg);
                    
                    interruptedToolCalls.put(uuid, toolCall);
                    isGenerating.put(uuid, false);
                    generationStates.put(uuid, GenerationStatus.CANCELLED);
                    generationStartTimes.remove(uuid);
                    player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR, new TextComponent(""));
                    return;
                }
            }

            // 2. 连续调用次数上限检测
            if (session.getCurrentChainToolCount() >= maxChainCount) {
                plugin.getLogger().warning("[CLI] 检测到 " + player.getName() + " 的工具链过长: 连续 " + session.getCurrentChainToolCount() + " 次工具调用。");
                
                // 仍然显示本次工具调用
                String toolName = toolCall.split(":", 2)[0];
                String args = toolCall.contains(":") ? toolCall.split(":", 2)[1] : "";
                if ("#webread".equals(toolName)) {
                    player.sendMessage(ChatColor.GOLD + "⇒ Fancy 尝试调用: " + ChatColor.WHITE + args.trim());
                } else {
                    player.sendMessage(ChatColor.GOLD + "⇒ Fancy 尝试调用: " + ChatColor.WHITE + toolName + (args.isEmpty() ? "" : " " + args));
                }

                player.sendMessage(ChatColor.YELLOW + "⨀ 识别到Fancy重复调用" + maxChainCount + "次相似操作，请优化提示词。");
                
                // 显示“不再打断”按钮
                TextComponent exemptMsg = new TextComponent(ChatColor.YELLOW + "⇒ 发送任意消息继续对话。如果你认为这是正常操作，点击 ");
                TextComponent btn = new TextComponent(ChatColor.GREEN + "[本次对话不再打断]");
                btn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cli exempt_anti_loop"));
                btn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("点击后本次会话将不再触发自动打断")));
                exemptMsg.addExtra(btn);
                player.spigot().sendMessage(exemptMsg);
                
                interruptedToolCalls.put(uuid, toolCall);
                isGenerating.put(uuid, false);
                generationStates.put(uuid, GenerationStatus.COMPLETED);
                generationStartTimes.remove(uuid);
                player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR, new TextComponent(""));
                return;
            }
            
            // 记录本次工具调用
            session.addToolCall(toolCall);
        }
        // --- 检测逻辑结束 ---

        // 如果该工具之前被中断过且现在继续执行，清除记录
        interruptedToolCalls.remove(uuid);

        // 委托给 ToolExecutor 执行
        boolean toolSuccess = toolExecutor.executeTool(player, toolCall, session);

        if (session != null) {
            if (toolSuccess) {
                session.incrementToolSuccess();
            } else {
                session.incrementToolFailure();
            }
        }
    }

    public void feedbackToAI(Player player, String feedback) {
        UUID uuid = player.getUniqueId();
        DialogueSession session = sessions.get(uuid);
        if (session == null) return;

        session.addMessage("user", feedback);
        
        // 记录反馈后的 Token 估算
        int estimatedTokens = calculateTotalEstimatedTokens(player, session);
        if (plugin.getConfigManager().isDebug()) {
            plugin.getLogger().info("[CLI] Feedback added. Session size: " + session.getHistory().size() + ", Estimated Tokens for next request: " + estimatedTokens);
        }

        isGenerating.put(uuid, true);
        generationStates.put(uuid, GenerationStatus.THINKING);
        generationStartTimes.put(uuid, System.currentTimeMillis());

        // 工具返回信息不显示给玩家，仅在日志记录并触发 AI 思考
        if (plugin.getConfigManager().isDebug()) {
            plugin.getLogger().info("[CLI] Feedback sent to AI for " + player.getName() + ": " + feedback);
        }

        // 异步调用 AI，不显示 "Thought..." 提示，因为这是后台自动反馈
        if (!plugin.isEnabled()) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            final String systemPrompt = promptManager.getBaseSystemPrompt(player); // 在 try 块外部定义
            
            // 设置重试回调，向玩家显示重试提示
            ai.setRetryCallback((statusCode, retryMessage) -> {
                if (!plugin.isEnabled()) return;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        TextComponent retryMsg;
                        if (statusCode == 429) {
                            // 429 错误使用黄色⁕ 白色
                            retryMsg = new TextComponent(ChatColor.YELLOW + "⁕ " + ChatColor.WHITE + retryMessage);
                        } else {
                            // 其他错误使用灰色⁕
                            retryMsg = new TextComponent(ChatColor.GRAY + "⁕ " + retryMessage);
                        }
                        player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.CHAT, retryMsg);
                    }
                });
            });
            
            try {
                    AIResponse response = ai.chat(session, systemPrompt);

                if (!plugin.isEnabled()) return;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    handleAIResponse(player, response);
                });
            } catch (IOException e) {
                if (!plugin.isEnabled()) return;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    // 保存重试信息
                    retryInfoMap.put(uuid, new RetryInfo(session, systemPrompt, feedback, false));

                    String errorMsg = e.getMessage();
                    // 只显示友好的提示消息（FancyHelper > 开头的）
                    if (errorMsg != null && errorMsg.startsWith("§zFancyHelper§b§r §7> §f")) {
                        player.sendMessage(ColorUtil.translateCustomColors(errorMsg));
                    } else if (errorMsg != null && errorMsg.startsWith("FancyHelper > ")) {
                        player.sendMessage(ColorUtil.translateCustomColors("§zFancyHelper§b§r §7> §f" + errorMsg.substring("FancyHelper > ".length())));
                    } else {
                        player.sendMessage(ChatColor.RED + "⨀ AI 调用出错: " + errorMsg);
                    }
                    // 控制台日志已在 CloudFlareAI.java 中输出

                    // 显示重试按钮（与报错信息同一行）
                    TextComponent retryBtn = new TextComponent(ChatColor.WHITE + "(");
                    retryBtn.addExtra(new TextComponent(ChatColor.GREEN + "🔄"));
                    retryBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cli retry"));
                    String hoverText = "§b§l=======\n§b§l|  重试  |\n§b§l=======";
                    retryBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(ChatColor.translateAlternateColorCodes('&', hoverText))));
                    retryBtn.addExtra(new TextComponent(ChatColor.WHITE + ")"));

                    player.spigot().sendMessage(retryBtn);

                    isGenerating.put(uuid, false);
                    recordThinkingTime(uuid);
                    generationStates.put(uuid, GenerationStatus.ERROR);
                    generationStartTimes.remove(uuid);
                    // 立即清除动作栏
                    player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR, new TextComponent(""));
                    // 移除导致失败的消息，防止污染后续对话
                    session.removeLastMessage();
                });
            } catch (Throwable t) {
                plugin.getCloudErrorReport().report(t);
                if (!plugin.isEnabled()) return;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    // 保存重试信息
                    retryInfoMap.put(uuid, new RetryInfo(session, systemPrompt, feedback, false));

                    player.sendMessage(ChatColor.RED + "⨀ 系统内部错误: " + t.getMessage());
                    plugin.getLogger().warning("系统内部错误: " + t.getMessage());

                    // 显示重试按钮（与报错信息同一行）
                    TextComponent retryBtn = new TextComponent(ChatColor.WHITE + "(");
                    retryBtn.addExtra(new TextComponent(ChatColor.GREEN + "🔄"));
                    retryBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cli retry"));
                    String hoverText = "§b§l=======\n§b§l|  重试  |\n§b§l=======";
                    retryBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(ChatColor.translateAlternateColorCodes('&', hoverText))));
                    retryBtn.addExtra(new TextComponent(ChatColor.WHITE + ")"));

                    player.spigot().sendMessage(retryBtn);

                    isGenerating.put(uuid, false);
                    recordThinkingTime(uuid);
                    generationStates.put(uuid, GenerationStatus.ERROR);
                    generationStartTimes.remove(uuid);
                    // 立即清除动作栏
                    player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR, new TextComponent(""));
                    // 移除导致失败的消息，防止污染后续对话
                    session.removeLastMessage();
                });
            } finally {
                // 清除重试回调
                ai.clearRetryCallback();
            }
        });
    }

    /**
     * 计算两个字符串的相似度 (基于 Levenshtein 距离)
     * @param s1 字符串1
     * @param s2 字符串2
     * @return 相似度 (0.0 - 1.0)
     */
    private double calculateSimilarity(String s1, String s2) {
        if (s1 == null || s2 == null) return 0.0;
        if (s1.equals(s2)) return 1.0;
        if (s1.isEmpty() || s2.isEmpty()) return 0.0;

        int len1 = s1.length();
        int len2 = s2.length();
        int[][] dp = new int[len1 + 1][len2 + 1];

        for (int i = 0; i <= len1; i++) dp[i][0] = i;
        for (int j = 0; j <= len2; j++) dp[0][j] = j;

        for (int i = 1; i <= len1; i++) {
            for (int j = 1; j <= len2; j++) {
                int cost = (s1.charAt(i - 1) == s2.charAt(j - 1)) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost);
            }
        }

        int distance = dp[len1][len2];
        return 1.0 - ((double) distance / Math.max(len1, len2));
    }

    private void displayFancyContent(Player player, String content, String currentThought) {
        // 获取当前 session
        DialogueSession session = sessions.get(player.getUniqueId());
        
        // 如果本次回复包含思考过程，或者历史最后一条消息有思考过程，显示按钮
        if (session != null) {
            String thoughtToShow = currentThought;
            long thoughtMessageId = -1;
            long thoughtThinkingTimeMs = session.getLastThinkingTimeMs();

            List<DialogueSession.Message> history = session.getHistory();
            if (thoughtToShow == null && !history.isEmpty()) {
                // 如果当前没有提取到思考过程，尝试查找历史最后一条 assistant 消息
                for (int i = history.size() - 1; i >= 0; i--) {
                    if ("assistant".equalsIgnoreCase(history.get(i).getRole())) {
                        if (history.get(i).hasThought()) {
                            thoughtToShow = history.get(i).getThought();
                            thoughtMessageId = history.get(i).getId();
                            thoughtThinkingTimeMs = history.get(i).getThinkingTimeMs();
                        }
                        break;
                    }
                }
            } else if (thoughtToShow != null) {
                // 如果当前提取到了思考过程，它已经被 addMessage 加入了历史
                if (!history.isEmpty()) {
                    DialogueSession.Message last = history.get(history.size() - 1);
                    thoughtMessageId = last.getId();
                    thoughtThinkingTimeMs = last.getThinkingTimeMs();
                }
            }
            
            if (thoughtToShow != null) {
                TextComponent thoughtBtn = new TextComponent(ChatColor.GRAY + " ※ Thought");
                // 传递 messageId 以便稳定地回放对应 Thought（避免历史裁剪导致索引漂移）
                String cmd = "/cli thought" + (thoughtMessageId != -1 ? " t:" + thoughtMessageId : "");
                thoughtBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmd));
                thoughtBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(ChatColor.GRAY + "点击查看本次思考过程")));
                
                // 在 Thought 按钮右侧显示本次思考的时间
                double lastSec = thoughtThinkingTimeMs / 1000.0;
                TextComponent timeTag = new TextComponent(ChatColor.DARK_GRAY + " (" + String.format("%.1f", lastSec) + "s)");
                thoughtBtn.addExtra(timeTag);
                
                player.spigot().sendMessage(thoughtBtn);
            }
        }

        // 处理正文内容
        if (content != null && !content.trim().isEmpty()) {
            // 先处理自定义颜色代码 §x 和 §z
            content = ColorUtil.translateCustomColors(content);
            
            // 处理代码块 ```...```
            String[] codeParts = content.split("```");
            TextComponent finalMessage = new TextComponent(ChatColor.WHITE + "◆ ");
            
            for (int i = 0; i < codeParts.length; i++) {
                if (i % 2 == 1) {
                    // 代码块部分，亮蓝色显示
                    finalMessage.addExtra(ChatColor.DARK_AQUA + codeParts[i]);
                } else {
                    // 普通文本部分，进一步处理 **...** 高亮
                    String text = codeParts[i];
                    String[] highlightParts = text.split("\\*\\*");
                    
                    for (int j = 0; j < highlightParts.length; j++) {
                        if (j % 2 == 1) {
                            // 高亮部分，使用自定义亮蓝色 #30AEE5
                            // 移除内部颜色代码以确保高亮颜色生效
                            String cleanText = ChatColor.stripColor(highlightParts[j]);
                            TextComponent highlightComp = new TextComponent(cleanText);
                            highlightComp.setColor(net.md_5.bungee.api.ChatColor.of(ColorUtil.getColorZ()));
                            finalMessage.addExtra(highlightComp);
                        } else {
                            // 普通部分，白色显示
                            finalMessage.addExtra(ChatColor.WHITE + highlightParts[j]);
                        }
                    }
                }
            }
            player.spigot().sendMessage(finalMessage);
        }
    }

    /**
     * 展示特定索引或最新的思考过程
     */
    public void handleThought(Player player, String[] args) {
        DialogueSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            player.sendMessage(ColorUtil.translateCustomColors("§zFancyHelper§b§r §7> §f当前没有活动的对话。"));
            return;
        }

        String thought = null;
        long thinkingTimeMs = session.getLastThinkingTimeMs();
        boolean hasExplicitTarget = args.length > 0;
        if (hasExplicitTarget) {
            String target = args[0];

            if (target.startsWith("t:")) {
                try {
                    long messageId = Long.parseLong(target.substring(2));
                    DialogueSession.ThoughtSnapshot snapshot = session.getThoughtSnapshot(messageId);
                    if (snapshot != null) {
                        thought = snapshot.getThought();
                        thinkingTimeMs = snapshot.getThinkingTimeMs();
                    } else {
                        DialogueSession.Message message = session.findMessageById(messageId);
                        if (message != null && message.hasThought()) {
                            thought = message.getThought();
                            thinkingTimeMs = message.getThinkingTimeMs();
                        }
                    }
                } catch (NumberFormatException ignored) {}
            } else {
                try {
                    int index = Integer.parseInt(target);
                    List<DialogueSession.Message> history = session.getHistory();
                    if (index >= 0 && index < history.size()) {
                        DialogueSession.Message message = history.get(index);
                        if (message.hasThought()) {
                            thought = message.getThought();
                            thinkingTimeMs = message.getThinkingTimeMs();
                        }
                    }
                } catch (NumberFormatException ignored) {}
            }
        }

        // 仅当没有指定目标时，才回退到最后一次思考
        if (!hasExplicitTarget && thought == null) {
            thought = session.getLastThought();
        }

        if (thought == null) {
            player.sendMessage(ColorUtil.translateCustomColors("§zFancyHelper§b§r §7> §f找不到对应的思考过程。"));
            return;
        }

        // 创建书本
        org.bukkit.inventory.ItemStack book = new org.bukkit.inventory.ItemStack(org.bukkit.Material.WRITTEN_BOOK);
        org.bukkit.inventory.meta.BookMeta meta = (org.bukkit.inventory.meta.BookMeta) book.getItemMeta();
        
        if (meta != null) {
            meta.setTitle("Fancy Thought");
            meta.setAuthor("Fancy");

            // 获取本次思考的时长
            double lastThinkingSec = thinkingTimeMs / 1000.0;
            String timePrefix = ChatColor.DARK_GRAY + "Thought (" + String.format("%.1f", lastThinkingSec) + "s)\n\n" + ChatColor.RESET;
            
            // 将 **文本** 转换为 Minecraft 粗体格式 §l文本§r
            String formattedThought = convertMarkdownBoldToMinecraft(thought);
            String fullThought = timePrefix + formattedThought;
            
            // 分页处理（书本每页约 256 字符，但实际受行数限制，使用 128 作为安全边距）
            List<String> pages = new ArrayList<>();
            int pageSize = 128;
            for (int i = 0; i < fullThought.length(); i += pageSize) {
                pages.add(fullThought.substring(i, Math.min(i + pageSize, fullThought.length())));
            }
            
            if (pages.isEmpty()) pages.add("");
            meta.setPages(pages);
            book.setItemMeta(meta);
            
            // 打开书本
            player.openBook(book);
        }
    }

    /**
     * 将 Markdown 粗体语法 **文本** 转换为 Minecraft 颜色代码格式 §l文本§r
     * @param text 原始文本
     * @return 转换后的文本
     */
    private String convertMarkdownBoldToMinecraft(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        // 使用正则表达式匹配 **文本** 并替换为 §l文本§r
        // 使用非贪婪匹配避免跨多个粗体块的问题
        return text.replaceAll("\\*\\*(.+?)\\*\\*", "§l$1§r");
    }

    private void sendAgreement(Player player) {
        player.sendMessage(ChatColor.GRAY + "=================");
        player.sendMessage(ChatColor.WHITE + "FancyHelper 用户协议");
        player.sendMessage(ChatColor.WHITE + "在进入 FancyHelper 之前，您需要阅读并同意用户协议。");
        // player.sendMessage("");
        TextComponent message = new TextComponent(ChatColor.WHITE + "请点击 ");
        TextComponent bookBtn = new TextComponent(ChatColor.AQUA + "[阅读协议]");
        bookBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cli read"));
        bookBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(ChatColor.AQUA + "点击阅读 FancyHelper 用户协议")));
        
        message.addExtra(bookBtn);
        message.addExtra(new TextComponent(ChatColor.WHITE + " 并在阅读完成后发送 "));
        
        TextComponent agreeBtn = new TextComponent(ChatColor.GREEN + "agree");
        agreeBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cli agree"));
        agreeBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(ChatColor.GREEN + "点击直接同意协议")));
        
        message.addExtra(agreeBtn);
        message.addExtra(new TextComponent(ChatColor.WHITE + " 表示同意并继续。"));
        
        player.spigot().sendMessage(message);
        player.sendMessage(ChatColor.GRAY + "=================");
    }

    /**
     * 为玩家打开 EULA 虚拟书本。
     */
    public void openEulaBook(Player player) {
        player.openBook(plugin.getEulaManager().getEulaBook());
    }

    /**
     * 打开 待办列表 书本
     * @param player 玩家
     */
    public void openTodoBook(Player player) {
        player.openBook(plugin.getTodoManager().getTodoBook(player));
    }

    private void sendEnterMessage(Player player) {
        UUID uuid = player.getUniqueId();
        DialogueSession session = sessions.get(uuid);
        DialogueSession.Mode mode = session != null ? session.getMode() : DialogueSession.Mode.NORMAL;

        player.sendMessage(ChatColor.GRAY + "==================");
        player.sendMessage("");
        
        TextComponent message = new TextComponent(ChatColor.WHITE + "Chatting with Fancy ");
        
        TextComponent modeTag;
        if (mode == DialogueSession.Mode.NORMAL) {
            modeTag = new TextComponent(ChatColor.GREEN + " (Normal) ");
        } else if (mode == DialogueSession.Mode.SMART) {
            modeTag = new TextComponent(ChatColor.BLUE + " (SMART) ");
        } else {
            modeTag = new TextComponent(ChatColor.RED + " (YOLO) ");
        }
        modeTag.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("点击选择模式")));
        modeTag.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cli gui mode"));
        message.addExtra(modeTag);

        TextComponent settingsBtn = new TextComponent(ChatColor.GRAY + "[Settings]");
        settingsBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(ChatColor.GRAY + "点击打开工具设置")));
        settingsBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cli settings"));
        message.addExtra(settingsBtn);
        
        player.spigot().sendMessage(message);
        player.sendMessage("");
        player.sendMessage(ChatColor.GRAY + "==================");
    }

    private void sendExitMessage(Player player) {
        UUID uuid = player.getUniqueId();
        DialogueSession session = sessions.get(uuid);
        
        long totalTokens = 0;
        long inputTokens = 0;
        long outputTokens = 0;
        
        if (session != null) {
            inputTokens = session.getTotalInputTokens();
            outputTokens = session.getTotalOutputTokens();
            totalTokens = inputTokens + outputTokens;
        }

        long durationMs = session != null ? System.currentTimeMillis() - session.getStartTime() : 0;
        double durationSec = durationMs / 1000.0;
        
        // 获取思考总时长
        double thinkingSec = session != null ? session.getTotalThinkingTimeMs() / 1000.0 : 0.0;

        player.sendMessage(ChatColor.GRAY + "==================");
        player.sendMessage("");
        player.sendMessage(ChatColor.WHITE + "已退出 FancyHelper");
        player.sendMessage(ChatColor.GRAY + "消耗 Token: " + totalTokens + " (In: " + inputTokens + ", Out: " + outputTokens + ")");
        player.sendMessage(ChatColor.GRAY + "总时长: " + String.format("%.1f", durationSec) + " 秒 (思考: " + String.format("%.1f", thinkingSec) + " 秒)");
        player.sendMessage("");
        player.sendMessage(ChatColor.GRAY + "==================");
    }

    /**
     * 获取当前处于 CLI 模式的玩家数量
     * 
     * @return 活跃玩家数量
     */
    public int getActivePlayersCount() {
        return activeCLIPayers.size();
    }

    public boolean isInCLI(Player player) {
        return activeCLIPayers.contains(player.getUniqueId());
    }

    // ==================== 公共访问方法（供 ToolExecutor 使用）====================

    /**
     * 设置生成状态
     */
    public void setGenerating(UUID uuid, boolean generating, GenerationStatus status) {
        isGenerating.put(uuid, generating);
        generationStates.put(uuid, status);
        if (status == GenerationStatus.THINKING || status == GenerationStatus.EXECUTING_TOOL) {
            generationStartTimes.put(uuid, System.currentTimeMillis());
        }
    }

    /**
     * 设置待处理命令
     */
    public void setPendingCommand(UUID uuid, String command) {
        pendingCommands.put(uuid, command);
    }

    /**
     * 获取待处理命令
     */
    public String getPendingCommand(UUID uuid) {
        return pendingCommands.get(uuid);
    }

    /**
     * 移除待处理命令
     */
    public void removePendingCommand(UUID uuid) {
        pendingCommands.remove(uuid);
    }

    /**
     * 获取生成状态
     */
    public GenerationStatus getGenerationState(UUID uuid) {
        return generationStates.getOrDefault(uuid, GenerationStatus.IDLE);
    }

    /**
     * 检查是否正在生成
     */
    public boolean isGenerating(UUID uuid) {
        return isGenerating.getOrDefault(uuid, false);
    }

    /**
     * 获取对话会话
     */
    public DialogueSession getSession(UUID uuid) {
        return sessions.get(uuid);
    }

    public String getLastError(UUID uuid) {
        DialogueSession session = sessions.get(uuid);
        return session != null ? session.getLastError() : null;
    }

    /**
     * 记录思考时间
     */
    public void recordThinkingTimePublic(UUID uuid) {
        recordThinkingTime(uuid);
    }

    /**
     * 清除生成开始时间
     */
    public void clearGenerationStartTime(UUID uuid) {
        generationStartTimes.remove(uuid);
    }
}
