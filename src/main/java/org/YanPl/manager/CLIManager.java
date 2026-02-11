package org.YanPl.manager;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.YanPl.FancyHelper;
import org.YanPl.api.CloudFlareAI;
import org.YanPl.model.AIResponse;
import org.YanPl.model.DialogueSession;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.scheduler.BukkitRunnable;

/**
 * CLI 模式管理器，负责管理玩家的 CLI 状态和对话流
 */
public class CLIManager {
    private final FancyHelper plugin;
    private final CloudFlareAI ai;
    private final PromptManager promptManager;
    private final Set<UUID> activeCLIPayers = new HashSet<>();
    private final Set<UUID> pendingAgreementPlayers = new HashSet<>();
    private final Set<UUID> agreedPlayers = new HashSet<>();
    private final Set<UUID> yoloAgreedPlayers = new HashSet<>();
    private final Set<UUID> yoloModePlayers = new HashSet<>();
    private final Set<UUID> pendingYoloAgreementPlayers = new HashSet<>();
    private final File agreedPlayersFile;
    private final File yoloAgreedPlayersFile;
    private final File yoloModePlayersFile;
    private final Map<UUID, DialogueSession> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> isGenerating = new ConcurrentHashMap<>();
    private final Map<UUID, GenerationStatus> generationStates = new ConcurrentHashMap<>();
    private final Map<UUID, Long> generationStartTimes = new ConcurrentHashMap<>();
    private final Map<UUID, String> pendingCommands = new ConcurrentHashMap<>();

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
        this.agreedPlayersFile = new File(plugin.getDataFolder(), "agreed_players.txt");
        this.yoloAgreedPlayersFile = new File(plugin.getDataFolder(), "yolo_agreed_players.txt");
        this.yoloModePlayersFile = new File(plugin.getDataFolder(), "yolo_mode_players.txt");
        loadAgreedPlayers();
        loadYoloAgreedPlayers();
        loadYoloModePlayers();
        startTimeoutTask();
        startThinkingTask();
    }

    private void loadAgreedPlayers() {
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

    private void loadYoloAgreedPlayers() {
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

    private void loadYoloModePlayers() {
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
                            player.sendMessage(ChatColor.YELLOW + "由于长时间未活动，已自动退出 FancyHelper。");
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
     * 启动 AI 思考状态显示任务
     */
    private void startThinkingTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
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
                            player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR, new TextComponent(message));
                            break;
                        case EXECUTING_TOOL:
                            message = ChatColor.GRAY + "....";
                            player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR, new TextComponent(message));
                            break;
                        case WAITING_CONFIRM:
                            message = ChatColor.YELLOW + "正在征求您的许可...";
                            player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR, new TextComponent(message));
                            break;
                        case WAITING_CHOICE:
                            message = ChatColor.AQUA + "正在征求您的意见...";
                            player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR, new TextComponent(message));
                            break;
                        case COMPLETED:
                            message = ChatColor.GREEN + "- ✓ -";
                            player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR, new TextComponent(message));
                            // 清除动作栏
                            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                                player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR, new TextComponent(""));
                            }, 20L);
                            generationStates.put(uuid, GenerationStatus.IDLE);
                            generationStartTimes.remove(uuid);
                            break;
                        case CANCELLED:
                            message = ChatColor.RED + "- ✕ -";
                            player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR, new TextComponent(message));
                            // 清除动作栏
                            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                                player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR, new TextComponent(""));
                            }, 20L);
                            generationStates.put(uuid, GenerationStatus.IDLE);
                            generationStartTimes.remove(uuid);
                            break;
                        case ERROR:
                            message = ChatColor.RED + "- ERROR -";
                            player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR, new TextComponent(message));
                            // 清除动作栏
                            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                                player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR, new TextComponent(""));
                            }, 20L);
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
     * 关闭管理器，清理资源
     */
    public void shutdown() {
        plugin.getLogger().info("[CLI] 正在关闭 CLIManager...");
        
        // 移除所有活跃的CLI玩家
        for (UUID uuid : new ArrayList<>(activeCLIPayers)) {
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
        
        plugin.getLogger().info("[CLI] CLIManager 已完成关闭。");
    }

    /**
     * 进入 CLI 模式
     */
    public void enterCLI(Player player) {
        UUID uuid = player.getUniqueId();

        // 检查 EULA 文件状态
        if (!plugin.getEulaManager().isEulaValid()) {
            player.sendMessage(ChatColor.RED + "系统错误：EULA 文件缺失或被非法改动且无法还原，请联系管理员检查权限设置。");
            plugin.getLogger().warning("[CLI] 由于 EULA 文件无效，拒绝了 " + player.getName() + " 的访问。");
            return;
        }

        plugin.getLogger().info("[CLI] 玩家 " + player.getName() + " 正在进入 FancyHelper。");
        
        // 检查用户协议
        if (!agreedPlayers.contains(uuid)) {
            plugin.getLogger().info("[CLI] 玩家 " + player.getName() + " 需要同意协议。");
            sendAgreement(player);
            pendingAgreementPlayers.add(uuid);
            return;
        }
        
        activeCLIPayers.add(uuid);
        DialogueSession session = new DialogueSession();
        // 恢复上次的模式
        if (yoloModePlayers.contains(uuid)) {
            session.setMode(DialogueSession.Mode.YOLO);
        }
        sessions.put(uuid, session);
        sendEnterMessage(player);
        
        // 触发 AI 问候
        triggerGreeting(player);
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
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try {
                // 检查玩家是否仍在线且在 CLI 模式中
                if (!activeCLIPayers.contains(uuid) || !player.isOnline()) return;

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
                // 格式：◆ [问候语]，[天蓝色玩家名]。[随机短语]
                TextComponent message = new TextComponent(ChatColor.WHITE + "◆ " + timeGreeting + "，");
                
                TextComponent playerName = new TextComponent(player.getName());
                playerName.setColor(net.md_5.bungee.api.ChatColor.AQUA); // 天蓝色
                
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
        plugin.getLogger().info("[CLI] 玩家 " + player.getName() + " 正在退出 FancyHelper。");
        sendExitMessage(player);
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
            saveYoloModeState(uuid, true); // 保存 YOLO 状态
            player.sendMessage(ChatColor.GRAY + "------------------");
            player.sendMessage(ChatColor.WHITE + "⨀ 已切换至 YOLO 模式。在该模式下，Fancy 执行命令将不再请求您的确认。");
        } else {
            session.setMode(DialogueSession.Mode.NORMAL);
            saveYoloModeState(uuid, false); // 移除 YOLO 状态
            player.sendMessage(ChatColor.GRAY + "------------------");
            player.sendMessage(ChatColor.WHITE + "⨀ 已切换至 Normal 模式。");
        }
        sendEnterMessage(player); // 重新发送页眉以显示新状态
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

    public void handleConfirm(Player player) {
        UUID uuid = player.getUniqueId();
        if (pendingCommands.containsKey(uuid)) {
            String cmd = pendingCommands.get(uuid);
            if (!"CHOOSING".equals(cmd)) {
                pendingCommands.remove(uuid);
                generationStates.put(uuid, GenerationStatus.EXECUTING_TOOL);
                if (cmd.startsWith("LS:") || cmd.startsWith("READ:") || cmd.startsWith("DIFF:")) {
                    String[] parts = cmd.split(":", 2);
                    String type = parts[0].toLowerCase();
                    String args = parts[1];
                    checkVerificationAndExecute(player, type, args);
                } else {
                    executeCommand(player, cmd);
                }
            }
        }
    }

    private void checkVerificationAndExecute(Player player, String type, String args) {
        // 检查是否被冻结
        long freezeRemaining = plugin.getVerificationManager().getPlayerFreezeRemaining(player);
        if (freezeRemaining > 0) {
            player.sendMessage(ChatColor.RED + "验证已冻结，请在 " + freezeRemaining + " 秒后重试。");
            return;
        }
        
        if (plugin.getConfigManager().isPlayerToolEnabled(player, type)) {
            executeFileOperation(player, type, args);
        } else {
            player.sendMessage(ChatColor.YELLOW + "首次使用 " + type + " 工具需要完成安全验证。");
            plugin.getVerificationManager().startVerification(player, type, () -> {
                plugin.getConfigManager().setPlayerToolEnabled(player, type, true);
                executeFileOperation(player, type, args);
            });
        }
    }

    public void handleCancel(Player player) {
        UUID uuid = player.getUniqueId();
        if (pendingCommands.containsKey(uuid)) {
            pendingCommands.remove(uuid);
            player.sendMessage(ChatColor.GRAY + "⇒ 命令已取消");
            isGenerating.put(uuid, false);
            generationStates.put(uuid, GenerationStatus.CANCELLED);
            generationStartTimes.put(uuid, System.currentTimeMillis());
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
            plugin.getLogger().info("[CLI] 玩家 " + player.getName() + " 发送了协议同意消息: " + message);
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
            plugin.getLogger().info("[CLI] 拦截到来自 " + player.getName() + " 的消息: " + message);
            if (message.equalsIgnoreCase("exit")) {
                exitCLI(player);
                return true;
            }
            if (message.equalsIgnoreCase("stop")) {
                boolean interrupted = false;
                if (isGenerating.getOrDefault(uuid, false)) {
                    isGenerating.put(uuid, false);
                    generationStates.put(uuid, GenerationStatus.CANCELLED);
                    generationStartTimes.put(uuid, System.currentTimeMillis());
                    player.sendMessage(ChatColor.YELLOW + "⇒ 已打断 Fancy 生成");
                    interrupted = true;
                }
                if (pendingCommands.containsKey(uuid)) {
                    pendingCommands.remove(uuid);
                    player.sendMessage(ChatColor.GRAY + "⇒ 已取消当前待处理的操作");
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
                    player.sendMessage(ChatColor.GREEN + "✔ 已为本次对话开启豁免模式，Fancy 将不再被自动打断。");
                }
                return true;
            }

            // 处理待确认的命令或选择
            if (pendingCommands.containsKey(uuid)) {
                String pending = pendingCommands.get(uuid);
                if (pending.equals("CHOOSING")) {
                    pendingCommands.remove(uuid);
                    player.sendMessage(ChatColor.GRAY + "◇ " + message);
                    feedbackToAI(player, "#choose_result: " + message);
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

    private void processAIMessage(Player player, String message) {
        UUID uuid = player.getUniqueId();
        DialogueSession session = sessions.get(uuid);
        if (session == null) return;

        session.addMessage("user", message);
        isGenerating.put(uuid, true);
        generationStates.put(uuid, GenerationStatus.THINKING);
        generationStartTimes.put(uuid, System.currentTimeMillis());

        player.sendMessage(ChatColor.GRAY + "◇ " + message);
        // 不再主动发送 Thought...，避免干扰用户
        // player.sendMessage(ChatColor.GRAY + "◆ Thought...");

        String modelName = plugin.getConfigManager().getCloudflareModel();
        plugin.getLogger().info("[CLI] 会话 " + player.getName() + " - 历史记录大小: " + session.getHistory().size() + ", 预计 Token: " + session.getEstimatedTokens(modelName));

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                AIResponse response = ai.chat(session, promptManager.getBaseSystemPrompt(player));
                Bukkit.getScheduler().runTask(plugin, () -> handleAIResponse(player, response));
            } catch (IOException e) {
                plugin.getCloudErrorReport().report(e);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage(ChatColor.RED + "AI 调用出错: " + e.getMessage());
                    isGenerating.put(uuid, false);
                    generationStates.put(uuid, GenerationStatus.ERROR);
                    generationStartTimes.remove(uuid);
                    // 立即清除动作栏
                    player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR, new TextComponent(""));
                    // 移除导致失败的消息，防止污染后续对话
                    session.removeLastMessage();
                });
            } catch (Throwable t) {
                plugin.getCloudErrorReport().report(t);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage(ChatColor.RED + "系统内部错误: " + t.getMessage());
                    isGenerating.put(uuid, false);
                    generationStates.put(uuid, GenerationStatus.ERROR);
                    generationStartTimes.remove(uuid);
                    // 立即清除动作栏
                    player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR, new TextComponent(""));
                });
            }
        });
    }

    private void handleAIResponse(Player player, AIResponse aiResponse) {
        UUID uuid = player.getUniqueId();
        DialogueSession session = sessions.get(uuid);
        if (session == null) return;

        // 收到 AI 回复，立即停止计时
        generationStates.put(uuid, GenerationStatus.COMPLETED);
        generationStartTimes.remove(uuid);

        // 如果生成已被打断，则丢弃响应
        if (!isGenerating.getOrDefault(uuid, false)) {
            plugin.getLogger().info("[CLI] 由于被中断，丢弃了 " + player.getName() + " 的 AI 响应。");
            return;
        }

        String response = aiResponse.getContent();
        String thoughtContent = aiResponse.getThought() != null ? aiResponse.getThought() : "";

        plugin.getLogger().info("[CLI] 已收到 " + player.getName() + " 的 AI 响应 (长度: " + response.length() + ")");

        // 如果 response 里面还有 <thought> 标签（API 可能没拆分出来），则继续尝试提取
        java.util.regex.Matcher thoughtMatcher = java.util.regex.Pattern.compile("(?s)<thought>(.*?)</thought>").matcher(response);
        if (thoughtMatcher.find()) {
            if (thoughtContent.isEmpty()) {
                thoughtContent = thoughtMatcher.group(1);
            }
            response = response.replaceAll("(?s)<thought>.*?</thought>", "");
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
        // 这样可以避免 AI 在回复末尾多加一个 #over 导致前面的主要工具（如 #diff）被忽略
        String content = cleanResponse;
        String toolCall = "";
        
        // 定义已知工具列表
        List<String> knownTools = Arrays.asList("#over", "#exit", "#run", "#get", "#choose", "#search", "#ls", "#read", "#diff");
        
        int currentPos = 0;
        boolean foundTool = false;
        while (currentPos < cleanResponse.length()) {
            int hashIndex = cleanResponse.indexOf("#", currentPos);
            if (hashIndex == -1) break;

            // 检查是否为行首调用（前面只有空白字符或处于开头）
            boolean isAtLineStart = true;
            for (int i = hashIndex - 1; i >= 0; i--) {
                char c = cleanResponse.charAt(i);
                if (c == '\n' || c == '\r') break;
                if (!Character.isWhitespace(c)) {
                    isAtLineStart = false;
                    break;
                }
            }

            if (isAtLineStart) {
                String potentialToolPart = cleanResponse.substring(hashIndex).trim();
                for (String tool : knownTools) {
                    if (potentialToolPart.toLowerCase().startsWith(tool)) {
                        toolCall = potentialToolPart;
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
            isGenerating.put(uuid, false);
            generationStates.put(uuid, GenerationStatus.COMPLETED);
            checkTokenWarning(player, session);
        }
    }

    private void checkTokenWarning(Player player, DialogueSession session) {
        String modelName = plugin.getConfigManager().getCloudflareModel();
        int estimatedTokens = session.getEstimatedTokens(modelName);
        int maxTokens = 12800;
        int remaining = maxTokens - estimatedTokens;

        if (remaining < plugin.getConfigManager().getTokenWarningThreshold()) {
            player.sendMessage(ChatColor.YELLOW + "⨀ 剩余上下文长度不足 ，Fancy 可能会遗忘较早的对话内容来保证对话继续。");
        }
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
                    player.sendMessage(ChatColor.GOLD + "⇒ Fancy 尝试调用: " + ChatColor.WHITE + toolName + (args.isEmpty() ? "" : " " + args));

                    player.sendMessage(ChatColor.RED + "⨀ 检测到 Fancy 可能陷入了重复操作的死循环。");
                    
                    // 显示“不再打断”按钮
                    TextComponent exemptMsg = new TextComponent(ChatColor.YELLOW + "⇒ 已自动打断。如果您确认这是正常操作，点击 ");
                    TextComponent btn = new TextComponent(ChatColor.GREEN + "[ 本次对话不再打断 ]");
                    btn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cli exempt_anti_loop"));
                    btn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("点击后本次会话将不再触发自动打断")));
                    exemptMsg.addExtra(btn);
                    player.spigot().sendMessage(exemptMsg);
                    
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
                player.sendMessage(ChatColor.GOLD + "⇒ Fancy 尝试调用: " + ChatColor.WHITE + toolName + (args.isEmpty() ? "" : " " + args));

                player.sendMessage(ChatColor.RED + "⨀ Fancy 已经连续执行了 " + maxChainCount + " 次操作，为了防止过度消耗，已自动暂停。");
                
                // 显示“不再打断”按钮
                TextComponent exemptMsg = new TextComponent(ChatColor.YELLOW + "⇒ 如果您需要它继续，请发送任意消息。或点击 ");
                TextComponent btn = new TextComponent(ChatColor.GREEN + "[ 本次对话不再打断 ]");
                btn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cli exempt_anti_loop"));
                btn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("点击后本次会话将不再触发自动打断")));
                exemptMsg.addExtra(btn);
                player.spigot().sendMessage(exemptMsg);
                
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

        boolean toolSuccess = true;

        // 改进的解析逻辑：兼容冒号和空格分隔符，且只分割第一次出现的标识符
        String toolName;
        String args = "";

        // 查找第一个冒号或空格的位置
        int colonIndex = toolCall.indexOf(":");
        int spaceIndex = toolCall.indexOf(" ");

        int splitIndex = -1;
        if (colonIndex != -1 && spaceIndex != -1) {
            splitIndex = Math.min(colonIndex, spaceIndex);
        } else if (colonIndex != -1) {
            splitIndex = colonIndex;
        } else if (spaceIndex != -1) {
            splitIndex = spaceIndex;
        }

        if (splitIndex != -1) {
            toolName = toolCall.substring(0, splitIndex).trim();
            args = toolCall.substring(splitIndex + 1).trim();
        } else {
            toolName = toolCall.trim();
        }

        plugin.getLogger().info("[CLI] 正在为 " + player.getName() + " 执行工具: " + toolName + " (参数: " + args + ")");

        // 统一转换为小写进行匹配
        String lowerToolName = toolName.toLowerCase();

        // 展示给玩家时只显示工具名（如果不是 search, run 或 over 这种有自己显示逻辑或不需要显示的工具）
        if (!lowerToolName.equals("#search") && !lowerToolName.equals("#run") && !lowerToolName.equals("#over") && !lowerToolName.equals("#ls") && !lowerToolName.equals("#read") && !lowerToolName.equals("#diff") && !lowerToolName.equals("#exit")) {
            player.sendMessage(ChatColor.GRAY + "〇 " + toolName);
        } else if (lowerToolName.equals("#ls")) {
            player.sendMessage(ChatColor.GRAY + "〇 正在列出目录: " + ChatColor.WHITE + args);
        } else if (lowerToolName.equals("#read")) {
            player.sendMessage(ChatColor.GRAY + "〇 正在读取文件: " + ChatColor.WHITE + args);
        } else if (lowerToolName.equals("#diff")) {
            player.sendMessage(ChatColor.GRAY + "〇 正在修改文件: " + ChatColor.WHITE + args.split("\\|")[0].trim());
        } else if (lowerToolName.equals("#exit")) {
            player.sendMessage(ChatColor.GRAY + "〇 Exiting...");
        }

        switch (lowerToolName) {
            case "#over":
                isGenerating.put(uuid, false);
                generationStates.put(uuid, GenerationStatus.COMPLETED);
                break;
            case "#exit":
                exitCLI(player);
                break;
            case "#run":
                if (args.isEmpty()) {
                    player.sendMessage(ChatColor.RED + "错误: #run 工具需要提供命令参数");
                    feedbackToAI(player, "#error: #run 工具需要提供命令参数，例如 #run: say hello");
                    toolSuccess = false;
                } else {
                    handleRunTool(player, args);
                }
                break;
            case "#ls":
                handleFileTool(player, "ls", args);
                break;
            case "#read":
                handleFileTool(player, "read", args);
                break;
            case "#diff":
                handleFileTool(player, "diff", args);
                break;
            case "#get":
                handleGetTool(player, args);
                break;
            case "#choose":
                handleChooseTool(player, args);
                break;
            case "#search":
                handleSearchTool(player, args);
                break;
            default:
                player.sendMessage(ChatColor.RED + "未知工具: " + toolName);
                feedbackToAI(player, "#error: 未知工具 " + toolName + "。请仅使用系统提示中定义的工具。");
                toolSuccess = false;
                break;
        }

        if (session != null) {
            if (toolSuccess) {
                session.incrementToolSuccess();
            } else {
                session.incrementToolFailure();
            }
        }
    }

    private void handleRunTool(Player player, String command) {
        UUID uuid = player.getUniqueId();
        DialogueSession session = sessions.get(uuid);
        
        // 自动过滤掉领先的斜杠 /
        String cleanCommand = command.startsWith("/") ? command.substring(1) : command;

        // 如果是 YOLO 模式，直接执行
        if (session != null && session.getMode() == DialogueSession.Mode.YOLO) {
            player.sendMessage(ChatColor.GOLD + "⇒ YOLO 执行 " + ChatColor.WHITE + cleanCommand);
            generationStates.put(uuid, GenerationStatus.EXECUTING_TOOL);
            executeCommand(player, cleanCommand);
            return;
        }
        
        pendingCommands.put(uuid, cleanCommand);
        generationStates.put(uuid, GenerationStatus.WAITING_CONFIRM);

        sendConfirmButtons(player, cleanCommand);
    }

    private void handleFileTool(Player player, String type, String args) {
        UUID uuid = player.getUniqueId();
        DialogueSession session = sessions.get(uuid);
        
        // 如果是 YOLO 模式，直接执行
        if (session != null && session.getMode() == DialogueSession.Mode.YOLO) {
            String actionDesc = type.equals("ls") ? "列出目录" : (type.equals("read") ? "读取文件" : "修改文件");
            player.sendMessage(ChatColor.GOLD + "⇒ YOLO " + actionDesc + " " + ChatColor.WHITE + args);
            
            // 检查是否被冻结
            long freezeRemaining = plugin.getVerificationManager().getPlayerFreezeRemaining(player);
            if (freezeRemaining > 0) {
                player.sendMessage(ChatColor.RED + "验证已冻结，请在 " + freezeRemaining + " 秒后重试。");
                return;
            }
            
            // YOLO 模式下也需要检查权限开启，但不需要手动确认
            if (plugin.getConfigManager().isPlayerToolEnabled(player, type)) {
                generationStates.put(uuid, GenerationStatus.EXECUTING_TOOL);
                executeFileOperation(player, type, args);
            } else {
                player.sendMessage(ChatColor.YELLOW + "检测到 YOLO 模式调用 " + type + "，但该工具尚未完成首次验证。");
                plugin.getVerificationManager().startVerification(player, type, () -> {
                    plugin.getConfigManager().setPlayerToolEnabled(player, type, true);
                    generationStates.put(uuid, GenerationStatus.EXECUTING_TOOL);
                    executeFileOperation(player, type, args);
                });
            }
            return;
        }
        
        String pendingStr = type.toUpperCase() + ":" + args;
        pendingCommands.put(uuid, pendingStr);
        generationStates.put(uuid, GenerationStatus.WAITING_CONFIRM);

        String actionDesc = type.equals("ls") ? "列出目录" : (type.equals("read") ? "读取文件" : "修改文件内容");
        sendConfirmButtons(player, actionDesc + " " + args);
    }

    private void sendConfirmButtons(Player player, String displayAction) {
        TextComponent message = new TextComponent(ChatColor.GRAY + "⇒ " + displayAction + " ");
        
        TextComponent yBtn = new TextComponent(ChatColor.GREEN + "[ Y ]");
        yBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cli confirm"));
        yBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("确认执行操作")));

        TextComponent spacer = new TextComponent(" / ");

        TextComponent nBtn = new TextComponent(ChatColor.RED + "[ N ]");
        nBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cli cancel"));
        nBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("取消执行")));

        message.addExtra(yBtn);
        message.addExtra(spacer);
        message.addExtra(nBtn);

        player.spigot().sendMessage(message);
    }

    private void executeFileOperation(Player player, String type, String args) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                File root = Bukkit.getWorldContainer(); // 安全地获取服务器根目录
                String result = "";
                
                // 处理路径参数：去除首尾空格，并去除开头的斜杠以确保相对于根目录
                String pathArg = args.trim();
                if (pathArg.startsWith("/") || pathArg.startsWith("\\")) {
                    pathArg = pathArg.substring(1);
                }

                if (type.equals("ls")) {
                    File dir = new File(root, pathArg.isEmpty() ? "." : pathArg);
                    if (!isWithinRoot(root, dir)) {
                        result = "错误: 路径超出服务器目录限制";
                    } else if (!dir.exists()) {
                        result = "错误: 目录不存在";
                    } else if (!dir.isDirectory()) {
                        result = "错误: 不是一个目录";
                    } else {
                        File[] files = dir.listFiles();
                        if (files == null) {
                            result = "错误: 无法列出目录内容";
                        } else {
                            StringBuilder sb = new StringBuilder("目录 " + (args.isEmpty() ? "." : args) + " 的内容:\n");
                            // 排序：目录在前，文件在后，按字母顺序
                            Arrays.sort(files, (f1, f2) -> {
                                if (f1.isDirectory() && !f2.isDirectory()) return -1;
                                if (!f1.isDirectory() && f2.isDirectory()) return 1;
                                return f1.getName().compareToIgnoreCase(f2.getName());
                            });
                            for (File f : files) {
                                String size = f.isDirectory() ? "" : " (" + (f.length() / 1024) + "KB)";
                                sb.append(f.isDirectory() ? "[DIR] " : "[FILE] ").append(f.getName()).append(size).append("\n");
                            }
                            result = sb.toString();
                        }
                    }
                } else if (type.equals("read")) {
                    File file = new File(root, pathArg);
                    if (!isWithinRoot(root, file)) {
                        result = "错误: 路径超出服务器目录限制";
                    } else if (!file.exists()) {
                        result = "错误: 文件不存在";
                    } else if (file.isDirectory()) {
                        result = "错误: 这是一个目录，请使用 #ls";
                    } else if (file.length() > 1024 * 100) { // 100KB 限制
                        result = "错误: 文件太大 (" + (file.length() / 1024) + "KB)，无法直接读取，请分段读取或选择其他方式";
                    } else {
                        result = new String(java.nio.file.Files.readAllBytes(file.toPath()), java.nio.charset.StandardCharsets.UTF_8);
                    }
                } else if (type.equals("diff")) {
                    String[] diffParts = pathArg.split("\\|", 3);
                    if (diffParts.length < 3) {
                        result = "错误: #diff 需要提供路径、查找内容和替换内容，格式：#diff: path | search | replace";
                    } else {
                        String path = diffParts[0].trim();
                        String search = diffParts[1];
                        String replace = diffParts[2];
                        
                        File file = new File(root, path);
                        if (!isWithinRoot(root, file)) {
                            result = "错误: 路径超出服务器目录限制";
                        } else if (!file.exists()) {
                            result = "错误: 文件不存在";
                        } else {
                            String content = new String(java.nio.file.Files.readAllBytes(file.toPath()), java.nio.charset.StandardCharsets.UTF_8);
                            
                            // 智能处理：AI 经常习惯在 | 符号前后各加一个空格。
                            // 如果带空格的 search 找不到，但去掉前后各一个空格后能找到，则自动修正。
                            if (!content.contains(search)) {
                                String trimmedSearch = search;
                                if (trimmedSearch.startsWith(" ")) trimmedSearch = trimmedSearch.substring(1);
                                if (trimmedSearch.endsWith(" ")) trimmedSearch = trimmedSearch.substring(0, trimmedSearch.length() - 1);
                                
                                if (content.contains(trimmedSearch)) {
                                    search = trimmedSearch;
                                    // 同时也对 replace 做对称处理
                                    if (replace.startsWith(" ")) replace = replace.substring(1);
                                    // 注意：replace 的末尾空格不需要处理，因为 args.trim() 已经去掉了整个 tool call 的末尾空格
                                }
                            }

                            if (!content.contains(search)) {
                                result = "错误: 未在文件中找到指定的查找内容，请确保查找内容完全匹配（包括缩进）";
                            } else {
                                String newContent = content.replace(search, replace);
                                java.nio.file.Files.write(file.toPath(), newContent.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                                result = "成功修改文件: " + path + "\n修改内容摘要：\n- 查找: " + (search.length() > 50 ? search.substring(0, 50) + "..." : search) + "\n- 替换为: " + (replace.length() > 50 ? replace.substring(0, 50) + "..." : replace);
                            }
                        }
                    }
                }

                final String finalResult = result;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage(ChatColor.GRAY + "⇒ 反馈已发送至 Fancy");
                    
                    // 提示执行结果摘要
                    if (type.equals("ls") && !finalResult.startsWith("错误:")) {
                        player.sendMessage(ChatColor.GRAY + "〇 已获取目录列表。");
                    } else if (type.equals("read") && !finalResult.startsWith("错误:")) {
                        player.sendMessage(ChatColor.GRAY + "〇 已读取文件内容 (" + (finalResult.length() / 1024.0) + "KB)。");
                    } else if (type.equals("diff") && !finalResult.startsWith("错误:")) {
                        player.sendMessage(ChatColor.GRAY + "〇 已成功修改文件。");
                    }
                    
                    feedbackToAI(player, "#" + type + "_result: " + finalResult);
                });
            } catch (Exception e) {
                plugin.getCloudErrorReport().report(e);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    feedbackToAI(player, "#" + type + "_result: 错误 - " + e.getMessage());
                });
            } catch (Throwable t) {
                plugin.getCloudErrorReport().report(t);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    feedbackToAI(player, "#" + type + "_result: 严重错误 - " + t.getMessage());
                });
            }
        });
    }

    private boolean isWithinRoot(File root, File file) {
        try {
            return file.getCanonicalPath().startsWith(root.getCanonicalPath());
        } catch (IOException e) {
            return false;
        }
    }

    private void executeCommand(Player player, String command) {
        // 开始数据包捕获
        if (plugin.getPacketCaptureManager() != null) {
            plugin.getPacketCaptureManager().startCapture(player);
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            StringBuilder output = new StringBuilder();
            
            // 检查是否为原版命令或特定需要避开拦截器的命令
            String cmdName = command.split(" ")[0].toLowerCase();
            if (cmdName.startsWith("/")) cmdName = cmdName.substring(1);
            boolean isVanilla = cmdName.startsWith("minecraft:") || 
                               Arrays.asList("fill", "setblock", "tp", "teleport", "give", "gamemode", "spawnpoint", "weather", "time", "msg", "tell", "w", "say", "list", "execute").contains(cmdName);

            boolean success = false;
            
            if (!isVanilla) {
                // 对于非原版命令，尝试使用拦截器以捕获可能的 sendMessage 输出
                try {
                    org.bukkit.command.CommandSender interceptor = createInterceptor(player, output);
                    success = Bukkit.dispatchCommand(interceptor, command);
                } catch (Throwable t) {
                    // 如果拦截器失败（如 VanillaCommandWrapper 报错），则回退到真实玩家
                    success = player.performCommand(command);
                }
            } else {
                // 对于原版命令，直接使用玩家执行，依靠 ProtocolLib 捕获输出
                success = player.performCommand(command);
            }

            boolean finalSuccess = success;
            
            // 提示玩家正在等待异步反馈
            player.sendMessage(ChatColor.GRAY + "⇒ 命令已下发，等待反馈中...");

            // 延迟 1 秒（20 ticks）后再处理结果，给异步任务留出时间
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                // 停止并获取数据包捕获结果
                String packetOutput = "";
                if (plugin.getPacketCaptureManager() != null) {
                    packetOutput = plugin.getPacketCaptureManager().stopCapture(player);
                }
                
                // 如果 ProtocolLib 捕获到了内容，优先使用它
                // 只有在 ProtocolLib 没捕获到且 Proxy 捕获到时，才使用 Proxy 的内容，避免重复
                String finalResult;
                if (!packetOutput.isEmpty()) {
                    finalResult = packetOutput;
                } else if (output.length() > 0) {
                    finalResult = output.toString();
                } else if (finalSuccess) {
                    // 如果成功但没有捕获到输出，尝试给 AI 提供更具体的上下文
                    if (command.toLowerCase().startsWith("tp")) {
                        finalResult = "命令执行结果未知 (你可以用choose工具问一下用户)";
                    } else if (command.toLowerCase().startsWith("op") || command.toLowerCase().startsWith("deop")) {
                        finalResult = "命令执行结果未知 (权限变更指令通常仅显示在控制台或被静默处理)";
                    } else {
                        finalResult = "命令执行结果未知 (你可以用choose工具问一下用户)";
                    }
                } else {
                    // 如果失败且没有输出，通常是语法错误或原版命令拦截失败
                    finalResult = "命令执行失败。可能原因：\n1. 命令语法错误\n2. 权限不足\n3. 该指令不支持拦截输出\n请检查语法或换一种实现方式。";
                }
                
                player.sendMessage(ChatColor.GRAY + "⇒ 反馈已发送至 Fancy");
                
                // 将详细结果反馈给 AI
                feedbackToAI(player, "#run_result: " + finalResult);
            }, 20L);
        });
    }

    /**
     * 创建一个用于拦截命令输出的代理对象
     */
    private org.bukkit.command.CommandSender createInterceptor(Player player, StringBuilder output) {
        return (org.bukkit.command.CommandSender) java.lang.reflect.Proxy.newProxyInstance(
            plugin.getClass().getClassLoader(),
            new Class<?>[]{org.bukkit.entity.Player.class},
            (proxy, method, args) -> {
                String methodName = method.getName();
                
                // 拦截所有 sendMessage 和相关发送消息的方法
                if (methodName.equals("sendMessage") || methodName.equals("sendRawMessage") || methodName.equals("sendActionBar")) {
                    if (args.length > 0 && args[0] != null) {
                        if (args[0] instanceof String) {
                            String msg = (String) args[0];
                            if (output.length() > 0) output.append("\n");
                            output.append(org.bukkit.ChatColor.stripColor(msg));
                            if (methodName.equals("sendActionBar")) {
                                player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR, new net.md_5.bungee.api.chat.TextComponent(msg));
                            } else {
                                player.sendMessage(msg);
                            }
                        } else if (args[0] instanceof String[]) {
                            for (String msg : (String[]) args[0]) {
                                if (output.length() > 0) output.append("\n");
                                output.append(org.bukkit.ChatColor.stripColor(msg));
                                player.sendMessage(msg);
                            }
                        } else if (args.length > 1 && args[0] instanceof java.util.UUID && args[1] instanceof String) {
                            String msg = (String) args[1];
                            if (output.length() > 0) output.append("\n");
                            output.append(org.bukkit.ChatColor.stripColor(msg));
                            player.sendMessage(msg);
                        } else {
                            String extracted = null;
                            try {
                                Class<?> componentClass = Class.forName("net.kyori.adventure.text.Component");
                                Object componentObj = args[0];
                                Object component = componentClass.isInstance(componentObj) ? componentObj : null;
                                if (component == null) {
                                    try {
                                        java.lang.reflect.Method asComponent = componentObj.getClass().getMethod("asComponent");
                                        Object maybeComponent = asComponent.invoke(componentObj);
                                        if (componentClass.isInstance(maybeComponent)) {
                                            component = maybeComponent;
                                        }
                                    } catch (Exception ignored) {
                                    }
                                }
                                if (component != null) {
                                    java.lang.reflect.Method plainTextMethod = Class.forName("net.kyori.adventure.text.serializer.plain.PlainComponentSerializer").getMethod("plain");
                                    Object serializer = plainTextMethod.invoke(null);
                                    java.lang.reflect.Method serializeMethod = serializer.getClass().getMethod("serialize", componentClass);
                                    extracted = (String) serializeMethod.invoke(serializer, component);
                                    if (extracted != null && !extracted.isEmpty()) {
                                        if (output.length() > 0) output.append("\n");
                                        output.append(org.bukkit.ChatColor.stripColor(extracted));
                                        if (methodName.equals("sendActionBar")) {
                                            try {
                                                java.lang.reflect.Method sendActionBar = player.getClass().getMethod("sendActionBar", componentClass);
                                                sendActionBar.invoke(player, component);
                                            } catch (Exception ignored) {
                                                player.sendMessage(extracted);
                                            }
                                        } else {
                                            try {
                                                java.lang.reflect.Method sendMessage = player.getClass().getMethod("sendMessage", componentClass);
                                                sendMessage.invoke(player, component);
                                            } catch (Exception ignored) {
                                                player.sendMessage(extracted);
                                            }
                                        }
                                    }
                                }
                            } catch (Exception ignored) {
                            }
                        }
                    }
                    return null;
                }

                // 拦截标题发送
                if (methodName.equals("sendTitle") && args.length >= 2) {
                    String title = args[0] != null ? args[0].toString() : "";
                    String subtitle = args[1] != null ? args[1].toString() : "";
                    if (!title.isEmpty() || !subtitle.isEmpty()) {
                        if (output.length() > 0) output.append("\n");
                        output.append("[Title] ").append(org.bukkit.ChatColor.stripColor(title));
                        if (!subtitle.isEmpty()) output.append(" [Subtitle] ").append(org.bukkit.ChatColor.stripColor(subtitle));
                        
                        // 转发给玩家，使用更通用的 API 避开可能的版本不匹配
                        try {
                            player.sendTitle(title, subtitle, 
                                args.length > 2 ? (int)args[2] : 10, 
                                args.length > 3 ? (int)args[3] : 70, 
                                args.length > 4 ? (int)args[4] : 20);
                        } catch (NoSuchMethodError e) {
                            // 兼容极旧版本或特定的 Bukkit 环境
                            player.sendMessage(title + " " + subtitle);
                        }
                    }
                    return null;
                }
                
                // 拦截 spigot().sendMessage
                if (methodName.equals("spigot")) {
                    return new org.bukkit.command.CommandSender.Spigot() {
                        @Override
                        public void sendMessage(net.md_5.bungee.api.chat.BaseComponent component) {
                            if (component == null) return;
                            String legacyText = net.md_5.bungee.api.chat.TextComponent.toLegacyText(component);
                            if (output.length() > 0) output.append("\n");
                            output.append(org.bukkit.ChatColor.stripColor(legacyText));
                            player.spigot().sendMessage(component);
                        }

                        @Override
                        public void sendMessage(net.md_5.bungee.api.chat.BaseComponent... components) {
                            if (components == null) return;
                            for (net.md_5.bungee.api.chat.BaseComponent component : components) {
                                sendMessage(component);
                            }
                        }
                    };
                }

                // 其他方法（权限检查、名字等）委托给原玩家
                try {
                    Object result = method.invoke(player, args);
                    // 如果方法返回 null 且返回类型是基本类型，需要返回对应的默认值
                    if (result == null && method.getReturnType().isPrimitive()) {
                        Class<?> returnType = method.getReturnType();
                        if (returnType == boolean.class) return false;
                        if (returnType == int.class) return 0;
                        if (returnType == double.class) return 0.0;
                        if (returnType == float.class) return 0.0f;
                        if (returnType == long.class) return 0L;
                        if (returnType == byte.class) return (byte) 0;
                        if (returnType == short.class) return (short) 0;
                        if (returnType == char.class) return '\0';
                    }
                    return result;
                } catch (java.lang.reflect.InvocationTargetException e) {
                    // 记录异常但不崩溃，尽量让命令继续执行
                    plugin.getLogger().warning("[CLI] Method " + methodName + " threw exception: " + e.getCause().getMessage());
                    plugin.getCloudErrorReport().report(e.getCause());
                    throw e.getCause();
                } catch (Exception e) {
                    plugin.getCloudErrorReport().report(e);
                    return null;
                }
            }
        );
    }

    private void handleGetTool(Player player, String fileName) {
        generationStates.put(player.getUniqueId(), GenerationStatus.EXECUTING_TOOL);
        File presetFile = new File(plugin.getDataFolder(), "preset/" + fileName);
        if (!presetFile.exists()) {
            feedbackToAI(player, "#get_result: 文件不存在");
            return;
        }

        try {
            List<String> lines = java.nio.file.Files.readAllLines(presetFile.toPath());
            String content = String.join("\n", lines);
            feedbackToAI(player, "#get_result: " + content);
        } catch (IOException e) {
            feedbackToAI(player, "#get_result: 读取文件失败 - " + e.getMessage());
        }
    }

    private void handleChooseTool(Player player, String optionsStr) {
        String[] options = optionsStr.split(",");
        TextComponent message = new TextComponent(ChatColor.GRAY + "⨀ [ ");
        
        for (int i = 0; i < options.length; i++) {
            String opt = options[i].trim();
            TextComponent optBtn = new TextComponent(ChatColor.AQUA + opt);
            // 设置点击事件，点击后执行 /cli select <opt>
            optBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cli select " + opt));
            optBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(ChatColor.GRAY + "点击选择: " + ChatColor.AQUA + opt)));
            
            message.addExtra(optBtn);
            if (i < options.length - 1) {
                message.addExtra(ChatColor.GRAY + " | ");
            }
        }
        message.addExtra(ChatColor.GRAY + " ]");
        
        player.spigot().sendMessage(message);
        // 标记玩家正在进行选择，以便拦截点击后的 RUN_COMMAND
        pendingCommands.put(player.getUniqueId(), "CHOOSING"); 
        generationStates.put(player.getUniqueId(), GenerationStatus.WAITING_CHOICE);
    }

    private void handleSearchTool(Player player, String query) {
        player.sendMessage(ChatColor.GRAY + "〇 #search: " + query);
        generationStates.put(player.getUniqueId(), GenerationStatus.EXECUTING_TOOL);
        
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String result;
            if (query.toLowerCase().contains("widely")) {
                String q = query.replace("widely", "").trim();
                result = fetchPublicSearchResult(q);
            } else {
                result = fetchWikiResult(query);
                // 如果 Wiki 没搜到，自动尝试全网搜索
                if (result.equals("未找到相关 Wiki 条目。")) {
                    player.sendMessage(ChatColor.GRAY + "〇 Wiki 无结果，正在尝试全网搜索...");
                    result = fetchPublicSearchResult(query);
                }
            }
            
            final String finalResult = result;
            Bukkit.getScheduler().runTask(plugin, () -> {
                feedbackToAI(player, "#search_result: " + finalResult);
            });
        });
    }

    /**
     * 调用 Minecraft Wiki 公开 API 搜索
     */
    private String fetchWikiResult(String query) {
        try {
            // 使用 Minecraft Wiki 的 MediaWiki API
            String url = "https://zh.minecraft.wiki/api.php?action=query&list=search&srsearch=" +
                         java.net.URLEncoder.encode(query, "UTF-8") + "&format=json&utf8=1";

            java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(10))
                    .build();

            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(url))
                    .timeout(java.time.Duration.ofSeconds(10))
                    .GET()
                    .build();

            java.net.http.HttpResponse<String> response = httpClient.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(response.body()).getAsJsonObject();
                com.google.gson.JsonArray searchResults = json.getAsJsonObject("query").getAsJsonArray("search");

                if (searchResults.size() > 0) {
                    StringBuilder sb = new StringBuilder("Minecraft Wiki 搜索结果：\n");
                    for (int i = 0; i < Math.min(3, searchResults.size()); i++) {
                        com.google.gson.JsonObject item = searchResults.get(i).getAsJsonObject();
                        String title = item.get("title").getAsString();
                        String snippet = item.get("snippet").getAsString().replaceAll("<[^>]*>", ""); // 移除 HTML 标签
                        sb.append("- ").append(title).append(": ").append(snippet).append("\n");
                    }
                    return sb.toString();
                }
            }
        } catch (Exception e) {
            return "Wiki 搜索出错: " + e.getMessage();
        }
        return "未找到相关 Wiki 条目。";
    }

    /**
     * 调用公开搜索接口 (UAPI Aggregate Search)
     */
    private String fetchPublicSearchResult(String query) {
        try {
            // 使用 UAPI 的聚合搜索接口
            String url = "https://uapis.cn/api/v1/search/aggregate";

            com.google.gson.JsonObject bodyJson = new com.google.gson.JsonObject();
            // 参数名确认为 query
            bodyJson.addProperty("query", query);

            java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(10))
                    .build();

            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(url))
                    .header("User-Agent", "FancyHelper/1.0")
                    .header("Content-Type", "application/json; charset=utf-8")
                    .timeout(java.time.Duration.ofSeconds(10))
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(bodyJson.toString()))
                    .build();

            java.net.http.HttpResponse<String> response = httpClient.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                String responseBody = response.body();
                com.google.gson.JsonElement jsonElement = com.google.gson.JsonParser.parseString(responseBody);

                com.google.gson.JsonArray results = null;
                if (jsonElement.isJsonArray()) {
                    results = jsonElement.getAsJsonArray();
                } else if (jsonElement.isJsonObject()) {
                    com.google.gson.JsonObject jsonObj = jsonElement.getAsJsonObject();
                    if (jsonObj.has("data") && jsonObj.get("data").isJsonArray()) {
                        results = jsonObj.getAsJsonArray("data");
                    } else if (jsonObj.has("results") && jsonObj.get("results").isJsonArray()) {
                        results = jsonObj.getAsJsonArray("results");
                    }
                }

                if (results != null && results.size() > 0) {
                    StringBuilder sb = new StringBuilder("全网搜索结果 (" + query + ")：\n");
                    for (int i = 0; i < Math.min(5, results.size()); i++) {
                        com.google.gson.JsonObject item = results.get(i).getAsJsonObject();

                        String title = "无标题";
                        if (item.has("title") && !item.get("title").isJsonNull()) {
                            title = item.get("title").getAsString();
                        }

                        String content = "";
                        if (item.has("content") && !item.get("content").isJsonNull()) {
                            content = item.get("content").getAsString();
                        } else if (item.has("snippet") && !item.get("snippet").isJsonNull()) {
                            content = item.get("snippet").getAsString();
                        } else if (item.has("abstract") && !item.get("abstract").isJsonNull()) {
                            content = item.get("abstract").getAsString();
                        }

                        if (content.length() > 500) {
                            content = content.substring(0, 500) + "...";
                        }

                        sb.append("- ").append(title).append(": ").append(content).append("\n");
                    }
                    return sb.toString();
                }
            } else {
                plugin.getLogger().warning("UAPI 搜索失败: " + response.statusCode());
                plugin.getLogger().warning("UAPI 错误详情: " + response.body());
            }
        } catch (Exception e) {
            return "全网搜索出错: " + e.getMessage();
        }
        return "未找到相关全网搜索结果。";
    }

    private void feedbackToAI(Player player, String feedback) {
        UUID uuid = player.getUniqueId();
        DialogueSession session = sessions.get(uuid);
        if (session == null) return;

        session.addMessage("user", feedback);
        isGenerating.put(uuid, true);
        generationStates.put(uuid, GenerationStatus.THINKING);
        generationStartTimes.put(uuid, System.currentTimeMillis());

        // 工具返回信息不显示给玩家，仅在日志记录并触发 AI 思考
        plugin.getLogger().info("[CLI] Feedback sent to AI for " + player.getName() + ": " + feedback);
        
        // 异步调用 AI，不显示 "Thought..." 提示，因为这是后台自动反馈
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                    String systemPrompt = promptManager.getBaseSystemPrompt(player);
                    AIResponse response = ai.chat(session, systemPrompt);

                Bukkit.getScheduler().runTask(plugin, () -> {
                    handleAIResponse(player, response);
                });
            } catch (IOException e) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage(ChatColor.RED + "AI 调用出错: " + e.getMessage());
                    isGenerating.put(uuid, false);
                    generationStates.put(uuid, GenerationStatus.ERROR);
                    generationStartTimes.remove(uuid);
                    // 立即清除动作栏
                    player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR, new TextComponent(""));
                });
            } catch (Throwable t) {
                plugin.getCloudErrorReport().report(t);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage(ChatColor.RED + "系统内部错误: " + t.getMessage());
                    isGenerating.put(uuid, false);
                    generationStates.put(uuid, GenerationStatus.ERROR);
                    generationStartTimes.remove(uuid);
                    // 立即清除动作栏
                    player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR, new TextComponent(""));
                });
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
            int thoughtIndex = -1;

            List<DialogueSession.Message> history = session.getHistory();
            if (thoughtToShow == null && !history.isEmpty()) {
                // 如果当前没有提取到思考过程，尝试查找历史最后一条 assistant 消息
                for (int i = history.size() - 1; i >= 0; i--) {
                    if ("assistant".equalsIgnoreCase(history.get(i).getRole())) {
                        if (history.get(i).hasThought()) {
                            thoughtToShow = history.get(i).getThought();
                            thoughtIndex = i;
                        }
                        break;
                    }
                }
            } else if (thoughtToShow != null) {
                // 如果当前提取到了思考过程，它已经被 addMessage 加入了历史，索引是 history.size() - 1
                thoughtIndex = history.size() - 1;
            }
            
            if (thoughtToShow != null) {
                TextComponent thoughtBtn = new TextComponent(ChatColor.GRAY + " ※ Thought");
                // 传递索引以便查看特定思考，如果索引无效则由 handleThought 处理回退
                String cmd = "/cli thought" + (thoughtIndex != -1 ? " " + thoughtIndex : "");
                thoughtBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmd));
                thoughtBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(ChatColor.GRAY + "点击查看本次思考过程")));
                player.spigot().sendMessage(thoughtBtn);
            }
        }

        // 处理代码块 ```...```
        String[] codeParts = content.split("```");
        TextComponent finalMessage = new TextComponent(ChatColor.WHITE + "◆ ");
        
        for (int i = 0; i < codeParts.length; i++) {
            if (i % 2 == 1) {
                // 代码块部分，亮蓝色显示
                finalMessage.addExtra(ChatColor.AQUA + codeParts[i]);
            } else {
                // 普通文本部分，进一步处理 **...** 高亮
                String text = codeParts[i];
                String[] highlightParts = text.split("\\*\\*");
                
                for (int j = 0; j < highlightParts.length; j++) {
                    if (j % 2 == 1) {
                        // 高亮部分，亮蓝色显示
                        finalMessage.addExtra(ChatColor.AQUA + highlightParts[j]);
                    } else {
                        // 普通部分，白色显示
                        finalMessage.addExtra(ChatColor.WHITE + highlightParts[j]);
                    }
                }
            }
        }
        player.spigot().sendMessage(finalMessage);
    }

    /**
     * 展示特定索引或最新的思考过程
     */
    public void handleThought(Player player, String[] args) {
        DialogueSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            player.sendMessage(ChatColor.RED + "当前没有活动的对话。");
            return;
        }

        String thought = null;
        if (args.length > 0) {
            try {
                int index = Integer.parseInt(args[0]);
                List<DialogueSession.Message> history = session.getHistory();
                if (index >= 0 && index < history.size()) {
                    thought = history.get(index).getThought();
                }
            } catch (NumberFormatException ignored) {}
        }

        // 如果没有提供索引或索引无效，则回退到最后一次思考
        if (thought == null) {
            thought = session.getLastThought();
        }

        if (thought == null) {
            player.sendMessage(ChatColor.RED + "找不到对应的思考过程。");
            return;
        }

        // 创建书本
        org.bukkit.inventory.ItemStack book = new org.bukkit.inventory.ItemStack(org.bukkit.Material.WRITTEN_BOOK);
        org.bukkit.inventory.meta.BookMeta meta = (org.bukkit.inventory.meta.BookMeta) book.getItemMeta();
        
        if (meta != null) {
            meta.setTitle("Fancy Thought");
            meta.setAuthor("Fancy");
            
            // 分页处理（书本每页约 256 字符，但实际受行数限制，使用 128 作为安全边距）
            List<String> pages = new ArrayList<>();
            int pageSize = 128;
            for (int i = 0; i < thought.length(); i += pageSize) {
                pages.add(thought.substring(i, Math.min(i + pageSize, thought.length())));
            }
            
            if (pages.isEmpty()) pages.add("");
            meta.setPages(pages);
            book.setItemMeta(meta);
            
            // 打开书本
            player.openBook(book);
        }
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

    private void sendEnterMessage(Player player) {
        UUID uuid = player.getUniqueId();
        DialogueSession session = sessions.get(uuid);
        DialogueSession.Mode mode = session != null ? session.getMode() : DialogueSession.Mode.NORMAL;

        player.sendMessage(ChatColor.GRAY + "==================");
        player.sendMessage("");
        
        TextComponent message = new TextComponent(ChatColor.WHITE + "Chatting with Fancy ");
        
        if (mode == DialogueSession.Mode.NORMAL) {
            TextComponent modeTag = new TextComponent(ChatColor.GREEN + " (Normal) ");
            modeTag.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(ChatColor.GOLD + "点击进入 YOLO 模式")));
            modeTag.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cli yolo"));
            message.addExtra(modeTag);
        } else {
            TextComponent modeTag = new TextComponent(ChatColor.RED + " (YOLO) ");
            modeTag.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(ChatColor.GREEN + "点击回到 Normal 模式")));
            modeTag.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cli normal"));
            message.addExtra(modeTag);
        }

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
        String modelName = plugin.getConfigManager().getCloudflareModel();
        int tokens = session != null ? session.getEstimatedTokens(modelName) : 0;
        int thoughtTokens = session != null ? session.getThoughtTokens() : 0;
        long durationMs = session != null ? System.currentTimeMillis() - session.getStartTime() : 0;
        double durationSec = durationMs / 1000.0;

        player.sendMessage(ChatColor.GRAY + "==================");
        player.sendMessage("");
        player.sendMessage(ChatColor.WHITE + "已退出 FancyHelper");
        player.sendMessage(ChatColor.GRAY + "消耗 Token: " + (tokens + thoughtTokens) + "  | 时长: " + String.format("%.1f", durationSec) + " 秒");
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
}
