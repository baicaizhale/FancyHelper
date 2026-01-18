package org.YanPl.manager;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.YanPl.MineAgent;
import org.YanPl.api.CloudFlareAI;
import org.YanPl.model.DialogueSession;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.scheduler.BukkitRunnable;

public class CLIManager {
    private final MineAgent plugin;
    private final CloudFlareAI ai;
    private final PromptManager promptManager;
    private final Set<UUID> activeCLIPayers = new HashSet<>();
    private final Set<UUID> pendingAgreementPlayers = new HashSet<>();
    private final Set<UUID> agreedPlayers = new HashSet<>();
    private final File agreedPlayersFile;
    private final Map<UUID, DialogueSession> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> isGenerating = new ConcurrentHashMap<>();
    private final Map<UUID, String> pendingCommands = new ConcurrentHashMap<>();

    public CLIManager(MineAgent plugin) {
        this.plugin = plugin;
        this.ai = new CloudFlareAI(plugin);
        this.promptManager = new PromptManager(plugin);
        this.agreedPlayersFile = new File(plugin.getDataFolder(), "agreed_players.txt");
        loadAgreedPlayers();
        startTimeoutTask();
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
                            player.sendMessage(ChatColor.YELLOW + "由于长时间未活动，已自动退出 CLI Mode。");
                            exitCLI(player);
                        } else {
                            activeCLIPayers.remove(uuid);
                            sessions.remove(uuid);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 20L * 60, 20L * 60);
    }

    public void toggleCLI(Player player) {
        UUID uuid = player.getUniqueId();
        if (activeCLIPayers.contains(uuid)) {
            exitCLI(player);
        } else {
            enterCLI(player);
        }
    }

    public void shutdown() {
        plugin.getLogger().info("[CLI] Shutting down CLIManager...");
        
        for (UUID uuid : new ArrayList<>(activeCLIPayers)) {
            sessions.remove(uuid);
        }
        activeCLIPayers.clear();
        pendingAgreementPlayers.clear();
        sessions.clear();
        isGenerating.clear();
        pendingCommands.clear();
        
        if (ai != null) {
            ai.shutdown();
        }
        
        plugin.getLogger().info("[CLI] CLIManager shutdown completed.");
    }

    public void enterCLI(Player player) {
        UUID uuid = player.getUniqueId();
        plugin.getLogger().info("[CLI] Player " + player.getName() + " is entering CLI mode.");
        
        if (!agreedPlayers.contains(uuid)) {
            plugin.getLogger().info("[CLI] Player " + player.getName() + " needs to agree to terms.");
            sendAgreement(player);
            pendingAgreementPlayers.add(uuid);
            return;
        }

        activeCLIPayers.add(uuid);
        sessions.put(uuid, new DialogueSession());
        sendEnterMessage(player);
    }

    public void exitCLI(Player player) {
        UUID uuid = player.getUniqueId();
        plugin.getLogger().info("[CLI] Player " + player.getName() + " is exiting CLI mode.");
        activeCLIPayers.remove(uuid);
        pendingAgreementPlayers.remove(uuid);
        sessions.remove(uuid);
        isGenerating.remove(uuid);
        sendExitMessage(player);
    }

    public void handleConfirm(Player player) {
        UUID uuid = player.getUniqueId();
        if (pendingCommands.containsKey(uuid)) {
            String cmd = pendingCommands.get(uuid);
            if (!"CHOOSING".equals(cmd)) {
                pendingCommands.remove(uuid);
                executeCommand(player, cmd);
            }
        }
    }

    public void handleCancel(Player player) {
        UUID uuid = player.getUniqueId();
        if (pendingCommands.containsKey(uuid)) {
            pendingCommands.remove(uuid);
            player.sendMessage(ChatColor.GRAY + "⇒ 命令已取消");
            isGenerating.put(uuid, false);
        }
    }

    public boolean handleChat(Player player, String message) {
        UUID uuid = player.getUniqueId();

        if (pendingAgreementPlayers.contains(uuid)) {
            plugin.getLogger().info("[CLI] Player " + player.getName() + " sent agreement message: " + message);
            if (message.equalsIgnoreCase("agree")) {
                pendingAgreementPlayers.remove(uuid);
                saveAgreedPlayer(uuid);
                enterCLI(player);
            } else {
                player.sendMessage(ChatColor.RED + "请发送 agree 以同意协议，或发送 /cli 退出。");
            }
            return true;
        }

        if (activeCLIPayers.contains(uuid)) {
            plugin.getLogger().info("[CLI] Intercepted message from " + player.getName() + ": " + message);
            if (message.equalsIgnoreCase("exit")) {
                exitCLI(player);
                return true;
            }
            if (message.equalsIgnoreCase("stop")) {
                boolean interrupted = false;
                if (isGenerating.getOrDefault(uuid, false)) {
                    isGenerating.put(uuid, false);
                    player.sendMessage(ChatColor.YELLOW + "⇒ 已打断 Agent 生成");
                    interrupted = true;
                }
                if (pendingCommands.containsKey(uuid)) {
                    pendingCommands.remove(uuid);
                    player.sendMessage(ChatColor.GRAY + "⇒ 已取消当前待处理的操作");
                    isGenerating.put(uuid, false);
                    interrupted = true;
                }
                if (!interrupted) {
                    player.sendMessage(ChatColor.GRAY + "当前没有正在进行的操作。输入 exit 退出 CLI 模式。");
                }
                return true;
            }

            if (pendingCommands.containsKey(uuid)) {
                String pending = pendingCommands.get(uuid);
                if (pending.equals("CHOOSING")) {
                    pendingCommands.remove(uuid);
                    feedbackToAI(player, "#choose_result: " + message);
                    return true;
                }
                
                if (message.equalsIgnoreCase("y") || message.equalsIgnoreCase("/mineagent confirm")) {
                    String cmd = pendingCommands.remove(uuid);
                    executeCommand(player, cmd);
                } else if (message.equalsIgnoreCase("n") || message.equalsIgnoreCase("/mineagent cancel")) {
                    pendingCommands.remove(uuid);
                    player.sendMessage(ChatColor.GRAY + "⇒ 命令已取消");
                    isGenerating.put(uuid, false);
                } else {
                    player.sendMessage(ChatColor.RED + "请确认命令 [Y/N]");
                }
                return true;
            }
            
            if (isGenerating.getOrDefault(uuid, false)) {
                player.sendMessage(ChatColor.RED + "⨀ 请不要在 Agent 生成内容时发送消息，如需打断请输入 stop");
                return true;
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

        player.sendMessage(ChatColor.GRAY + "◇ " + message);

        plugin.getLogger().info("[CLI] Session " + player.getName() + " - History Size: " + session.getHistory().size() + ", Est. Tokens: " + session.getEstimatedTokens());

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String response = ai.chat(session, promptManager.getBaseSystemPrompt(player));
                Bukkit.getScheduler().runTask(plugin, () -> handleAIResponse(player, response));
            } catch (IOException e) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage(ChatColor.RED + "AI 调用出错: " + e.getMessage());
                    isGenerating.put(uuid, false);
                    session.removeLastMessage();
                });
            }
        });
    }

    private void handleAIResponse(Player player, String response) {
        UUID uuid = player.getUniqueId();
        DialogueSession session = sessions.get(uuid);
        if (session == null) return;

        if (!isGenerating.getOrDefault(uuid, false)) {
            plugin.getLogger().info("[CLI] Discarding AI response for " + player.getName() + " due to interruption.");
            return;
        }

        plugin.getLogger().info("[CLI] AI Response received for " + player.getName() + " (Length: " + response.length() + ")");

        session.addMessage("assistant", response);

        String cleanResponse = response.replaceAll("(?s)<thought>.*?</thought>", "");
        cleanResponse = cleanResponse.replaceAll("(?i)^Thought:.*?\n", "");
        cleanResponse = cleanResponse.replaceAll("(?i)^思考过程:.*?\n", "");
        cleanResponse = cleanResponse.trim();
        
        String content = cleanResponse;
        String toolCall = "";
        
        List<String> knownTools = Arrays.asList("#over", "#exit", "#run", "#get", "#choose", "#search");
        
        int lastHashIndex = cleanResponse.lastIndexOf("#");
        if (lastHashIndex != -1) {
            String potentialToolPart = cleanResponse.substring(lastHashIndex).trim();
            for (String tool : knownTools) {
                if (potentialToolPart.toLowerCase().startsWith(tool)) {
                    toolCall = potentialToolPart;
                    content = cleanResponse.substring(0, lastHashIndex).trim();
                    break;
                }
            }
        }

        if (!content.isEmpty()) {
            displayAgentContent(player, content);
        }

        if (!toolCall.isEmpty()) {
            executeTool(player, toolCall);
        } else {
            isGenerating.put(uuid, false);
            checkTokenWarning(player, session);
        }
    }

    private void checkTokenWarning(Player player, DialogueSession session) {
        int estimatedTokens = session.getEstimatedTokens();
        int maxTokens = 4000; 
        int remaining = maxTokens - estimatedTokens;
        
        if (remaining < plugin.getConfigManager().getTokenWarningThreshold()) {
            player.sendMessage(ChatColor.YELLOW + "⨀ 剩余 Token 不足 (" + remaining + ")，Agent 可能会遗忘较早的对话内容。");
        }
    }

    private void executeTool(Player player, String toolCall) {
        UUID uuid = player.getUniqueId();
        
        String toolName;
        String args = "";
        
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

        plugin.getLogger().info("[CLI] Executing tool for " + player.getName() + ": " + toolName + " (Args: " + args + ")");
        
        String lowerToolName = toolName.toLowerCase();
        
        if (!lowerToolName.equals("#search") && !lowerToolName.equals("#run")) {
            player.sendMessage(ChatColor.GRAY + "〇 " + toolName);
        }

        switch (lowerToolName) {
            case "#over":
                isGenerating.put(uuid, false);
                break;
            case "#exit":
                exitCLI(player);
                break;
            case "#run":
                if (args.isEmpty()) {
                    player.sendMessage(ChatColor.RED + "错误: #run 工具需要提供命令参数");
                    feedbackToAI(player, "#error: #run 工具需要提供命令参数，例如 #run: say hello");
                } else {
                    handleRunTool(player, args);
                }
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
                break;
        }
    }

    private void handleRunTool(Player player, String command) {
        UUID uuid = player.getUniqueId();
        
        String cleanCommand = command.startsWith("/") ? command.substring(1) : command;
        pendingCommands.put(uuid, cleanCommand);

        TextComponent message = new TextComponent(ChatColor.GRAY + "⇒ " + cleanCommand + " ");
        
        TextComponent yBtn = new TextComponent(ChatColor.GREEN + "[ Y ]");
        yBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cli confirm"));
        yBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("确认执行命令")));

        TextComponent spacer = new TextComponent(" / ");

        TextComponent nBtn = new TextComponent(ChatColor.RED + "[ N ]");
        nBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cli cancel"));
        nBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("取消执行")));

        message.addExtra(yBtn);
        message.addExtra(spacer);
        message.addExtra(nBtn);

        player.spigot().sendMessage(message);
    }

    private void executeCommand(Player player, String command) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            StringBuilder output = new StringBuilder();
            
            org.bukkit.command.CommandSender interceptor = (org.bukkit.command.CommandSender) java.lang.reflect.Proxy.newProxyInstance(
                plugin.getClass().getClassLoader(),
                new Class<?>[]{org.bukkit.entity.Player.class},
                (proxy, method, args) -> {
                    String methodName = method.getName();
                    
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
                            }
                        }
                        return null;
                    }

                    if (methodName.equals("sendTitle") && args.length >= 2) {
                        return null;
                    }

                    try {
                        return method.invoke(player, args);
                    } catch (java.lang.reflect.InvocationTargetException e) {
                        throw e.getCause();
                    }
                });

            boolean success = plugin.getServer().dispatchCommand(interceptor, command);
            
            if (success) {
                if (output.length() > 0) {
                    feedbackToAI(player, "#command_output: " + output.toString());
                } else {
                    feedbackToAI(player, "#command_output: (命令已执行，但系统未能捕获到输出。这可能是因为命令是静默执行的，或者直接发送到了您的屏幕。)");
                }
            } else {
                player.sendMessage(ChatColor.RED + "命令执行失败: " + command);
                feedbackToAI(player, "#error: 命令执行失败或不存在: " + command);
                isGenerating.put(player.getUniqueId(), false);
            }
        });
    }

    private void handleGetTool(Player player, String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            player.sendMessage(ChatColor.RED + "错误: #get 工具需要文件名参数");
            feedbackToAI(player, "#error: #get 工具需要文件名参数");
            return;
        }

        File presetDir = new File(plugin.getDataFolder(), "preset");
        File file = new File(presetDir, fileName);

        if (!file.exists()) {
            player.sendMessage(ChatColor.RED + "文件不存在: " + fileName);
            feedbackToAI(player, "#error: 文件不存在: " + fileName + "。请使用 #search 搜索或检查文件名。");
            return;
        }

        try {
            List<String> lines = java.nio.file.Files.readAllLines(file.toPath());
            String content = String.join("\n", lines);
            
            int maxLen = 2000;
            if (content.length() > maxLen) {
                content = content.substring(0, maxLen) + "\n...(truncated)";
            }

            feedbackToAI(player, "#file_content " + fileName + ":\n" + content);
            player.sendMessage(ChatColor.GREEN + "已获取文件内容: " + fileName);
        } catch (IOException e) {
            player.sendMessage(ChatColor.RED + "读取文件失败: " + e.getMessage());
            feedbackToAI(player, "#error: 读取文件失败: " + e.getMessage());
        }
    }
    
    private void handleChooseTool(Player player, String args) {
        if (args == null || args.isEmpty()) {
            feedbackToAI(player, "#error: #choose 工具需要选项参数");
            return;
        }
        
        String[] options = args.split("[,，]");
        if (options.length < 2) {
            feedbackToAI(player, "#error: #choose 至少需要两个选项");
            return;
        }
        
        pendingCommands.put(player.getUniqueId(), "CHOOSING");
        
        player.sendMessage(ChatColor.AQUA + "请选择一个选项 (直接在聊天栏输入):");
        for (String opt : options) {
            player.sendMessage(ChatColor.WHITE + " - " + opt.trim());
        }
    }

    private void handleSearchTool(Player player, String query) {
        if (query == null || query.isEmpty()) {
            feedbackToAI(player, "#error: #search 需要查询内容");
            return;
        }

        player.sendMessage(ChatColor.GRAY + "正在搜索: " + query + " ...");
        
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
             try {
                 String searchResult = "模拟搜索结果: 关于 " + query + " 的信息..."; 
                 
                 Bukkit.getScheduler().runTask(plugin, () -> {
                     feedbackToAI(player, "#search_result: " + searchResult);
                 });
             } catch (Exception e) {
                 Bukkit.getScheduler().runTask(plugin, () -> {
                     feedbackToAI(player, "#error: 搜索失败: " + e.getMessage());
                 });
             }
        });
    }

    private void feedbackToAI(Player player, String systemMessage) {
        UUID uuid = player.getUniqueId();
        DialogueSession session = sessions.get(uuid);
        if (session == null) return;
        
        session.addMessage("system", systemMessage);
        isGenerating.put(uuid, true);
        
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String response = ai.chat(session, promptManager.getBaseSystemPrompt(player));
                Bukkit.getScheduler().runTask(plugin, () -> handleAIResponse(player, response));
            } catch (IOException e) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage(ChatColor.RED + "AI 调用出错 (Feedback): " + e.getMessage());
                    isGenerating.put(uuid, false);
                    session.removeLastMessage(); 
                });
            }
        });
    }

    private void displayAgentContent(Player player, String content) {
        String[] lines = content.split("\n");
        for (String line : lines) {
            player.sendMessage(ChatColor.WHITE + line);
        }
    }

    private void sendAgreement(Player player) {
        player.sendMessage(ChatColor.GOLD + "=== 用户协议 ===");
        player.sendMessage(ChatColor.WHITE + "欢迎使用 MineAgent CLI 模式。");
        player.sendMessage(ChatColor.WHITE + "本功能使用 AI 技术辅助生成命令。");
        player.sendMessage(ChatColor.WHITE + "请注意：");
        player.sendMessage(ChatColor.GRAY + "1. AI 生成的命令可能存在风险，请仔细核对。");
        player.sendMessage(ChatColor.GRAY + "2. 您的对话数据将被发送到 CloudFlare Workers AI。");
        player.sendMessage(ChatColor.WHITE + "请输入 " + ChatColor.GREEN + "agree" + ChatColor.WHITE + " 同意协议并继续，或输入 " + ChatColor.RED + "/cli" + ChatColor.WHITE + " 退出。");
        player.sendMessage(ChatColor.GOLD + "==================");
    }

    private void sendEnterMessage(Player player) {
        player.sendMessage(ChatColor.GREEN + "=== 已进入 CLI Mode ===");
        player.sendMessage(ChatColor.GRAY + "你可以直接在聊天栏输入自然语言来控制服务器。");
        player.sendMessage(ChatColor.GRAY + "输入 " + ChatColor.YELLOW + "exit" + ChatColor.GRAY + " 退出此模式。");
        player.sendMessage(ChatColor.GRAY + "输入 " + ChatColor.YELLOW + "stop" + ChatColor.GRAY + " 打断生成或取消操作。");
    }

    private void sendExitMessage(Player player) {
        player.sendMessage(ChatColor.YELLOW + "=== 已退出 CLI Mode ===");
    }
    
    public int getActivePlayersCount() {
        return activeCLIPayers.size();
    }
}
