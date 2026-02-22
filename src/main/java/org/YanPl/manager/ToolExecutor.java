package org.YanPl.manager;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.YanPl.FancyHelper;
import org.YanPl.model.DialogueSession;
import org.YanPl.model.Question;
import org.YanPl.model.ExecutionPlan;
import org.YanPl.model.PlanStep;
import org.YanPl.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * 工具执行器，负责处理 AI 发起的各类工具调用
 * 从 CLIManager 中提取出来以降低复杂度
 */
public class ToolExecutor {
    private final FancyHelper plugin;
    private final CLIManager cliManager;
    private final Gson gson = new Gson();

    public ToolExecutor(FancyHelper plugin, CLIManager cliManager) {
        this.plugin = plugin;
        this.cliManager = cliManager;
    }

    /**
     * 执行工具调用
     * @param player 玩家
     * @param toolCall 工具调用字符串
     * @param session 对话会话
     * @return 是否成功执行
     */
    public boolean executeTool(Player player, String toolCall, DialogueSession session) {
        UUID uuid = player.getUniqueId();

        // 解析工具名称和参数
        ToolParseResult parseResult = parseToolCall(toolCall);
        String toolName = parseResult.toolName;
        String args = parseResult.args;

        // PLAN 模式强制限制：只允许信息收集和计划创建工具
        if (session != null && session.getMode() == DialogueSession.Mode.PLAN) {
            String lowerToolName = toolName.toLowerCase();
            // 允许的工具：#ask_questions、#create_plan、#search、#getpreset、#over、#exit
            boolean isAllowedTool = lowerToolName.equals("#ask_questions") ||
                                   lowerToolName.equals("#create_plan") ||
                                   lowerToolName.equals("#search") ||
                                   lowerToolName.equals("#getpreset") ||
                                   lowerToolName.equals("#over") ||
                                   lowerToolName.equals("#exit");

            // 特殊限制：在PLAN模式下，如果已有计划，禁止调用#over
            if (lowerToolName.equals("#over") && session.getCurrentPlan() != null) {
                plugin.getLogger().warning("[CLI] PLAN 模式下阻止了#over调用：计划已创建，应等待用户选择执行模式");
                player.sendMessage(ChatColor.RED + "⨀ 计划已创建，请等待用户选择执行模式（YOLO、Normal或Modify）");
                cliManager.feedbackToAI(player, "#error: 计划已创建，必须等待用户选择执行模式。禁止在计划创建后立即调用#over。");
                // 不设置生成状态为false，让feedbackToAI自动管理
                return false;
            }

            if (!isAllowedTool) {
                plugin.getLogger().warning("[CLI] PLAN 模式下阻止了非法工具调用: " + toolName);
                player.sendMessage(ChatColor.RED + "⨀ PLAN 模式下只允许 #ask_questions、#create_plan、#search、#getpreset 工具");
                cliManager.feedbackToAI(player, "#error: PLAN 模式禁止直接执行命令。允许的工具：#ask_questions、#create_plan、#search、#getpreset。禁止的工具：#run、#ls、#read、#diff、#todo、#remember、#forget、#editmem、#choose。");
                // 不设置生成状态为false，让feedbackToAI自动管理
                return false;
            }
        }

        plugin.getLogger().info("[CLI] 正在为 " + player.getName() + " 执行工具: " + toolName + " (参数: " + args + ")");

        // 显示工具调用信息
        displayToolCall(player, toolName, args);

        // 执行对应的工具
        boolean success = true;
        String lowerToolName = toolName.toLowerCase();

        switch (lowerToolName) {
            case "#over":
                cliManager.setGenerating(uuid, false, CLIManager.GenerationStatus.COMPLETED);
                break;
            case "#exit":
                cliManager.exitCLI(player);
                break;
            case "#run":
                success = handleRunTool(player, args, session);
                break;
            case "#ls":
                handleFileTool(player, "ls", args, session);
                break;
            case "#read":
                handleFileTool(player, "read", args, session);
                break;
            case "#diff":
                handleFileTool(player, "diff", args, session);
                break;
            case "#getpreset":
                handleGetTool(player, args);
                break;
            case "#choose":
                handleChooseTool(player, args);
                break;
            case "#search":
                handleSearchTool(player, args);
                break;
            case "#todo":
                handleTodoTool(player, args);
                break;
            case "#remember":
                handleRememberTool(player, args);
                break;
            case "#forget":
                handleForgetKeyTool(player, args);
                break;
            case "#editmem":
                handleEditmemTool(player, args);
                break;
            case "#ask_questions":
                success = handleAskQuestionsTool(player, args, session);
                break;
            case "#create_plan":
                success = handleCreatePlanTool(player, args, session);
                break;
            default:
                player.sendMessage(ChatColor.RED + "未知工具: " + toolName);
                cliManager.feedbackToAI(player, "#error: 未知工具 " + toolName + "。请仅使用系统提示中定义的工具。");
                success = false;
                break;
        }

        return success;
    }

    /**
     * 工具调用解析结果
     */
    public static class ToolParseResult {
        public final String toolName;
        public final String args;

        public ToolParseResult(String toolName, String args) {
            this.toolName = toolName;
            this.args = args;
        }
    }

    /**
     * 解析工具调用字符串
     */
    public ToolParseResult parseToolCall(String toolCall) {
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

        return new ToolParseResult(toolName, args);
    }

