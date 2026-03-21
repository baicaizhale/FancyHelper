package org.YanPl.manager;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.YanPl.FancyHelper;
import org.YanPl.model.DialogueSession;
import org.YanPl.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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

        // 记录工具调用日志
        if (session != null) {
            session.appendLog("TOOL_EXECUTION", "Executing tool: " + toolCall);
            // 清除之前的错误信息
            session.setLastError(null);
        }

        // 解析工具名称和参数
        ToolParseResult parseResult = parseToolCall(toolCall);
        String toolName = parseResult.toolName;
        String args = parseResult.args;

        if (plugin.getConfigManager().isDebug()) {
            plugin.getLogger().info("[CLI] 正在为 " + player.getName() + " 执行工具: " + toolName + " (参数: " + args + ")");
        }

        // 显示工具调用信息
        displayToolCall(player, toolName, args);

        // 执行对应的工具
        boolean success = true;
        String lowerToolName = toolName.toLowerCase();

        switch (lowerToolName) {
            // 会话管理工具
            case "#end":
                cliManager.setGenerating(uuid, false, CLIManager.GenerationStatus.COMPLETED);
                break;
            case "#exit":
                cliManager.exitCLI(player);
                break;
            
            // 执行工具
            case "#run":
                success = handleRunTool(player, args, session);
                break;
            
            // 文件工具
            case "#list":
                handleFileTool(player, "ls", args, session);
                break;
            case "#read":
                handleFileTool(player, "read", args, session);
                break;
            case "#edit":
                handleFileTool(player, "diff", args, session);
                break;
            case "#getpreset":
                handleGetTool(player, args);
                break;
            
            // 交互工具
            case "#choose":
                handleChooseTool(player, args);
                break;
            
            // 搜索工具
            case "#search":
                handleSearchTool(player, args);
                break;
            
            // 任务工具
            case "#todo":
                handleTodoTool(player, args);
                break;
            
            // 记忆工具
            case "#remember":
                handleRememberTool(player, args);
                break;
            case "#forget":
                handleForgetKeyTool(player, args);
                break;
            case "#edit_memory":
                handleEditmemTool(player, args);
                break;
            
            default:
                player.sendMessage(ChatColor.RED + "未知工具: " + toolName);
                String error = "#error: 未知工具 " + toolName + "。请仅使用系统提示中定义的工具。";
                cliManager.feedbackToAI(player, error);
                if (session != null) {
                    session.setLastError(error);
                }
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
     * 支持统一的工具调用格式：#工具名: 参数
     */
    public ToolParseResult parseToolCall(String toolCall) {
        String toolName;
        String args = "";

        // 查找第一个冒号的位置
        int colonIndex = toolCall.indexOf(":");

        if (colonIndex != -1) {
            toolName = toolCall.substring(0, colonIndex).trim();
            args = toolCall.substring(colonIndex + 1).trim();
        } else {
            // 兼容旧格式，查找第一个空格
            int spaceIndex = toolCall.indexOf(" ");
            if (spaceIndex != -1) {
                toolName = toolCall.substring(0, spaceIndex).trim();
                args = toolCall.substring(spaceIndex + 1).trim();
            } else {
                toolName = toolCall.trim();
            }
        }

        return new ToolParseResult(toolName, args);
    }

    /**
     * 显示工具调用信息给玩家
     */
    private void displayToolCall(Player player, String toolName, String args) {
        String lowerToolName = toolName.toLowerCase();

        if (lowerToolName.equals("#remember") || lowerToolName.equals("#forget") ||
            lowerToolName.equals("#edit_memory")) {
            TextComponent message = new TextComponent(ChatColor.WHITE + "⁕ Fancy 正在记住你说的话.. ");
            TextComponent manageBtn = new TextComponent("[管理记忆]");
            manageBtn.setColor(net.md_5.bungee.api.ChatColor.of(ColorUtil.getColorZ()));
            manageBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cli memory"));
            manageBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(ChatColor.GRAY + "点击管理偏好记忆")));
            message.addExtra(manageBtn);
            player.spigot().sendMessage(message);
        } else if (lowerToolName.equals("#edit")) {
            String[] parts = args.split("\\|", 3);
            String path = parts.length > 0 ? parts[0].trim() : "";
            player.sendMessage(ChatColor.GRAY + "〇 正在修改文件: " + ChatColor.WHITE + path);
            if (parts.length >= 3) {
                player.sendMessage(ChatColor.GRAY + "From " + ChatColor.WHITE + parts[1]);
                player.sendMessage(ChatColor.GRAY + "To " + ChatColor.WHITE + parts[2]);
            }
        } else if (lowerToolName.equals("#exit")) {
            player.sendMessage(ChatColor.GRAY + "〇 Exiting...");
        } else if (!lowerToolName.equals("#search") && !lowerToolName.equals("#run") && 
            !lowerToolName.equals("#end") && !lowerToolName.equals("#list") && 
            !lowerToolName.equals("#read") && !lowerToolName.equals("#todo") &&
            !lowerToolName.equals("#getpreset")) {
            player.sendMessage(ChatColor.GRAY + "〇 " + toolName);
        }
    }

    /**
     * 处理 #run 工具
     */
    private boolean handleRunTool(Player player, String command, DialogueSession session) {
        if (command.isEmpty()) {
            player.sendMessage(ChatColor.RED + "错误: #run 工具需要提供命令参数");
            String error = "#error: #run 工具需要提供命令参数，例如 #run: say hello";
            cliManager.feedbackToAI(player, error);
            if (session != null) {
                session.setLastError(error);
            }
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
                player.sendMessage(ChatColor.GOLD + ">> YOLO RUN " + ChatColor.WHITE + cleanCommand);
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
        String cleanCmd = cmd.trim();
        if (cleanCmd.toLowerCase().startsWith("minecraft:")) {
            cleanCmd = cleanCmd.substring(10).trim();
        }
        
        // 处理 execute 命令的递归检查
        if (cleanCmd.toLowerCase().startsWith("execute")) {
            String lower = cleanCmd.toLowerCase();
            int runIndex = lower.indexOf(" run ");
            if (runIndex != -1) {
                String subCmd = cleanCmd.substring(runIndex + 5).trim();
                return isRiskyCommand(subCmd);
            }
        }

        List<String> risky = plugin.getConfigManager().getYoloRiskCommands();
        if (risky == null || risky.isEmpty()) return false;
        
        String lc = cleanCmd.toLowerCase();
        for (String r : risky) {
            if (r == null) continue;
            String rr = r.trim().toLowerCase();
            if (rr.isEmpty()) continue;
            
            // 精确匹配命令名或带参数的命令
            if (lc.equals(rr)) return true;
            if (lc.startsWith(rr + " ")) return true;
        }
        return false;
    }

    /**
     * 处理文件工具 (#ls, #read, #diff)
     */
    private void handleFileTool(Player player, String type, String args, DialogueSession session) {
        UUID uuid = player.getUniqueId();

        if ("read".equals(type)) {
            String pathArg = args == null ? "" : args.trim();
            String[] parts = pathArg.split("\\s+");
            String path = parts.length > 0 ? parts[0] : "";
            try {
                String cleaned = path.startsWith("/") || path.startsWith("\\") ? path.substring(1) : path;
                java.io.File worldRoot = org.bukkit.Bukkit.getWorldContainer();
                java.io.File target = new java.io.File(worldRoot, cleaned).getCanonicalFile();
                java.io.File presetDir = new java.io.File(plugin.getDataFolder(), "preset").getCanonicalFile();
                if (target.getPath().startsWith(presetDir.getPath() + java.io.File.separator) || target.equals(presetDir)) {
                    String name = target.getName();
                    player.sendMessage(org.bukkit.ChatColor.YELLOW + "提示: 预设文件请使用 #getpreset: " + name);
                    cliManager.feedbackToAI(player, "#read_result: 错误 - 预设文件请使用 #getpreset: " + name);
                    return;
                }
            } catch (Exception ignored) {}
        }

        // #ls 和 #read 不需要确认，直接执行
        if ("ls".equals(type) || "read".equals(type)) {
            String actionDesc = type.equals("ls") ? "LIST" : "READ";
            player.sendMessage(ChatColor.GOLD + ">> " + actionDesc + " " + ChatColor.WHITE + args);

            // 检查是否被冻结
            long freezeRemaining = plugin.getVerificationManager().getPlayerFreezeRemaining(player);
            if (freezeRemaining > 0) {
                player.sendMessage(ChatColor.RED + "验证已冻结，请在 " + freezeRemaining + " 秒后重试。");
                return;
            }

            // 检查权限开启
            if (plugin.getConfigManager().isPlayerToolEnabled(player, type)) {
                cliManager.setGenerating(uuid, false, CLIManager.GenerationStatus.EXECUTING_TOOL);
                executeFileOperation(player, type, args);
            } else {
                player.sendMessage(ChatColor.YELLOW + "检测到调用 " + type + "，但该工具尚未完成首次验证。");
                plugin.getVerificationManager().startVerification(player, type, () -> {
                    plugin.getConfigManager().setPlayerToolEnabled(player, type, true);
                    cliManager.setGenerating(uuid, false, CLIManager.GenerationStatus.EXECUTING_TOOL);
                    executeFileOperation(player, type, args);
                });
            }
            return;
        }

        // #edit (diff) 需要确认
        String pendingStr = type.toUpperCase() + ":" + args;
        cliManager.setPendingCommand(uuid, pendingStr);
        cliManager.setGenerating(uuid, false, CLIManager.GenerationStatus.WAITING_CONFIRM);
        sendConfirmButtons(player, "");
    }

    /**
     * 发送确认按钮
     */
    public void sendConfirmButtons(Player player, String displayAction) {
        TextComponent message = new TextComponent(displayAction != null && !displayAction.trim().isEmpty() 
            ? (ChatColor.GRAY + ">> " + ChatColor.WHITE + displayAction + " ") : "");

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
        String[] parts = pathArg.split("\\s+");
        String path = parts[0];
        int startLine = 1;
        int endLine = -1;

        if (parts.length > 1) {
            String range = parts[1];
            try {
                if (range.contains("-")) {
                    String[] rangeParts = range.split("-");
                    if (rangeParts.length > 0 && !rangeParts[0].isEmpty()) {
                        startLine = Integer.parseInt(rangeParts[0]);
                    }
                    if (rangeParts.length > 1 && !rangeParts[1].isEmpty()) {
                        endLine = Integer.parseInt(rangeParts[1]);
                    }
                } else {
                    startLine = Integer.parseInt(range);
                    endLine = startLine; // Read single line
                }
            } catch (NumberFormatException ignored) {}
        }

        File file = new File(root, path);
        
        if (!isWithinRoot(root, file)) {
            return "错误: 路径超出服务器目录限制";
        }
        if (!file.exists()) {
            return "错误: 文件不存在";
        }
        if (file.isDirectory()) {
            return "错误: 这是一个目录，请使用 #ls";
        }
        if (file.length() > 1024 * 1024) {
            return "错误: 文件过大 (" + (file.length() / 1024) + "KB)，无法读取。";
        }

        StringBuilder content = new StringBuilder();
        try (java.io.BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            String line;
            int currentLine = 1;
            int maxLines = 500;
            int readCount = 0;
            
            while ((line = reader.readLine()) != null) {
                boolean inRange = true;
                if (currentLine < startLine) inRange = false;
                if (endLine != -1 && currentLine > endLine) inRange = false;

                if (inRange) {
                    if (readCount >= maxLines) {
                        content.append("\n... (内容过长，已截断显示 " + maxLines + " 行) ...");
                        break;
                    }
                    content.append(line).append("\n");
                    readCount++;
                }
                currentLine++;
            }
        }
        return content.toString();
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
        
        // 检查是否是行号范围模式
        // 格式：#diff: path | 10-15 | content
        boolean isLineMode = false;
        int startLine = -1;
        int endLine = -1;
        
        if (search.trim().matches("^\\d+-\\d+$")) {
            try {
                String[] range = search.trim().split("-");
                startLine = Integer.parseInt(range[0]);
                endLine = Integer.parseInt(range[1]);
                isLineMode = true;
            } catch (NumberFormatException ignored) {}
        }

        File file = new File(root, path);
        
        if (!isWithinRoot(root, file)) {
            return "错误: 路径超出服务器目录限制";
        }
        if (!file.exists()) {
            return "错误: 文件不存在";
        }

        if (isLineMode) {
            // 行号模式替换
            List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            if (startLine < 1 || startLine > lines.size() + 1) { // 允许追加到末尾
                 return "错误: 起始行号无效 (文件总行数: " + lines.size() + ")";
            }
            if (endLine > lines.size()) {
                 endLine = lines.size(); // 自动修正结束行号
            }
            if (startLine > endLine) {
                 // 可能是插入操作？暂时不允许反向范围
                 return "错误: 起始行号不能大于结束行号";
            }
            
            List<String> newLines = new java.util.ArrayList<>();
            
            // 复制前面的行
            for (int i = 0; i < startLine - 1; i++) {
                newLines.add(lines.get(i));
            }
            
            // 插入新内容
            if (replace != null && !replace.isEmpty()) {
                String[] replaceLines = replace.split("\n");
                for (String rLine : replaceLines) {
                    newLines.add(rLine);
                }
            }
            
            // 复制后面的行
            for (int i = endLine; i < lines.size(); i++) {
                newLines.add(lines.get(i));
            }
            
            Files.write(file.toPath(), newLines, StandardCharsets.UTF_8);
            return "成功修改文件 (行号模式): " + path + " [" + startLine + "-" + endLine + "]";
        }

        // 原有的内容匹配模式
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

        int index = content.indexOf(search);
        if (index == -1) {
            return "错误: 未在文件中找到指定的查找内容，请确保查找内容完全匹配（包括缩进）";
        }

        String newContent = content.substring(0, index) + replace + content.substring(index + search.length());
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
            String rootPath = root.getCanonicalPath();
            String filePath = file.getCanonicalPath();

            if (!rootPath.endsWith(File.separator)) {
                rootPath += File.separator;
            }

            return filePath.equals(root.getCanonicalPath()) || filePath.startsWith(rootPath);
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

            player.sendMessage(ChatColor.GRAY + "⇒ 命令已下发，等待反馈中...");

            boolean success;
            try {
                org.bukkit.command.CommandSender interceptor = createInterceptor(player, output);
                success = Bukkit.dispatchCommand(interceptor, command);
            } catch (Throwable t) {
                success = player.performCommand(command);
            }

            boolean finalSuccess = success;

            if (!plugin.isEnabled()) return;
            
            // 延迟 1 秒 (20 ticks) 检查反馈
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                String currentPacketOutput = "";
                String currentProxyOutput = output.toString();
                boolean hasOutput = false;

                // 检查 PacketCapture 是否有内容
                if (plugin.getPacketCaptureManager() != null) {
                    currentPacketOutput = plugin.getPacketCaptureManager().peekCapture(player);
                    if (!currentPacketOutput.isEmpty()) hasOutput = true;
                }
                // 检查拦截器是否有内容
                if (!currentProxyOutput.isEmpty()) hasOutput = true;

                // 如果有输出，或者命令执行失败，则立即结束
                if (hasOutput || !finalSuccess) {
                    String finalPacketOutput = "";
                    if (plugin.getPacketCaptureManager() != null) {
                        // 如果拦截器已有输出，则忽略数据包捕获的输出，避免广播消息污染
                        if (!currentProxyOutput.isEmpty()) {
                            // 停止捕获但丢弃输出
                            plugin.getPacketCaptureManager().stopCapture(player);
                            finalPacketOutput = "";
                        } else {
                            finalPacketOutput = plugin.getPacketCaptureManager().stopCapture(player);
                        }
                    }
                    String finalResult = buildCommandResult(command, finalPacketOutput, currentProxyOutput, finalSuccess);
                    player.sendMessage(ChatColor.GRAY + "⇒ 反馈已发送至 Fancy");
                    cliManager.feedbackToAI(player, "#run_result: " + finalResult);
                    return;
                }

                // 如果没有输出，延长等待 5 秒 (100 ticks)
                player.sendMessage(ChatColor.GRAY + "⇒ 暂无反馈，延长等待 5秒...");
                
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    String delayedPacketOutput = "";
                    if (plugin.getPacketCaptureManager() != null) {
                        delayedPacketOutput = plugin.getPacketCaptureManager().stopCapture(player);
                    }
                    
                    String finalResult = buildCommandResult(command, delayedPacketOutput, output.toString(), finalSuccess);
                    player.sendMessage(ChatColor.GRAY + "⇒ 反馈已发送至 Fancy");
                    cliManager.feedbackToAI(player, "#run_result: " + finalResult);
                }, 100L);

            }, 20L);
        });
    }

    /**
     * 构建命令执行结果
     */
    private String buildCommandResult(String command, String packetOutput, String proxyOutput, boolean success) {
        if (!proxyOutput.isEmpty()) {
            return proxyOutput;
        }
        if (!packetOutput.isEmpty()) {
            return packetOutput;
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
        // 显示正在读取预设的信息
        player.sendMessage(ChatColor.GRAY + "〇 正在读取预设...");
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
}