    /**
     * 显示工具调用信息给玩家
     */
    private void displayToolCall(Player player, String toolName, String args) {
        String lowerToolName = toolName.toLowerCase();

        if (lowerToolName.equals("#remember") || lowerToolName.equals("#forget") ||
            lowerToolName.equals("#editmem")) {
            TextComponent message = new TextComponent(ChatColor.WHITE + "⁕ Fancy 正在记住你说的话.. ");
            TextComponent manageBtn = new TextComponent("[管理记忆]");
            manageBtn.setColor(net.md_5.bungee.api.ChatColor.of(ColorUtil.getColorZ()));
            manageBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cli memory"));
            manageBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(ChatColor.GRAY + "点击管理偏好记忆")));
            message.addExtra(manageBtn);
            player.spigot().sendMessage(message);
        } else if (lowerToolName.equals("#diff")) {
            String[] parts = args.split("\\|", 3);
            String path = parts.length > 0 ? parts[0].trim() : "";
            player.sendMessage(ChatColor.GRAY + "〇 正在修改文件: " + ChatColor.WHITE + path);
            if (parts.length >= 3) {
                player.sendMessage(ChatColor.GRAY + "From " + ChatColor.WHITE + parts[1]);
                player.sendMessage(ChatColor.GRAY + "To " + ChatColor.WHITE + parts[2]);
            }
        } else if (lowerToolName.equals("#exit")) {
            player.sendMessage(ChatColor.GRAY + "〇 Exiting...");
        } else if (lowerToolName.equals("#ask_questions")) {
            player.sendMessage(ChatColor.GRAY + "⁕ " + ChatColor.WHITE + "Fancy 正在向你提问...");
        } else if (lowerToolName.equals("#create_plan")) {
            player.sendMessage(ChatColor.GRAY + "⁕ " + ChatColor.WHITE + "Fancy 正在制定执行计划...");
        } else if (!lowerToolName.equals("#search") && !lowerToolName.equals("#run") && 
            !lowerToolName.equals("#over") && !lowerToolName.equals("#ls") && 
            !lowerToolName.equals("#read") && !lowerToolName.equals("#todo")) {
            player.sendMessage(ChatColor.GRAY + "〇 " + toolName);
        }
    }

    /**
     * 处理 #run 工具
     */
    private boolean handleRunTool(Player player, String command, DialogueSession session) {
        if (command.isEmpty()) {
            player.sendMessage(ChatColor.RED + "错误: #run 工具需要提供命令参数");
            cliManager.feedbackToAI(player, "#error: #run 工具需要提供命令参数，例如 #run: say hello");
            return false;
        }

        UUID uuid = player.getUniqueId();
        String cleanCommand = command.startsWith("/") ? command.substring(1) : command;

        // YOLO 模式下风险命令需要确认
        if (session != null && session.getMode() == DialogueSession.Mode.YOLO) {
            if (isRiskyCommand(cleanCommand)) {
                player.sendMessage(ChatColor.YELLOW + "⨀ 检测到风险命令，执行可能带来无法挽回的后果，请检查命令");
                cliManager.setPendingCommand(uuid, cleanCommand);
                cliManager.setGenerating(uuid, false, CLIManager.GenerationStatus.WAITING_CONFIRM);
                sendConfirmButtons(player, cleanCommand);
                return true;
            } else {
                player.sendMessage(ChatColor.GOLD + "⇒ YOLO RUN " + ChatColor.WHITE + cleanCommand);
                cliManager.setGenerating(uuid, false, CLIManager.GenerationStatus.EXECUTING_TOOL);
                executeCommand(player, cleanCommand);
                return true;
            }
        }

        cliManager.setPendingCommand(uuid, cleanCommand);
        cliManager.setGenerating(uuid, false, CLIManager.GenerationStatus.WAITING_CONFIRM);
        sendConfirmButtons(player, cleanCommand);
        return true;
    }

    /**
     * 检查是否为风险命令
     */
    private boolean isRiskyCommand(String cmd) {
        List<String> risky = plugin.getConfigManager().getYoloRiskCommands();
        if (risky == null || risky.isEmpty()) return false;
        
        String lc = cmd.toLowerCase();
        for (String r : risky) {
            if (r == null) continue;
            String rr = r.trim().toLowerCase();
            if (rr.isEmpty()) continue;
            if (lc.startsWith(rr)) return true;
            int spaceIdx = lc.indexOf(' ');
            String first = spaceIdx >= 0 ? lc.substring(0, spaceIdx) : lc;
            if (first.equals(rr)) return true;
        }
        return false;
    }

    /**
     * 处理文件工具 (#ls, #read, #diff)
     */
    private void handleFileTool(Player player, String type, String args, DialogueSession session) {
        UUID uuid = player.getUniqueId();

        // YOLO 模式下直接执行
        if (session != null && session.getMode() == DialogueSession.Mode.YOLO) {
            String actionDesc = type.equals("ls") ? "LIST" : (type.equals("read") ? "READ" : "DIFF");
            player.sendMessage(ChatColor.GOLD + "⇒ YOLO " + actionDesc + " " + ChatColor.WHITE + args);

            // 检查是否被冻结
            long freezeRemaining = plugin.getVerificationManager().getPlayerFreezeRemaining(player);
            if (freezeRemaining > 0) {
                player.sendMessage(ChatColor.RED + "验证已冻结，请在 " + freezeRemaining + " 秒后重试。");
                return;
            }

            // YOLO 模式下也需要检查权限开启
            if (plugin.getConfigManager().isPlayerToolEnabled(player, type)) {
                cliManager.setGenerating(uuid, false, CLIManager.GenerationStatus.EXECUTING_TOOL);
                executeFileOperation(player, type, args);
            } else {
                player.sendMessage(ChatColor.YELLOW + "检测到 YOLO 模式调用 " + type + "，但该工具尚未完成首次验证。");
                plugin.getVerificationManager().startVerification(player, type, () -> {
                    plugin.getConfigManager().setPlayerToolEnabled(player, type, true);
                    cliManager.setGenerating(uuid, false, CLIManager.GenerationStatus.EXECUTING_TOOL);
                    executeFileOperation(player, type, args);
                });
            }
            return;
        }

        // 普通模式需要确认
        String pendingStr = type.toUpperCase() + ":" + args;
        cliManager.setPendingCommand(uuid, pendingStr);
        cliManager.setGenerating(uuid, false, CLIManager.GenerationStatus.WAITING_CONFIRM);

        if ("diff".equals(type)) {
            sendConfirmButtons(player, "");
        } else {
            String actionDesc = type.equals("ls") ? "请求列出目录" : "请求读取文件";
            sendConfirmButtons(player, actionDesc + " " + ChatColor.WHITE + args);
        }
    }

    /**
     * 发送确认按钮
     */
    public void sendConfirmButtons(Player player, String displayAction) {
        TextComponent message = new TextComponent(displayAction != null && !displayAction.trim().isEmpty() 
            ? (ChatColor.GRAY + "⇒ " + displayAction + " ") : "");

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

    /**
     * 执行文件操作
     */
    public void executeFileOperation(Player player, String type, String args) {
        if (!plugin.isEnabled()) return;
        
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                File root = Bukkit.getWorldContainer();
                String result = executeFileOperationInternal(root, type, args);

                final String finalResult = result;
                if (!plugin.isEnabled()) return;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage(ChatColor.GRAY + "⇒ 反馈已发送至 Fancy");
                    displayFileOperationResult(player, type, finalResult);
                    cliManager.feedbackToAI(player, "#" + type + "_result: " + finalResult);
                });
            } catch (Exception e) {
                plugin.getCloudErrorReport().report(e);
                if (!plugin.isEnabled()) return;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    cliManager.feedbackToAI(player, "#" + type + "_result: 错误 - " + e.getMessage());
                });
            }
        });
    }

    /**
     * 执行文件操作的内部逻辑
     */
    private String executeFileOperationInternal(File root, String type, String args) throws IOException {
        String pathArg = args.trim();
        if (pathArg.startsWith("/") || pathArg.startsWith("\\")) {
            pathArg = pathArg.substring(1);
        }

        if (type.equals("ls")) {
            return executeLsOperation(root, pathArg, args);
        } else if (type.equals("read")) {
            return executeReadOperation(root, pathArg);
        } else if (type.equals("diff")) {
            return executeDiffOperation(root, pathArg);
        }

        return "错误: 未知操作类型";
    }

    /**
     * 执行 ls 操作
     */
    private String executeLsOperation(File root, String pathArg, String args) {
        File dir = new File(root, pathArg.isEmpty() ? "." : pathArg);
        
        if (!isWithinRoot(root, dir)) {
            return "错误: 路径超出服务器目录限制";
        }
        if (!dir.exists()) {
            return "错误: 目录不存在";
        }
        if (!dir.isDirectory()) {
            return "错误: 不是一个目录";
        }

        File[] files = dir.listFiles();
        if (files == null) {
            return "错误: 无法列出目录内容";
        }

        StringBuilder sb = new StringBuilder("目录 " + (args.isEmpty() ? "." : args) + " 的内容:\n");
        Arrays.sort(files, (f1, f2) -> {
            if (f1.isDirectory() && !f2.isDirectory()) return -1;
            if (!f1.isDirectory() && f2.isDirectory()) return 1;
            return f1.getName().compareToIgnoreCase(f2.getName());
        });

        for (File f : files) {
            String size = f.isDirectory() ? "" : " (" + (f.length() / 1024) + "KB)";
            sb.append(f.isDirectory() ? "[DIR] " : "[FILE] ").append(f.getName()).append(size).append("\n");
        }

        return sb.toString();
    }

    /**
     * 执行 read 操作
     */
    private String executeReadOperation(File root, String pathArg) throws IOException {
        File file = new File(root, pathArg);
        
        if (!isWithinRoot(root, file)) {
            return "错误: 路径超出服务器目录限制";
        }
        if (!file.exists()) {
            return "错误: 文件不存在";
        }
        if (file.isDirectory()) {
            return "错误: 这是一个目录，请使用 #ls";
        }
        if (file.length() > 1024 * 100) {
            return "错误: 文件太大 (" + (file.length() / 1024) + "KB)，无法直接读取，请分段读取或选择其他方式";
        }

        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    /**
     * 执行 diff 操作
     */
    private String executeDiffOperation(File root, String pathArg) throws IOException {
        String[] diffParts = pathArg.split("\\|", 3);
        
        if (diffParts.length < 3) {
            return "错误: #diff 需要提供路径、查找内容和替换内容，格式：#diff: path | search | replace";
        }

        String path = diffParts[0].trim();
        String search = diffParts[1];
        String replace = diffParts[2];

        File file = new File(root, path);
        
        if (!isWithinRoot(root, file)) {
            return "错误: 路径超出服务器目录限制";
        }
        if (!file.exists()) {
            return "错误: 文件不存在";
        }

        String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);

        // 智能处理空格
        if (!content.contains(search)) {
            String trimmedSearch = search;
            if (trimmedSearch.startsWith(" ")) trimmedSearch = trimmedSearch.substring(1);
            if (trimmedSearch.endsWith(" ")) trimmedSearch = trimmedSearch.substring(0, trimmedSearch.length() - 1);

            if (content.contains(trimmedSearch)) {
                search = trimmedSearch;
                if (replace.startsWith(" ")) replace = replace.substring(1);
            }
        }

        if (!content.contains(search)) {
            return "错误: 未在文件中找到指定的查找内容，请确保查找内容完全匹配（包括缩进）";
        }

        String newContent = content.replace(search, replace);
        Files.write(file.toPath(), newContent.getBytes(StandardCharsets.UTF_8));

        return "成功修改文件: " + path + "\n修改内容摘要：\n- 查找: " + 
               (search.length() > 50 ? search.substring(0, 50) + "..." : search) + 
               "\n- 替换为: " + (replace.length() > 50 ? replace.substring(0, 50) + "..." : replace);
    }

    /**
     * 检查路径是否在根目录内
     */
    private boolean isWithinRoot(File root, File file) {
        try {
            return file.getCanonicalPath().startsWith(root.getCanonicalPath());
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * 显示文件操作结果摘要
     */
    private void displayFileOperationResult(Player player, String type, String result) {
        if (result.startsWith("错误:")) return;

        if (type.equals("ls")) {
            player.sendMessage(ChatColor.GRAY + "〇 已获取目录列表。");
        } else if (type.equals("read")) {
            player.sendMessage(ChatColor.GRAY + "〇 已读取文件内容 (" + (result.length() / 1024.0) + "KB)。");
        } else if (type.equals("diff")) {
            player.sendMessage(ChatColor.GRAY + "〇 已成功修改文件。");
        }
    }

    /**
     * 执行服务器命令
     */
    public void executeCommand(Player player, String command) {
        if (plugin.getPacketCaptureManager() != null) {
            plugin.getPacketCaptureManager().startCapture(player);
        }

        if (!plugin.isEnabled()) return;
        
        Bukkit.getScheduler().runTask(plugin, () -> {
            StringBuilder output = new StringBuilder();
            String cmdName = command.split(" ")[0].toLowerCase();
            if (cmdName.startsWith("/")) cmdName = cmdName.substring(1);

            boolean isVanilla = cmdName.startsWith("minecraft:") || 
                Arrays.asList("fill", "setblock", "tp", "teleport", "give", "gamemode", 
                              "spawnpoint", "weather", "time", "msg", "tell", "w", "say", "list", "execute").contains(cmdName);

            boolean success;
            if (!isVanilla) {
                try {
                    org.bukkit.command.CommandSender interceptor = createInterceptor(player, output);
                    success = Bukkit.dispatchCommand(interceptor, command);
                } catch (Throwable t) {
                    success = player.performCommand(command);
                }
            } else {
                success = player.performCommand(command);
            }

            boolean finalSuccess = success;
            player.sendMessage(ChatColor.GRAY + "⇒ 命令已下发，等待反馈中...");

            if (!plugin.isEnabled()) return;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                String packetOutput = "";
                if (plugin.getPacketCaptureManager() != null) {
                    packetOutput = plugin.getPacketCaptureManager().stopCapture(player);
                }

                String finalResult = buildCommandResult(command, packetOutput, output.toString(), finalSuccess);
                player.sendMessage(ChatColor.GRAY + "⇒ 反馈已发送至 Fancy");
                cliManager.feedbackToAI(player, "#run_result: " + finalResult);
            }, 20L);
        });
    }

    /**
     * 构建命令执行结果
     */
    private String buildCommandResult(String command, String packetOutput, String proxyOutput, boolean success) {
        if (!packetOutput.isEmpty()) {
            return packetOutput;
        }
        if (!proxyOutput.isEmpty()) {
            return proxyOutput;
        }
        if (success) {
            if (command.toLowerCase().startsWith("tp")) {
                return "命令执行结果未知 (你可以用choose工具问一下用户)";
            } else if (command.toLowerCase().startsWith("op") || command.toLowerCase().startsWith("deop")) {
                return "命令执行结果未知 (权限变更指令通常仅显示在控制台或被静默处理)";
            }
            return "命令执行结果未知 (你可以用choose工具问一下用户)";
        }
        return "命令执行失败。可能原因：\n1. 命令语法错误\n2. 权限不足\n3. 该指令不支持拦截输出\n请检查语法或换一种实现方式。";
    }

    /**
     * 创建命令输出拦截器
     */
    private org.bukkit.command.CommandSender createInterceptor(Player player, StringBuilder output) {
        return (org.bukkit.command.CommandSender) java.lang.reflect.Proxy.newProxyInstance(
            plugin.getClass().getClassLoader(),
            new Class<?>[]{org.bukkit.entity.Player.class},
            (proxy, method, args) -> {
                String methodName = method.getName();

                if (methodName.equals("sendMessage") || methodName.equals("sendRawMessage") || methodName.equals("sendActionBar")) {
                    return handleSendMessage(player, output, args, methodName);
                }

                if (methodName.equals("sendTitle") && args.length >= 2) {
                    return handleSendTitle(player, output, args);
                }

                if (methodName.equals("spigot")) {
                    return createSpigotInterceptor(player, output);
                }

                // 其他方法委托给原玩家
                try {
                    Object result = method.invoke(player, args);
                    if (result == null && method.getReturnType().isPrimitive()) {
                        return getDefaultValue(method.getReturnType());
                    }
                    return result;
                } catch (java.lang.reflect.InvocationTargetException e) {
                    plugin.getLogger().warning("[CLI] Method " + methodName + " threw exception: " + e.getCause().getMessage());
                    plugin.getCloudErrorReport().report(e.getCause());
                    throw e.getCause();
                }
            }
        );
    }

    /**
     * 处理 sendMessage 方法调用
     */
    private Object handleSendMessage(Player player, StringBuilder output, Object[] args, String methodName) {
        if (args.length == 0 || args[0] == null) return null;

        if (args[0] instanceof String) {
            String msg = (String) args[0];
            if (output.length() > 0) output.append("\n");
            output.append(ChatColor.stripColor(msg));
            if (methodName.equals("sendActionBar")) {
                player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR, 
                    new net.md_5.bungee.api.chat.TextComponent(msg));
            } else {
                player.sendMessage(msg);
            }
        } else if (args[0] instanceof String[]) {
            for (String msg : (String[]) args[0]) {
                if (output.length() > 0) output.append("\n");
                output.append(ChatColor.stripColor(msg));
                player.sendMessage(msg);
            }
        } else if (args.length > 1 && args[0] instanceof java.util.UUID && args[1] instanceof String) {
            String msg = (String) args[1];
            if (output.length() > 0) output.append("\n");
            output.append(ChatColor.stripColor(msg));
            player.sendMessage(msg);
        } else {
            handleComponentMessage(player, output, args[0], methodName);
        }

        return null;
    }

    /**
     * 处理组件消息
     */
    private void handleComponentMessage(Player player, StringBuilder output, Object componentObj, String methodName) {
        try {
            Class<?> componentClass = Class.forName("net.kyori.adventure.text.Component");
            Object component = componentClass.isInstance(componentObj) ? componentObj : null;

            if (component == null) {
                try {
                    java.lang.reflect.Method asComponent = componentObj.getClass().getMethod("asComponent");
                    Object maybeComponent = asComponent.invoke(componentObj);
                    if (componentClass.isInstance(maybeComponent)) {
                        component = maybeComponent;
                    }
                } catch (Exception ignored) {}
            }

            if (component != null) {
                java.lang.reflect.Method plainTextMethod = Class.forName("net.kyori.adventure.text.serializer.plain.PlainComponentSerializer").getMethod("plain");
                Object serializer = plainTextMethod.invoke(null);
                java.lang.reflect.Method serializeMethod = serializer.getClass().getMethod("serialize", componentClass);
                String extracted = (String) serializeMethod.invoke(serializer, component);

                if (extracted != null && !extracted.isEmpty()) {
                    if (output.length() > 0) output.append("\n");
                    output.append(ChatColor.stripColor(extracted));

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
        } catch (Exception ignored) {}
    }

    /**
     * 处理 sendTitle 方法调用
     */
    private Object handleSendTitle(Player player, StringBuilder output, Object[] args) {
        String title = args[0] != null ? args[0].toString() : "";
        String subtitle = args[1] != null ? args[1].toString() : "";

        if (!title.isEmpty() || !subtitle.isEmpty()) {
            try {
                player.sendTitle(title, subtitle,
                    args.length > 2 ? (int) args[2] : 10,
                    args.length > 3 ? (int) args[3] : 70,
                    args.length > 4 ? (int) args[4] : 20);
            } catch (NoSuchMethodError e) {
                player.sendMessage(title + " " + subtitle);
            }
        }

        return null;
    }

    /**
     * 创建 Spigot 拦截器
     */
    private org.bukkit.command.CommandSender.Spigot createSpigotInterceptor(Player player, StringBuilder output) {
        return new org.bukkit.command.CommandSender.Spigot() {
            @Override
            public void sendMessage(net.md_5.bungee.api.chat.BaseComponent component) {
                if (component == null) return;
                String legacyText = net.md_5.bungee.api.chat.TextComponent.toLegacyText(component);
                if (output.length() > 0) output.append("\n");
                output.append(ChatColor.stripColor(legacyText));
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

    /**
     * 获取基本类型的默认值
     */
    private Object getDefaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == double.class) return 0.0;
        if (type == float.class) return 0.0f;
        if (type == long.class) return 0L;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == char.class) return '\0';
        return null;
    }

    /**
     * 处理 #getpreset 工具
     */
    private void handleGetTool(Player player, String fileName) {
        cliManager.setGenerating(player.getUniqueId(), false, CLIManager.GenerationStatus.EXECUTING_TOOL);
        
        File presetFile = new File(plugin.getDataFolder(), "preset/" + fileName);
        if (!presetFile.exists()) {
            cliManager.feedbackToAI(player, "#get_result: 文件不存在");
            return;
        }

        try {
            List<String> lines = Files.readAllLines(presetFile.toPath());
            String content = String.join("\n", lines);
            cliManager.feedbackToAI(player, "#get_result: " + content);
        } catch (IOException e) {
            cliManager.feedbackToAI(player, "#get_result: 读取文件失败 - " + e.getMessage());
        }
    }

    /**
     * 处理 #choose 工具
     */
    private void handleChooseTool(Player player, String optionsStr) {
        String[] options = optionsStr.split(",");
        TextComponent message = new TextComponent(ChatColor.GRAY + "⨀ [ ");

        for (int i = 0; i < options.length; i++) {
            String opt = options[i].trim();
            TextComponent optBtn = new TextComponent(ChatColor.AQUA + opt);
            optBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cli select " + opt));
            optBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(ChatColor.GRAY + "点击选择: " + ChatColor.AQUA + opt)));

            message.addExtra(optBtn);
            if (i < options.length - 1) {
                message.addExtra(ChatColor.GRAY + " | ");
            }
        }
        message.addExtra(ChatColor.GRAY + " ]");

        player.spigot().sendMessage(message);
        cliManager.setPendingCommand(player.getUniqueId(), "CHOOSING");
        cliManager.setGenerating(player.getUniqueId(), false, CLIManager.GenerationStatus.WAITING_CHOICE);
    }

    /**
     * 处理 #search 工具
     */
    private void handleSearchTool(Player player, String query) {
        player.sendMessage(ChatColor.GRAY + "〇 #search: " + query);
        cliManager.setGenerating(player.getUniqueId(), false, CLIManager.GenerationStatus.EXECUTING_TOOL);

        if (!plugin.isEnabled()) return;
        
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String result;
            if (query.toLowerCase().contains("widely")) {
                result = performWideSearch(query);
            } else {
                result = performWikiSearch(query, player);
            }

            final String finalResult = result;
            if (!plugin.isEnabled()) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                cliManager.feedbackToAI(player, "#search_result: " + finalResult);
            });
        });
    }

    /**
     * 执行全网搜索
     */
    private String performWideSearch(String query) {
        String q = query.replace("widely", "").trim();
        if (plugin.getMetasoAPI().isAvailable()) {
            return plugin.getMetasoAPI().search(q);
        } else if (plugin.getConfigManager().isTavilyEnabled()) {
            return plugin.getTavilyAPI().search(q);
        }
        return fetchPublicSearchResult(q);
    }

    /**
     * 执行 Wiki 搜索
     */
    private String performWikiSearch(String query, Player player) {
        String result = fetchWikiResult(query);
        if (result.equals("未找到相关 Wiki 条目。")) {
            // 在主线程发送消息
            if (plugin.isEnabled()) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage(ChatColor.GRAY + "〇 Wiki 无结果，正在尝试全网搜索...");
                });
            }
            if (plugin.getMetasoAPI().isAvailable()) {
                return plugin.getMetasoAPI().search(query);
            } else if (plugin.getConfigManager().isTavilyEnabled()) {
                return plugin.getTavilyAPI().search(query);
            }
            return fetchPublicSearchResult(query);
        }
        return result;
    }

    /**
     * 调用 Minecraft Wiki 公开 API 搜索
     */
    private String fetchWikiResult(String query) {
        try {
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
                
                if (!json.has("query") || json.get("query").isJsonNull()) {
                    return "未找到相关 Wiki 条目。";
                }
                
                com.google.gson.JsonObject queryObj = json.getAsJsonObject("query");
                if (!queryObj.has("search") || queryObj.get("search").isJsonNull()) {
                    return "未找到相关 Wiki 条目。";
                }
                
                com.google.gson.JsonArray searchResults = queryObj.getAsJsonArray("search");

                if (searchResults.size() > 0) {
                    StringBuilder sb = new StringBuilder("Minecraft Wiki 搜索结果：\n");
                    for (int i = 0; i < Math.min(3, searchResults.size()); i++) {
                        com.google.gson.JsonObject item = searchResults.get(i).getAsJsonObject();
                        
                        String title = getStringField(item, "title");
                        String snippet = getStringField(item, "snippet").replaceAll("<[^>]*>", "");
                        
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
     * 调用公开搜索接口
     */
    private String fetchPublicSearchResult(String query) {
        try {
            String url = "https://uapis.cn/api/v1/search/aggregate";

            com.google.gson.JsonObject bodyJson = new com.google.gson.JsonObject();
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
                com.google.gson.JsonArray results = extractResultsArray(response.body());
                
                if (results != null && results.size() > 0) {
                    StringBuilder sb = new StringBuilder("全网搜索结果 (" + query + ")：\n");
                    for (int i = 0; i < Math.min(5, results.size()); i++) {
                        com.google.gson.JsonObject item = results.get(i).getAsJsonObject();
                        String title = getStringField(item, "title", "无标题");
                        String content = getStringField(item, "content", "snippet", "abstract");
                        
                        if (content.length() > 500) {
                            content = content.substring(0, 500) + "...";
                        }
                        sb.append("- ").append(title).append(": ").append(content).append("\n");
                    }
                    return sb.toString();
                }
            } else {
                plugin.getLogger().warning("UAPI 搜索失败: " + response.statusCode());
            }
        } catch (Exception e) {
            return "全网搜索出错: " + e.getMessage();
        }
        return "未找到相关全网搜索结果。";
    }

    /**
     * 从 JSON 响应中提取结果数组
     */
    private com.google.gson.JsonArray extractResultsArray(String responseBody) {
        com.google.gson.JsonElement jsonElement = com.google.gson.JsonParser.parseString(responseBody);

        if (jsonElement.isJsonArray()) {
            return jsonElement.getAsJsonArray();
        } else if (jsonElement.isJsonObject()) {
            com.google.gson.JsonObject jsonObj = jsonElement.getAsJsonObject();
            if (jsonObj.has("data") && jsonObj.get("data").isJsonArray()) {
                return jsonObj.getAsJsonArray("data");
            } else if (jsonObj.has("results") && jsonObj.get("results").isJsonArray()) {
                return jsonObj.getAsJsonArray("results");
            }
        }
        return null;
    }

    /**
     * 从 JSON 对象中获取字符串字段
     */
    private String getStringField(com.google.gson.JsonObject item, String... fieldNames) {
        for (String field : fieldNames) {
            if (item.has(field) && !item.get(field).isJsonNull()) {
                return item.get(field).getAsString();
            }
        }
        return "";
    }

    /**
     * 处理 #todo 工具
     */
    private void handleTodoTool(Player player, String todoJson) {
        UUID uuid = player.getUniqueId();
        cliManager.setGenerating(uuid, false, CLIManager.GenerationStatus.EXECUTING_TOOL);

        String result = plugin.getTodoManager().updateTodos(uuid, todoJson);

        if (result.startsWith("错误")) {
            player.sendMessage(ChatColor.RED + "⨀ " + result);
            cliManager.feedbackToAI(player, "#todo_result: " + result);
        } else {
            net.md_5.bungee.api.chat.TextComponent todoDisplay = plugin.getTodoManager().getTodoDisplayComponent(player);
            player.spigot().sendMessage(todoDisplay);

            String todoDetails = plugin.getTodoManager().getTodoDetails(uuid);
            cliManager.feedbackToAI(player, "#todo_result: " + todoDetails);
        }
    }

    /**
     * 处理 #remember 工具 - 记录玩家偏好
     * 格式: #remember: 内容 或 #remember: 分类|内容
     */
    private void handleRememberTool(Player player, String args) {
        UUID uuid = player.getUniqueId();
        cliManager.setGenerating(uuid, false, CLIManager.GenerationStatus.EXECUTING_TOOL);

        if (args == null || args.trim().isEmpty()) {
            cliManager.feedbackToAI(player, "#remember_result: error - 需要提供要记住的内容，格式: #remember: 内容 或 #remember: 分类|内容");
            return;
        }

        String content = args.trim();
        String category = "general";

        if (content.contains("|")) {
            String[] parts = content.split("\\|", 2);
            if (parts.length == 2) {
                category = parts[0].trim();
                content = parts[1].trim();
            }
        }

        if (content.isEmpty()) {
            cliManager.feedbackToAI(player, "#remember_result: error - 记忆内容不能为空");
            return;
        }

        String result = plugin.getInstructionManager().addInstruction(player, content, category);
        cliManager.feedbackToAI(player, "#remember_result: " + result);
    }

    /**
     * 处理 #forget 工具 - 删除指定记忆
     * 格式: #forget: 序号 或 #forget: all (清空所有)
     */
    private void handleForgetKeyTool(Player player, String args) {
        UUID uuid = player.getUniqueId();
        cliManager.setGenerating(uuid, false, CLIManager.GenerationStatus.EXECUTING_TOOL);

        if (args == null || args.trim().isEmpty()) {
            cliManager.feedbackToAI(player, "#forget_result: error - 需要提供序号或 'all'，格式: #forget: 序号 或 #forget: all");
            return;
        }

        String arg = args.trim();

        if (arg.equalsIgnoreCase("all")) {
            String result = plugin.getInstructionManager().clearInstructions(player);
            cliManager.feedbackToAI(player, "#forget_result: " + result);
            return;
        }

        try {
            int index = Integer.parseInt(arg);
            String result = plugin.getInstructionManager().removeInstruction(player, index);
            cliManager.feedbackToAI(player, "#forget_result: " + result);
        } catch (NumberFormatException e) {
            cliManager.feedbackToAI(player, "#forget_result: error - 无效的序号: " + arg);
        }
    }

    /**
     * 处理 #editmem 工具 - 修改指定记忆
     * 格式: #editmem: 序号|新内容 或 #editmem: 序号|分类|新内容
     */
    private void handleEditmemTool(Player player, String args) {
        UUID uuid = player.getUniqueId();
        cliManager.setGenerating(uuid, false, CLIManager.GenerationStatus.EXECUTING_TOOL);

        if (args == null || args.trim().isEmpty()) {
            cliManager.feedbackToAI(player, "#editmem_result: error - 需要提供序号和新内容，格式: #editmem: 序号|新内容 或 #editmem: 序号|分类|新内容");
            return;
        }

        String[] parts = args.trim().split("\\|", 3);
        
        if (parts.length < 2) {
            cliManager.feedbackToAI(player, "#editmem_result: error - 格式错误，正确格式: #editmem: 序号|新内容 或 #editmem: 序号|分类|新内容");
            return;
        }

        try {
            int index = Integer.parseInt(parts[0].trim());
            String content;
            String category;

            if (parts.length == 2) {
                category = "general";
                content = parts[1].trim();
            } else {
                category = parts[1].trim();
                content = parts[2].trim();
            }

            if (content.isEmpty()) {
                cliManager.feedbackToAI(player, "#editmem_result: error - 记忆内容不能为空");
                return;
            }

            String result = plugin.getInstructionManager().updateInstruction(player, index, content, category);
            cliManager.feedbackToAI(player, "#editmem_result: " + result);
        } catch (NumberFormatException e) {
            cliManager.feedbackToAI(player, "#editmem_result: error - 无效的序号: " + parts[0].trim());
        }
    }

    /**
     * 处理 #ask_questions 工具
     * 格式: #ask_questions: [{"id":"1","type":"text","text":"问题文本","required":true,"order":1}]
     * 
     * @param player 玩家
     * @param args 工具参数（JSON数组）
     * @param session 对话会话
     * @return 是否成功
     */
    private boolean handleAskQuestionsTool(Player player, String args, DialogueSession session) {
        UUID uuid = player.getUniqueId();
        cliManager.setGenerating(uuid, false, CLIManager.GenerationStatus.EXECUTING_TOOL);
        
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // 解析JSON
                JsonElement jsonElement = gson.fromJson(args, JsonElement.class);

                if (jsonElement == null || !jsonElement.isJsonArray()) {
                    plugin.getLogger().warning("[PLAN] #ask_questions JSON解析结果不是数组: " + jsonElement);
                    String errorMsg = "#ask_questions_result: 错误: 问题数据必须是数组格式，格式如 [{\"id\":\"1\",\"type\":\"text\",\"text\":\"问题\",\"order\":1}]";
                    feedbackErrorToMain(player, uuid, errorMsg);
                    return;
                }

                JsonArray jsonArray = jsonElement.getAsJsonArray();
                List<Question> questions = new ArrayList<>();
                for (int i = 0; i < jsonArray.size(); i++) {
                    JsonObject questionObj = jsonArray.get(i).getAsJsonObject();
                    Question question = parseQuestion(questionObj, i + 1);
                    questions.add(question);
                }
                
                final List<Question> finalQuestions = questions;
                if (!plugin.isEnabled()) return;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    plugin.getPlanManager().setQuestions(player, finalQuestions);
                    // 不向AI发送结果，让AI停止并等待用户回答问题
                });
            } catch (JsonSyntaxException e) {
                plugin.getLogger().warning("[PLAN] #ask_questions JSON语法错误: " + e.getMessage() + ", 原始数据: " + args);
                String errorMsg = "#ask_questions_result: 错误: JSON格式无效 - " + e.getMessage() + "。请确保格式为 [{\"id\":\"1\",\"type\":\"text\",\"text\":\"问题\",\"order\":1}]，options必须是数组格式如[\"选项1\",\"选项2\"]";
                feedbackErrorToMain(player, uuid, errorMsg);
            } catch (Exception e) {
                plugin.getCloudErrorReport().report(e);
                plugin.getLogger().warning("[PLAN] #ask_questions解析错误: " + e.getMessage() + ", 原始数据: " + args);
                String errorMsg = "#ask_questions_result: 错误: 解析失败 - " + e.getMessage();
                feedbackErrorToMain(player, uuid, errorMsg);
            }
        });
        
        return true;
    }

    /**
     * 异步任务中反馈错误到主线程
     */
    private void feedbackErrorToMain(Player player, UUID uuid, String errorMsg) {
        if (!plugin.isEnabled()) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            player.sendMessage(ChatColor.RED + "⨀ " + errorMsg.replace("#ask_questions_result: ", "").replace("#create_plan_result: ", ""));
            cliManager.feedbackToAI(player, errorMsg);
        });
    }

    /**
     * 处理 #create_plan 工具
     * 格式: #create_plan: {"title":"计划标题","description":"描述","steps":[{"order":1,"description":"步骤描述"}]}
     * 
     * @param player 玩家
     * @param args 工具参数（JSON对象）
     * @param session 对话会话
     * @return 是否成功
     */
    private boolean handleCreatePlanTool(Player player, String args, DialogueSession session) {
        UUID uuid = player.getUniqueId();
        cliManager.setGenerating(uuid, false, CLIManager.GenerationStatus.EXECUTING_TOOL);
        
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // 解析JSON
                JsonObject jsonObject = gson.fromJson(args, JsonObject.class);

                if (jsonObject == null || !jsonObject.has("title")) {
                    plugin.getLogger().warning("[PLAN] #create_plan JSON缺少title字段: " + jsonObject);
                    String errorMsg = "#create_plan_result: 错误: 计划必须包含title字段，格式如 {\"title\":\"计划标题\",\"steps\":[{\"order\":1,\"description\":\"步骤\"}]}";
                    feedbackErrorToMain(player, uuid, errorMsg);
                    return;
                }

                if (!jsonObject.has("steps") || !jsonObject.get("steps").isJsonArray()) {
                    plugin.getLogger().warning("[PLAN] #create_plan JSON的steps字段不是数组: " + jsonObject);
                    String errorMsg = "#create_plan_result: 错误: steps必须是数组格式，格式如 [{\"order\":1,\"description\":\"步骤\"}]";
                    feedbackErrorToMain(player, uuid, errorMsg);
                    return;
                }

                String title = jsonObject.get("title").getAsString();
                String description = jsonObject.has("description") ? jsonObject.get("description").getAsString() : "";
                
                ExecutionPlan plan = new ExecutionPlan(title, description);
                
                JsonArray stepsArray = jsonObject.get("steps").getAsJsonArray();
                for (int i = 0; i < stepsArray.size(); i++) {
                    JsonObject stepObj = stepsArray.get(i).getAsJsonObject();
                    
                    int order;
                    JsonElement orderElement = stepObj.get("order");
                    if (orderElement.isJsonPrimitive() && orderElement.getAsJsonPrimitive().isNumber()) {
                        order = orderElement.getAsInt();
                    } else if (orderElement.isJsonPrimitive() && orderElement.getAsJsonPrimitive().isString()) {
                        try {
                            order = Integer.parseInt(orderElement.getAsString());
                        } catch (NumberFormatException e) {
                            order = i + 1; // 使用默认值
                        }
                    } else {
                        order = i + 1; // 使用默认值
                    }
                    
                    String stepDesc = stepObj.get("description").getAsString();
                    String notes = stepObj.has("notes") ? stepObj.get("notes").getAsString() : "";
                    
                    PlanStep step = new PlanStep(order, stepDesc);
                    step.setNotes(notes);
                    plan.addStep(step);
                }
                
                final ExecutionPlan finalPlan = plan;
                if (!plugin.isEnabled()) return;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    plugin.getPlanManager().createPlan(player, finalPlan);
                    // 不向AI发送结果，让AI停止并等待用户选择执行模式
                });
            } catch (JsonSyntaxException e) {
                plugin.getLogger().warning("[PLAN] #create_plan JSON语法错误: " + e.getMessage() + ", 原始数据: " + args);
                String errorMsg = "#create_plan_result: 错误: JSON格式无效 - " + e.getMessage() + "。请确保格式为 {\"title\":\"计划标题\",\"steps\":[{\"order\":1,\"description\":\"步骤\"}]}";
                feedbackErrorToMain(player, uuid, errorMsg);
            } catch (Exception e) {
                plugin.getCloudErrorReport().report(e);
                plugin.getLogger().warning("[PLAN] #create_plan解析错误: " + e.getMessage() + ", 原始数据: " + args);
                String errorMsg = "#create_plan_result: 错误: 解析失败 - " + e.getMessage();
                feedbackErrorToMain(player, uuid, errorMsg);
            }
        });
        
        return true;
    }

    /**
     * 解析问题JSON
     * 
     * @param obj JSON对象
     * @param defaultOrder 默认顺序
     * @return Question对象
     */
    private Question parseQuestion(JsonObject obj, int defaultOrder) {
        String id = obj.has("id") ? obj.get("id").getAsString() : "q_" + System.currentTimeMillis();
        String typeStr = obj.get("type").getAsString();
        String text = obj.get("text").getAsString();
        int order = obj.has("order") ? obj.get("order").getAsInt() : defaultOrder;
        boolean required = !obj.has("required") || obj.get("required").getAsBoolean();
        
        Question.QuestionType type;
        switch (typeStr.toLowerCase()) {
            case "checkbox":
                type = Question.QuestionType.CHECKBOX;
                break;
            case "radio":
                type = Question.QuestionType.RADIO;
                break;
            case "text":
            default:
                type = Question.QuestionType.TEXT;
                break;
        }
        
        Question question = new Question(id, type, text, order);
        question.setRequired(required);
        
        // 解析选项（如果有）
        if (obj.has("options") && obj.get("options").isJsonArray()) {
            JsonArray optionsArray = obj.get("options").getAsJsonArray();
            List<String> options = new ArrayList<>();
            for (int i = 0; i < optionsArray.size(); i++) {
                options.add(optionsArray.get(i).getAsString());
            }
            question.setOptions(options);
        }
        
        return question;
    }
}
