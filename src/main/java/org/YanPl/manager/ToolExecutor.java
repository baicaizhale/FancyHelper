package org.YanPl.manager;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.YanPl.FancyHelper;
import org.YanPl.model.DialogueSession;
import org.YanPl.mcp.client.McpClientManager;
import org.YanPl.mcp.core.McpTypes;
import org.YanPl.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;


/**
 * 工具执行器，负责处理 AI 发起的各类工具调用
 * 从 CLIManager 中提取出来以降低复杂度
 */
public class ToolExecutor {
    private final FancyHelper plugin;
    private final CLIManager cliManager;
    private final RiskAssessmentManager riskAssessmentManager;

    public ToolExecutor(FancyHelper plugin, CLIManager cliManager) {
        this.plugin = plugin;
        this.cliManager = cliManager;
        this.riskAssessmentManager = new RiskAssessmentManager(plugin);
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

        // Plan Mode 工具白名单检查（必须在 displayToolCall 之前，避免显示被拒绝的工具）
        if (session != null && session.getMode() == DialogueSession.Mode.PLAN) {
            if (!isPlanModeTool(toolName)) {
                String error = "#error: 当前处于 Plan Mode，仅允许规划相关工具。使用 #start 结束规划并开始执行。";
                cliManager.feedbackToAI(player, error);
                session.setLastError(error);
                session.appendLog("PLAN_MODE_BLOCKED", "Blocked tool in plan mode: " + toolName);
                return false;
            }
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
            case "#start":
                handleStartTool(player);
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
            case "#write":
                handleFileTool(player, "write", args, session);
                break;
            case "#edit":
                handleFileTool(player, "edit", args, session);
                break;
            case "#skill":
                handleSkillTool(player, args, session);
                break;
            case "#unloadskill":
                handleUnloadSkillTool(player, args, session);
                break;

            // 交互工具
            case "#ask":
                handleAskTool(player, args);
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
            
            // 网页阅读工具
            case "#webfetch":
                handleWebFetchTool(player, args, session);
                break;

            // MCP 外部工具
            case "#mcp_tools":
                handleMcpToolsList(player);
                break;
            case "#mcp":
                success = handleMcpTool(player, args, session);
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
        } else if (lowerToolName.equals("#exit")) {
            player.sendMessage(ChatColor.GRAY + "〇 Exiting...");
        } else if (lowerToolName.equals("#skill")) {
            String skillId = args.trim().toLowerCase();
            org.YanPl.model.Skill skill = plugin.getSkillManager().getSkill(skillId);
            String skillName = skill != null ? skill.getDisplayName() : args;
            player.sendMessage(ChatColor.GRAY + "◌ Run Skill: " + skillName);
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

        // SMART 模式下评估风险
        if (session != null && session.getMode() == DialogueSession.Mode.SMART) {
            player.sendMessage(ChatColor.GRAY + "⁕ 正在评估操作风险...");
            cliManager.setGenerating(uuid, false, CLIManager.GenerationStatus.THINKING);
            
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                RiskAssessmentManager.RiskAssessment assessment = 
                    riskAssessmentManager.assessRisk("run", cleanCommand);
                
                Bukkit.getScheduler().runTask(plugin, () -> {
                    int threshold = plugin.getConfigManager().getSmartRiskThreshold();
                    if (assessment.level >= threshold) {
                        cliManager.sendSmartRiskConfirm(player, "run", cleanCommand, assessment);
                    } else {
                        player.sendMessage(ChatColor.GOLD + ">> SMART RUN " + ChatColor.WHITE + cleanCommand);
                        cliManager.setGenerating(uuid, false, CLIManager.GenerationStatus.EXECUTING_TOOL);
                        executeCommand(player, cleanCommand);
                    }
                });
            });
            return true;
        }

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
     * 处理文件工具 (#ls, #read, #edit)
     */
    private void handleFileTool(Player player, String type, String args, DialogueSession session) {
        UUID uuid = player.getUniqueId();

        // 提取路径用于显示和 read 跟踪
        String pathArg = args == null ? "" : args.trim();

        // #ls 和 #read 不需要确认，直接执行
        if ("ls".equals(type) || "read".equals(type)) {
            // 显示工具调用信息
            String displayType = type.equals("ls") ? "ListDir" : "ReadFile";
            String[] parts = pathArg.split("\\s+");
            String displayPath = parts.length > 0 ? parts[0] : "";
            player.sendMessage(ChatColor.GRAY + ">> " + ChatColor.WHITE + displayType + " " + displayPath);

            // 检查是否被冻结
            long freezeRemaining = plugin.getVerificationManager().getPlayerFreezeRemaining(player);
            if (freezeRemaining > 0) {
                player.sendMessage(ChatColor.RED + "验证已冻结，请在 " + freezeRemaining + " 秒后重试。");
                return;
            }

            // 检查权限开启
            // 将内部类型映射到配置中的工具名称
            String toolName = mapTypeToToolName(type);
            if (plugin.getConfigManager().isPlayerToolEnabled(player, toolName)) {
                cliManager.setGenerating(uuid, false, CLIManager.GenerationStatus.EXECUTING_TOOL);
                // 记录已读取的文件（用于 #write 的 read-before-write 检查），规范化路径
                if ("read".equals(type) && session != null && !displayPath.isEmpty()) {
                    String normalized = displayPath.replace('\\', '/');
                    if (normalized.startsWith("./")) normalized = normalized.substring(2);
                    session.addReadFile(normalized);
                }
                executeFileOperation(player, type, args);
            } else {
                player.sendMessage(ChatColor.YELLOW + "检测到调用 " + toolName + "，但该工具尚未完成首次验证。");
                plugin.getVerificationManager().startVerification(player, toolName, () -> {
                    plugin.getConfigManager().setPlayerToolEnabled(player, toolName, true);
                    cliManager.setGenerating(uuid, false, CLIManager.GenerationStatus.EXECUTING_TOOL);
                    if ("read".equals(type) && session != null && !displayPath.isEmpty()) {
                        String normalized = displayPath.replace('\\', '/');
                        if (normalized.startsWith("./")) normalized = normalized.substring(2);
                        session.addReadFile(normalized);
                    }
                    executeFileOperation(player, type, args);
                });
            }
            return;
        }

        // #write 的 read-before-write 检查
        if ("write".equals(type)) {
            String writePath = pathArg.contains("|") ? pathArg.substring(0, pathArg.indexOf("|")).trim() : pathArg.trim();
            if (!writePath.isEmpty()) {
                File root = Bukkit.getWorldContainer();
                File targetFile = new File(root, writePath);
                if (targetFile.exists()) {
                    // 规范化路径用于对比
                    String normalizedPath;
                    try {
                        normalizedPath = root.toPath().relativize(targetFile.toPath()).toString().replace('\\', '/');
                    } catch (Exception e) {
                        normalizedPath = writePath.replace('\\', '/');
                    }
                    if (session == null || !session.hasReadFile(normalizedPath)) {
                        String errorMsg = "错误：文件 " + normalizedPath + " 已存在。请先使用 #read 读取该文件后再使用 #write。";
                        cliManager.feedbackToAI(player, "#write_result: " + errorMsg);
                        player.sendMessage(ChatColor.RED + errorMsg);
                        return;
                    }
                }
            }
        }

        // #edit 和 #write 需要确认（YOLO模式除外，SMART模式也不特殊处理，与NORMAL一致）
        if (session != null && session.getMode() == DialogueSession.Mode.YOLO) {
            String pendingStr = type.toUpperCase() + ":" + args;
            cliManager.setPendingCommand(uuid, pendingStr);
            cliManager.setGenerating(uuid, false, CLIManager.GenerationStatus.EXECUTING_TOOL);
            executeFileOperation(player, type, args);
            return;
        }

        // NORMAL 和 SMART 模式：显示文件路径 + 确认按钮在一行
        String pendingStr = type.toUpperCase() + ":" + args;
        cliManager.setPendingCommand(uuid, pendingStr);
        cliManager.setGenerating(uuid, false, CLIManager.GenerationStatus.WAITING_CONFIRM);

        String[] parts = args.split("\\|", "edit".equals(type) ? 4 : 2);
        String filePath = parts.length > 0 ? parts[0].trim() : "";
        String label = "edit".equals(type) ? "修改" : "覆写";
        TextComponent msg = new TextComponent(ChatColor.GRAY + "✍ 正在" + label + "文件 " + ChatColor.WHITE + filePath + "    ");
        TextComponent yBtn = new TextComponent(ChatColor.GREEN + "✔");
        yBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cli confirm"));
        yBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(ChatColor.GRAY + "确认执行操作")));
        msg.addExtra(yBtn);
        msg.addExtra(new TextComponent(" / "));
        TextComponent nBtn = new TextComponent(ChatColor.RED + "✘");
        nBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cli cancel"));
        nBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(ChatColor.GRAY + "取消执行")));
        msg.addExtra(nBtn);
        player.spigot().sendMessage(msg);
    }

    /**
     * 发送确认按钮
     */
    public void sendConfirmButtons(Player player, String displayAction) {
        TextComponent message = new TextComponent(displayAction != null && !displayAction.trim().isEmpty() 
            ? (ChatColor.GRAY + ">> " + ChatColor.WHITE + displayAction + "   ") : "");

        TextComponent yBtn = new TextComponent(ChatColor.GREEN + "✔");
        yBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cli confirm"));
        yBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("确认执行操作")));

        TextComponent spacer = new TextComponent(" / ");

        TextComponent nBtn = new TextComponent(ChatColor.RED + "✘");
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

                if (!plugin.isEnabled()) return;
                // 先展示文件操作结果，不等待 view-fancy 上传
                Bukkit.getScheduler().runTask(plugin, () -> {
                    displayFileOperationResult(player, type, result);
                    cliManager.feedbackToAI(player, "#" + type + "_result: " + result);
                });

                // #write 成功后异步推送到 view-fancy，不阻塞结果展示
                if ("write".equals(type) && result.startsWith("成功写入文件:")) {
                    String viewUrl = submitToViewFancy(args);
                    if (viewUrl != null && plugin.isEnabled()) {
                        final String fViewUrl = viewUrl;
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            TextComponent link = new TextComponent(ChatColor.GRAY + "📄 在线查看: ");
                            TextComponent urlComp = new TextComponent(ChatColor.AQUA + "" + ChatColor.UNDERLINE + fViewUrl);
                            urlComp.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, fViewUrl));
                            urlComp.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(ChatColor.GRAY + "点击在浏览器中查看")));
                            link.addExtra(urlComp);
                            player.spigot().sendMessage(link);
                        });
                    }
                }
            } catch (Exception e) {
                plugin.getCloudErrorReport().report(e);
                if (!plugin.isEnabled()) return;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    cliManager.feedbackToAI(player, "#" + type + "_result: 错误 - " + e.getMessage());
                });
            }
        });
    }

    private String submitToViewFancy(String args) {
        int pipeIdx = args.indexOf("|");
        if (pipeIdx == -1) return null;
        String path = args.substring(0, pipeIdx).trim();
        String content = args.substring(pipeIdx + 1);
        // AI 用 \n 表示换行，\\n 表示字面 \n
        content = content.replace("\\\\n", "");
        content = content.replace("\\n", "\n");
        content = content.replace("", "\\n");

        final String baseUrl = "https://view.fancy.baicaizhale.top";

        try {
            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            String json = new com.google.gson.Gson().toJson(java.util.Map.of("path", path, "content", content));
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(baseUrl + "/api/submit"))
                .header("Content-Type", "application/json")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(json))
                .timeout(java.time.Duration.ofSeconds(10))
                .build();

            java.net.http.HttpResponse<String> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                com.google.gson.JsonObject obj = com.google.gson.JsonParser.parseString(response.body()).getAsJsonObject();
                String id = obj.get("id").getAsString();
                return baseUrl + "/" + id;
            }
        } catch (Exception e) {
            if (plugin.getConfigManager().isDebug()) {
                plugin.getLogger().warning("[ViewFancy] 推送失败: " + e.getMessage());
            }
        }
        return null;
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
        } else if (type.equals("edit")) {
            return executeDiffOperation(root, pathArg);
        } else if (type.equals("write")) {
            return executeWriteOperation(root, pathArg);
        }

        return "错误: 未知操作类型";
    }

    /**
     * 执行 ls 操作
     */
    private String executeLsOperation(File root, String pathArg, String args) {
        File dir = resolvePathCaseInsensitive(root, pathArg.isEmpty() ? "." : pathArg);
        
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
     * 返回带行号的内容，方便 AI 知道每行对应的行号
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

        File file = resolvePathCaseInsensitive(root, path);
        
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
            int maxLines = 2000;
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
                    // 添加行号前缀，格式：行号: 内容
                    content.append(currentLine).append(": ").append(line).append("\n");
                    readCount++;
                }
                currentLine++;
            }
        }
        return content.toString();
    }

    /**
     * 执行 edit 操作
     * 在给定行号范围内查找旧内容，如果找到多个匹配则拒绝操作
     * 支持自动搜索模式：range 可以是 "auto" 或省略（使用 "auto"）
     */
    private String executeDiffOperation(File root, String pathArg) throws IOException {
        String[] editParts = pathArg.split("\\|", 4);
        
        // 支持3种格式：
        // 1. path|range|original|replacement (4部分，带行号)
        // 2. path|original|replacement (3部分，自动搜索)
        // 3. path|auto|original|replacement (4部分，但range是auto)
        
        String path;
        String rangeStr;
        String original;
        String replacement;
        boolean autoSearch = false;
        
        if (editParts.length < 3) {
            return "错误: #edit 至少需要3个参数，格式：#edit: path|original|replacement 或 #edit: path|range|original|replacement";
        } else if (editParts.length == 3) {
            // 3部分格式：path|original|replacement
            path = editParts[0].trim();
            rangeStr = "auto";
            original = editParts[1];
            replacement = editParts[2];
            autoSearch = true;
        } else {
            // 4部分格式
            path = editParts[0].trim();
            rangeStr = editParts[1].trim();
            original = editParts[2];
            replacement = editParts[3];
            // 如果 range 是 auto 或空，使用自动搜索
            if (rangeStr.equalsIgnoreCase("auto") || rangeStr.isEmpty()) {
                autoSearch = true;
            }
        }
        
        // 解析行号范围
        int startLine = 1;
        int endLine = -1; // -1 表示文件末尾
        
        if (!autoSearch) {
            if (rangeStr.matches("^\\d+-\\d+$")) {
                // 范围格式：10-15
                try {
                    String[] range = rangeStr.split("-");
                    startLine = Integer.parseInt(range[0]);
                    endLine = Integer.parseInt(range[1]);
                } catch (NumberFormatException ignored) {
                    return "错误: 行号范围格式不正确，正确格式：10-15、10 或 auto";
                }
            } else if (rangeStr.matches("^\\d+$")) {
                // 单行格式：10
                try {
                    startLine = Integer.parseInt(rangeStr);
                    endLine = startLine;
                } catch (NumberFormatException ignored) {
                    return "错误: 行号格式不正确，正确格式：10-15、10 或 auto";
                }
            } else {
                return "错误: 行号范围格式不正确，正确格式：10-15、10 或 auto";
            }
        }

        File file = resolvePathCaseInsensitive(root, path);
        
        if (!isWithinRoot(root, file)) {
            return "错误: 路径超出服务器目录限制";
        }
        if (!file.exists()) {
            return "错误: 文件不存在";
        }

        // 读取文件内容
        List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
        
        // 自动搜索模式下，搜索整个文件
        if (autoSearch) {
            startLine = 1;
            endLine = lines.size();
        } else {
            // 验证行号范围
            if (startLine < 1 || startLine > lines.size()) {
                return "错误: 起始行号无效 (文件总行数: " + lines.size() + ")";
            }
            if (endLine > lines.size()) {
                endLine = lines.size();
            }
            if (startLine > endLine) {
                return "错误: 起始行号不能大于结束行号";
            }
        }
        
        // 将 original 按行分割，并去掉行号前缀（如果 AI 从 #read 复制了行号）
        String[] originalLines = original.split("\n");
        int originalLineCount = originalLines.length;
        
        // 去掉每行的行号前缀（格式：数字: ）
        for (int j = 0; j < originalLines.length; j++) {
            originalLines[j] = removeLineNumberPrefix(originalLines[j]);
        }
        
        // 在给定行号范围内查找所有匹配位置
        List<Integer> matchPositions = new java.util.ArrayList<>();
        
        // 计算搜索范围：从 startLine 到 endLine - originalLineCount + 1
        int searchEndLine = endLine - originalLineCount + 1;
        if (searchEndLine < startLine) {
            searchEndLine = startLine;
        }
        
        for (int i = startLine - 1; i <= searchEndLine - 1 && i < lines.size(); i++) {
            boolean match = true;
            for (int j = 0; j < originalLineCount; j++) {
                int fileLineIndex = i + j;
                if (fileLineIndex >= lines.size()) {
                    match = false;
                    break;
                }
                // 使用包含匹配：文件行包含 AI 提供的内容即可
                if (!lines.get(fileLineIndex).contains(originalLines[j])) {
                    match = false;
                    break;
                }
            }
            if (match) {
                matchPositions.add(i); // 记录匹配的起始行索引（0-based）
            }
        }
        
        // 根据匹配结果处理
        if (matchPositions.isEmpty()) {
            // 没有找到匹配
            if (autoSearch) {
                return "错误: 在文件中未找到包含指定内容的行\n" +
                       "查找内容: " + original + "\n" +
                       "提示：请提供更简短的关键内容（如 'enabled: true' 而不是整行）";
            } else {
                // 构建实际内容用于显示
                StringBuilder rangeContent = new StringBuilder();
                for (int i = startLine - 1; i < endLine; i++) {
                    rangeContent.append(lines.get(i));
                    if (i < endLine - 1) {
                        rangeContent.append("\n");
                    }
                }
                return "错误: 在给定行号范围 " + rangeStr + " 内未找到包含指定内容的行\n" +
                       "查找内容: " + original + "\n" +
                       "行号范围内的实际内容:\n" + rangeContent.toString() + "\n" +
                       "提示：请提供更简短的关键内容（如 'enabled: true' 而不是整行）";
            }
        } else if (matchPositions.size() > 1) {
            // 找到多个匹配
            StringBuilder sb = new StringBuilder();
            if (autoSearch) {
                sb.append("错误: 在文件中找到 ").append(matchPositions.size()).append(" 处包含指定内容的行，无法确定要替换哪一处\n");
            } else {
                sb.append("错误: 在给定行号范围 ").append(rangeStr).append(" 内找到 ")
                  .append(matchPositions.size()).append(" 处包含指定内容的行，无法确定要替换哪一处\n");
            }
            sb.append("匹配位置: ");
            for (int i = 0; i < matchPositions.size(); i++) {
                if (i > 0) sb.append(", ");
                int matchStartLine = matchPositions.get(i) + 1; // 转换为 1-based
                int matchEndLine = matchStartLine + originalLineCount - 1;
                if (matchStartLine == matchEndLine) {
                    sb.append("第 ").append(matchStartLine).append(" 行");
                } else {
                    sb.append("第 ").append(matchStartLine).append("-").append(matchEndLine).append(" 行");
                }
            }
            sb.append("\n请使用更具体的行号范围（如 ").append(matchPositions.get(0) + 1).append("-")
              .append(matchPositions.get(0) + originalLineCount).append("）来唯一确定要替换的位置。");
            return sb.toString();
        }
        
        // 只有一个匹配，执行替换
        int matchStartIndex = matchPositions.get(0);
        int matchEndIndex = matchStartIndex + originalLineCount;
        
        // 替换内容
        List<String> newLines = new java.util.ArrayList<>();
        
        // 复制匹配位置之前的行
        for (int i = 0; i < matchStartIndex; i++) {
            newLines.add(lines.get(i));
        }
        
        // 插入修改后的内容（行内替换，保留行内其他部分）
        String[] replacementLines = replacement.split("\n");
        for (int j = 0; j < originalLineCount; j++) {
            String originalLine = lines.get(matchStartIndex + j);
            String newLine;

            if (j < replacementLines.length) {
                // 行内子串替换：将匹配到的内容替换为新内容，缩进和注释等其余部分自动保留
                newLine = originalLine.replace(originalLines[j], replacementLines[j]);
            } else {
                // 如果 replacement 行数少于 original 行数，保留原始行
                newLine = originalLine;
            }
            newLines.add(newLine);
        }
        
        // 如果 replacement 行数多于 original 行数，添加剩余的行
        for (int j = originalLineCount; j < replacementLines.length; j++) {
            newLines.add(replacementLines[j]);
        }
        
        // 复制匹配位置之后的行
        for (int i = matchEndIndex; i < lines.size(); i++) {
            newLines.add(lines.get(i));
        }
        
        // 写入文件
        Files.write(file.toPath(), newLines, StandardCharsets.UTF_8);
        
        // 返回修改前后的对比
        int actualStartLine = matchStartIndex + 1;
        int actualEndLine = matchEndIndex;
        StringBuilder result = new StringBuilder();
        result.append("成功修改文件: ").append(path).append("\n");
        result.append("行号范围: ").append(actualStartLine).append("-").append(actualEndLine).append("\n");
        result.append("修改前:\n").append(original).append("\n");
        result.append("修改后:\n").append(replacement);
        
        return result.toString();
    }

    /**
     * 执行 write 操作 — 完全覆写文件内容
     * 格式：#write: <path>|<content>
     * 对于已存在的文件，必须先 #read 才能 #write
     */
    private String executeWriteOperation(File root, String pathArg) throws IOException {
        int pipeIndex = pathArg.indexOf("|");
        if (pipeIndex == -1) {
            return "错误: #write 格式不正确，正确格式：#write: path|content";
        }

        String path = pathArg.substring(0, pipeIndex).trim();
        String content = pathArg.substring(pipeIndex + 1);

        if (path.isEmpty()) {
            return "错误: 文件路径不能为空";
        }

        // 处理路径前缀
        if (path.startsWith("/") || path.startsWith("\\")) {
            path = path.substring(1);
        }

        File file = resolvePathCaseInsensitive(root, path);

        if (!isWithinRoot(root, file)) {
            return "错误: 路径超出服务器目录限制";
        }

        // 确保父目录存在
        File parentDir = file.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }

        // \\n → 字面 \n, \n → 真实换行
        content = content.replace("\\\\n", "");
        content = content.replace("\\n", "\n");
        content = content.replace("", "\\n");
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));

        return "成功写入文件: " + path + " (" + content.length() + " 字符)";
    }

    /**
     * 尝试不区分大小写解析路径
     * 先尝试精确路径（兼容 Windows 和 Linux 精确输入），
     * 不命中时逐层扫描目录进行大小写不敏感匹配
     */
    private File resolvePathCaseInsensitive(File root, String path) {
        File exact = new File(root, path);
        if (exact.exists()) {
            return exact;
        }

        String[] components = path.replace("\\", "/").split("/");
        File current = root;

        for (int i = 0; i < components.length; i++) {
            String component = components[i];
            if (component.isEmpty()) continue;

            File[] children = current.listFiles();
            if (children == null) break;

            File match = null;
            for (File child : children) {
                if (child.getName().equalsIgnoreCase(component)) {
                    match = child;
                    break;
                }
            }

            if (match != null) {
                current = match;
            } else {
                StringBuilder remaining = new StringBuilder(component);
                for (int j = i + 1; j < components.length; j++) {
                    remaining.append("/").append(components[j]);
                }
                return new File(current, remaining.toString());
            }
        }

        return current;
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
        } else if (type.equals("edit")) {
            player.sendMessage(ChatColor.GRAY + "〇 已成功修改文件。");
            if (result.contains("修改前:\n") && result.contains("修改后:\n")) {
                String[] parts = result.split("修改前:\n|修改后:\n");
                if (parts.length >= 3) {
                    String[] beforeLines = parts[1].split("\n");
                    String[] afterLines = parts[2].split("\n");
                    player.sendMessage(ChatColor.GRAY + "─────────────────────────────────");
                    player.sendMessage(ChatColor.GRAY + "修改前:");
                    for (String line : beforeLines) {
                        player.sendMessage(ChatColor.GRAY + "  " + line);
                    }
                    player.sendMessage(ChatColor.GRAY + "─────────────────────────────────");
                    player.sendMessage(ChatColor.GRAY + "修改后:");
                    for (String line : afterLines) {
                        player.sendMessage(ChatColor.GRAY + "  " + line);
                    }
                    player.sendMessage(ChatColor.GRAY + "─────────────────────────────────");
                }
            }
        } else if (type.equals("write")) {
            // 写入完成后静默，无需额外消息
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
            return ColorUtil.legacyToReadable(proxyOutput);
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
            output.append(msg);
            if (methodName.equals("sendActionBar")) {
                player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                    new net.md_5.bungee.api.chat.TextComponent(msg));
            } else {
                player.sendMessage(msg);
            }
        } else if (args[0] instanceof String[]) {
            for (String msg : (String[]) args[0]) {
                if (output.length() > 0) output.append("\n");
                output.append(msg);
                player.sendMessage(msg);
            }
        } else if (args.length > 1 && args[0] instanceof java.util.UUID && args[1] instanceof String) {
            String msg = (String) args[1];
            if (output.length() > 0) output.append("\n");
            output.append(msg);
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
                // 使用 LegacyComponentSerializer 保留颜色信息（输出 § 码）
                Class<?> legacySerializerClass = Class.forName("net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer");
                java.lang.reflect.Method legacySection = legacySerializerClass.getMethod("legacySection");
                Object legacySerializer = legacySection.invoke(null);
                java.lang.reflect.Method serializeMethod = legacySerializer.getClass().getMethod("serialize", componentClass);
                String extracted = (String) serializeMethod.invoke(legacySerializer, component);

                if (extracted != null && !extracted.isEmpty()) {
                    if (output.length() > 0) output.append("\n");
                    output.append(extracted);

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
                output.append(legacyText);
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
     * 处理 #skill 工具
     */
    private void handleSkillTool(Player player, String args, DialogueSession session) {
        String skillId = args.trim().toLowerCase();
        
        if (skillId.isEmpty()) {
            cliManager.feedbackToAI(player, "#skill_result: 错误 - 请提供 Skill ID");
            return;
        }
        
        cliManager.setGenerating(player.getUniqueId(), false, CLIManager.GenerationStatus.EXECUTING_TOOL);
        
        org.YanPl.model.Skill skill = plugin.getSkillManager().getSkill(skillId);
        
        if (skill == null) {
            // 尝试搜索
            List<org.YanPl.model.Skill> matches = plugin.getSkillManager().searchSkills(skillId);
            if (matches.isEmpty()) {
                cliManager.feedbackToAI(player, "#skill_result: 错误 - 未找到 Skill: " + skillId);
                return;
            }
            skill = matches.get(0);
        }
        
        // 记录玩家已加载此 Skill
        plugin.getSkillManager().loadSkillForPlayer(player, skill.getId());

        // 将 Skill 内容加入对话上下文
        boolean added = false;
        if (session != null) {
            added = session.addSkillContext(skill);
        }
        
        // 反馈给 AI（支持模板变量）
        String suffix = added ? "" : " (已存在)";
        Map<String, String> templateContext = new HashMap<>();
        templateContext.put("player", player.getName());
        String result = "#skill_result: 已加载 Skill [" + skill.getMetadata().getName() + "]" + suffix + "\n\n"
                + skill.getFormattedProcessedContent(templateContext);
        cliManager.feedbackToAI(player, result);
    }

    /**
     * 处理 #unloadskill 工具
     */
    private void handleUnloadSkillTool(Player player, String args, DialogueSession session) {
        String skillId = args.trim().toLowerCase();

        if (skillId.isEmpty()) {
            cliManager.feedbackToAI(player, "#unloadskill_result: 错误 - 请提供 Skill ID");
            return;
        }

        cliManager.setGenerating(player.getUniqueId(), false, CLIManager.GenerationStatus.EXECUTING_TOOL);

        // 检查 Skill 是否存在
        org.YanPl.model.Skill skill = plugin.getSkillManager().getSkill(skillId);
        if (skill == null) {
            cliManager.feedbackToAI(player, "#unloadskill_result: 错误 - 未找到 Skill: " + skillId);
            return;
        }

        // 从对话历史移除
        boolean removedFromSession = false;
        if (session != null) {
            removedFromSession = session.removeSkillContext(skillId);
        }

        // 从玩家记录移除
        boolean removedFromPlayer = plugin.getSkillManager().unloadSkillForPlayer(player, skillId);

        // 反馈给 AI
        if (removedFromSession || removedFromPlayer) {
            cliManager.feedbackToAI(player, "#unloadskill_result: 已卸载 Skill [" + skill.getMetadata().getName() + "]");
        } else {
            cliManager.feedbackToAI(player, "#unloadskill_result: Skill [" + skill.getMetadata().getName() + "] 未在加载列表中");
        }
    }

    /**
     * 参考 Claude 的 AskUserQuestion 工具结构（单问题版本）
     */
    private static class AskUserQuestionRequest {
        @SerializedName("question")
        String question;
        
        @SerializedName("header")
        String header;
        
        @SerializedName("options")
        List<AskOption> options;
        
        @SerializedName("otherLabel")
        String otherLabel;
    }

    /**
     * 选项数据类
     */
    private static class AskOption {
        @SerializedName("label")
        String label;
        
        @SerializedName("description")
        String description;
    }

    private static final Gson gson = new Gson();

    /**
     * 处理 #ask 工具（仅支持 JSON 格式）
     */
    private void handleAskTool(Player player, String input) {
        try {
            AskUserQuestionRequest request = gson.fromJson(input, AskUserQuestionRequest.class);
            if (request.question != null && !request.question.isEmpty()) {
                handleJsonAskTool(player, request);
            } else {
                player.sendMessage(ChatColor.RED + "JSON 格式错误：缺少 question 字段");
            }
        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "JSON 解析失败: " + e.getMessage());
        }
    }

    /**
     * 处理 JSON 格式的 AskUserQuestion 工具
     */
    private void handleJsonAskTool(Player player, AskUserQuestionRequest request) {
        // 显示分隔线（顶部）
        player.sendMessage(ChatColor.DARK_GRAY + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        // 显示问题标题（粗体）
        if (request.question != null) {
            player.sendMessage(ChatColor.WHITE + "" + ChatColor.BOLD + request.question);
        }

        // 显示 header（芯片/标签样式）
        if (request.header != null && !request.header.isEmpty()) {
            player.sendMessage(ChatColor.DARK_GRAY + "[" + request.header + "]");
        }

        player.sendMessage("");

        // 显示选项（最多4个）
        int optionNum = 1;
        if (request.options != null) {
            for (AskOption opt : request.options) {
                if (optionNum > 4) break;

                // 构建选项行
                TextComponent optionLine = new TextComponent();
                TextComponent numberPart = new TextComponent(ChatColor.GRAY + "  " + optionNum + ". ");
                optionLine.addExtra(numberPart);

                TextComponent labelBtn = new TextComponent(ChatColor.WHITE + opt.label);
                labelBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cli select " + opt.label));
                optionLine.addExtra(labelBtn);

                player.spigot().sendMessage(optionLine);

                // 显示描述（灰色缩进）
                if (opt.description != null && !opt.description.isEmpty()) {
                    TextComponent descLine = new TextComponent(ChatColor.GRAY + "      " + opt.description);
                    descLine.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cli select " + opt.label));
                    player.spigot().sendMessage(descLine);
                }

                optionNum++;
            }
        }

        // 显示 Other 选项（如果 AI 指定了 otherLabel）
        if (request.otherLabel != null && !request.otherLabel.isEmpty()) {
            player.sendMessage("");
            int totalOptions = request.options != null ? request.options.size() : 0;
            TextComponent otherLine = new TextComponent();
            TextComponent otherNum = new TextComponent(ChatColor.GRAY + "  " + (totalOptions + 1) + ". ");
            otherLine.addExtra(otherNum);
            TextComponent otherBtn = new TextComponent(ChatColor.WHITE + request.otherLabel);
            otherBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cli other"));
            otherLine.addExtra(otherBtn);
            player.spigot().sendMessage(otherLine);
        }

        // 显示分隔线（底部）
        player.sendMessage(ChatColor.DARK_GRAY + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        cliManager.setPendingCommand(player.getUniqueId(), "CHOOSING");
        cliManager.setGenerating(player.getUniqueId(), false, CLIManager.GenerationStatus.WAITING_CHOICE);
    }

    /**
     * 处理 #search 工具
     */
    private void handleSearchTool(Player player, String query) {
        player.sendMessage(ChatColor.GRAY + "⨁ Searching: " + query);
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
        return "搜索服务不可用，请在配置文件中启用 Metaso API 或 Tavily API。";
    }

    /**
     * 执行 Wiki 搜索
     */
    private String performWikiSearch(String query, Player player) {
        String result = fetchWikiResult(query);
        if (result.equals("未找到相关 Wiki 条目。")) {
            if (plugin.getMetasoAPI().isAvailable()) {
                return plugin.getMetasoAPI().search(query);
            } else if (plugin.getConfigManager().isTavilyEnabled()) {
                return plugin.getTavilyAPI().search(query);
            }
            return "搜索服务不可用，请在配置文件中启用 Metaso API 或 Tavily API。";
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
     * 处理 #webfetch 工具 - 读取网页内容
     * 格式: #webfetch: https://example.com
     */
    private void handleWebFetchTool(Player player, String args, DialogueSession session) {
        UUID uuid = player.getUniqueId();
        cliManager.setGenerating(uuid, false, CLIManager.GenerationStatus.EXECUTING_TOOL);

        if (args == null || args.trim().isEmpty()) {
            player.sendMessage(ChatColor.RED + "错误: #webfetch 工具需要提供URL参数");
            cliManager.feedbackToAI(player, "#webfetch_result: error - 需要提供URL参数，例如 #webfetch: https://example.com");
            return;
        }

        // 清理URL，去除可能的Markdown格式和其他无关字符
        String url = args.trim();
        
        // 去除反引号
        url = url.replaceAll("`", "");
        
        // 去除可能的括号
        url = url.replaceAll("^\\(", "");
        url = url.replaceAll("\\)$", "");
        
        // 去除引号
        url = url.replaceAll("^['\"](.*)['\"]$", "$1");
        
        // 再次修剪空格
        url = url.trim();

        // 直接执行网页阅读，不需要验证
        executeWebFetch(player, url);
    }

    /**
     * 执行网页阅读操作
     */
    private void executeWebFetch(Player player, String url) {
        // 显示工具调用信息
        player.sendMessage(ChatColor.GRAY + ">> " + ChatColor.WHITE + "WebFetch " + url);
        
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String result = fetchWebPage(url);
                final String finalResult = result;
                if (!plugin.isEnabled()) return;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    cliManager.feedbackToAI(player, "#webfetch_result: " + finalResult);
                });
            } catch (Exception e) {
                plugin.getCloudErrorReport().report(e);
                if (!plugin.isEnabled()) return;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    String errorMessage = "#webfetch_result: 错误 - " + e.getMessage();
                    cliManager.feedbackToAI(player, errorMessage);
                    player.sendMessage(ChatColor.RED + "读取网页失败: " + e.getMessage());
                });
            }
        });
    }

    /**
     * 获取网页内容并解析
     * 优先使用 r.jina.ai 代理获取纯文本内容
     * 如果失败则回退到直接获取并清理HTML
     */
    protected String fetchWebPage(String url) throws Exception {
        // 验证URL格式
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw new IllegalArgumentException("URL必须以http://或https://开头");
        }

        // 首先尝试使用 r.jina.ai 代理
        try {
            String jinaUrl = "https://r.jina.ai/" + url;
            java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(15))
                    .followRedirects(java.net.http.HttpClient.Redirect.NORMAL)
                    .build();

            java.net.http.HttpRequest jinaRequest = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(jinaUrl))
                    .timeout(java.time.Duration.ofSeconds(20))
                    .header("Accept", "text/plain, text/html, */*")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .GET()
                    .build();

            java.net.http.HttpResponse<String> jinaResponse = httpClient.send(jinaRequest, java.net.http.HttpResponse.BodyHandlers.ofString());
            if (jinaResponse.statusCode() == 200) {
                String jinaText = jinaResponse.body().trim();
                if (jinaText.length() > 8000) {
                    jinaText = jinaText.substring(0, 8000) + "\n... (内容已截断)";
                }
                return jinaText;
            }
        } catch (Exception e) {
            if (plugin.getConfigManager().isDebug()) {
                plugin.getLogger().info("[WebFetch] Jina.ai 请求失败，回退到直接获取: " + e.getMessage());
            }
        }

        // 回退到原有的直接获取逻辑
        java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(30))
                .followRedirects(java.net.http.HttpClient.Redirect.NORMAL)
                .build();

        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(url))
                .timeout(java.time.Duration.ofSeconds(45))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .header("Accept-Encoding", "gzip, deflate")
                .header("Sec-Ch-Ua", "\"Google Chrome\";v=\"135\", \"Not:A-Brand\";v=\"99\", \"Chromium\";v=\"135\"")
                .header("Sec-Ch-Ua-Mobile", "?0")
                .header("Sec-Ch-Ua-Platform", "\"Windows\"")
                .header("Upgrade-Insecure-Requests", "1")
                .header("Sec-Fetch-Dest", "document")
                .header("Sec-Fetch-Mode", "navigate")
                .header("Sec-Fetch-Site", "none")
                .header("Sec-Fetch-User", "?1")
                .header("Cache-Control", "max-age=0")
                .header("Referer", "https://www.google.com/")
                .header("DNT", "1")
                .GET()
                .build();

        java.net.http.HttpResponse<byte[]> response = httpClient.send(request, java.net.http.HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new Exception("HTTP请求失败，状态码: " + response.statusCode());
        }

        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        byte[] bodyBytes = response.body();
        String contentType = response.headers().firstValue("Content-Type").orElse("text/html");
        String contentEncoding = response.headers().firstValue("Content-Encoding").orElse("identity");

        byte[] decompressedBytes = bodyBytes;
        try {
            if (contentEncoding.contains("gzip")) {
                java.io.ByteArrayInputStream bis = new java.io.ByteArrayInputStream(bodyBytes);
                java.util.zip.GZIPInputStream gis = new java.util.zip.GZIPInputStream(bis);
                java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int len;
                while ((len = gis.read(buffer)) > 0) {
                    bos.write(buffer, 0, len);
                }
                gis.close();
                bos.close();
                decompressedBytes = bos.toByteArray();
            } else if (contentEncoding.contains("deflate")) {
                java.io.ByteArrayInputStream bis = new java.io.ByteArrayInputStream(bodyBytes);
                java.util.zip.InflaterInputStream iis = new java.util.zip.InflaterInputStream(bis);
                java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int len;
                while ((len = iis.read(buffer)) > 0) {
                    bos.write(buffer, 0, len);
                }
                iis.close();
                bos.close();
                decompressedBytes = bos.toByteArray();
            }
        } catch (Exception e) {
            decompressedBytes = bodyBytes;
        }

        String charset = "UTF-8";
        if (contentType.contains("charset=")) {
            int charsetIndex = contentType.indexOf("charset=");
            charset = contentType.substring(charsetIndex + 8).trim();
            if (charset.startsWith("\"")) {
                charset = charset.substring(1, charset.length() - 1);
            }
        }

        String htmlContent;
        try {
            htmlContent = new String(decompressedBytes, charset);
        } catch (java.io.UnsupportedEncodingException e) {
            htmlContent = new String(decompressedBytes, java.nio.charset.StandardCharsets.UTF_8);
        }

        org.jsoup.nodes.Document doc = org.jsoup.Jsoup.parse(htmlContent);
        String title = doc.title();
        doc.select("script, style").remove();
        String bodyText = doc.body().text();

        final int MAX_CONTENT_LENGTH = 5000;
        if (bodyText.length() > MAX_CONTENT_LENGTH) {
            bodyText = bodyText.substring(0, MAX_CONTENT_LENGTH) + "... (内容过长，已截断)";
        }

        StringBuilder result = new StringBuilder();
        result.append("网页标题: ").append(title).append("\n");
        result.append("网页URL: ").append(url).append("\n");
        result.append("\n正文内容:\n");
        result.append(bodyText);

        return result.toString();
    }

    /**
     * 将内部类型映射到配置中的工具名称
     * @param type 内部类型（ls, read, edit, diff, write）
     * @return 配置中的工具名称（ls, read, edit, write）
     */
    private String mapTypeToToolName(String type) {
        return switch (type.toLowerCase()) {
            case "ls", "read" -> "read";
            case "edit", "diff", "write" -> "write";
            default -> type;
        };
    }

    /**
     * 去掉行号前缀（格式：数字: ）
     * @param line 带行号的行，如 "94: enabled: true"
     * @return 去掉行号后的行，如 "enabled: true"
     */
    private String removeLineNumberPrefix(String line) {
        // 查找第一个冒号
        int colonIndex = line.indexOf(':');
        if (colonIndex != -1) {
            // 检查冒号前是否都是数字（行号）
            boolean isLineNumber = true;
            for (int i = 0; i < colonIndex; i++) {
                char c = line.charAt(i);
                if (!Character.isDigit(c)) {
                    isLineNumber = false;
                    break;
                }
            }
            // 如果是行号格式，去掉行号和冒号
            if (isLineNumber) {
                String remaining = line.substring(colonIndex + 1);
                // 去掉冒号后的空格
                return remaining.trim();
            }
        }
        return line;
    }

    /**
     * 检查工具是否在 Plan Mode 白名单中
     */
    private boolean isPlanModeTool(String toolName) {
        String lower = toolName.toLowerCase().trim();
        return switch (lower) {
            case "#start", "#search", "#skill", "#unloadskill", "#webfetch",
                 "#list", "#read", "#todo", "#ask", "#end", "#exit",
                 "#mcp_tools" -> true;
            default -> false;
        };
    }

    /**

    /**
     * 处理 #mcp_tools — 列出所有 MCP 外部工具及其状态
     */
    private void handleMcpToolsList(Player player) {
        cliManager.setGenerating(player.getUniqueId(), false, CLIManager.GenerationStatus.EXECUTING_TOOL);

        if (plugin.getMcpManager() == null || !plugin.getMcpManager().isEnabled()) {
            cliManager.feedbackToAI(player, "#mcp_tools_result: MCP Client 未启用。请在 config.yml 中配置并启用 mcp.client。");
            return;
        }

        List<McpClientManager.ExternalToolInfo> allTools = plugin.getMcpManager().getAllToolsWithState();

        StringBuilder sb = new StringBuilder("[MCP Tools Status]\n\n");
        if (allTools.isEmpty()) {
            sb.append("没有配置的 MCP 服务器。\n");
        } else {
            Map<String, List<McpClientManager.ExternalToolInfo>> grouped = new java.util.LinkedHashMap<>();
            for (McpClientManager.ExternalToolInfo info : allTools) {
                grouped.computeIfAbsent(info.serverName, k -> new java.util.ArrayList<>()).add(info);
            }

            for (Map.Entry<String, List<McpClientManager.ExternalToolInfo>> entry : grouped.entrySet()) {
                String serverName = entry.getKey();
                List<McpClientManager.ExternalToolInfo> tools = entry.getValue();
                McpClientManager.ExternalToolInfo first = tools.get(0);

                if (!first.serverConnected) {
                    sb.append(serverName).append(" (未连接)\n");
                    continue;
                }
                sb.append(serverName).append(" (已连接):\n");

                for (McpClientManager.ExternalToolInfo info : tools) {
                    if (info.tool == null) continue;
                    String icon = info.enabled ? "☑" : "☐";
                    String desc = info.tool.description != null && !info.tool.description.isEmpty()
                        ? " - " + info.tool.description : "";
                    String disabled = info.enabled ? "" : " (已禁用)";
                    sb.append("  ").append(icon).append(" ").append(info.tool.name).append(desc).append(disabled).append("\n");
                }
                sb.append("\n");
            }
        }
        sb.append("Format: #mcp: serverName.toolName|{\"arg1\":\"value1\"}");

        cliManager.feedbackToAI(player, "#mcp_tools_result: " + sb.toString());
    }

    /**
     * 处理 #mcp — 调用外部 MCP 工具
     * 格式: #mcp: serverName.toolName|{"arg1":"value1"}
     */
    private boolean handleMcpTool(Player player, String args, DialogueSession session) {
        if (args == null || args.isEmpty()) {
            cliManager.feedbackToAI(player, "#mcp_error: 需要指定工具名和参数，格式: #mcp: serverName.toolName|{\"arg1\":\"value1\"}");
            return false;
        }

        if (plugin.getMcpManager() == null || !plugin.getMcpManager().isEnabled()) {
            cliManager.feedbackToAI(player, "#mcp_error: MCP Client 未启用");
            return false;
        }

        // 解析 serverName.toolName|jsonArgs
        String toolPart;
        String jsonArgs = "{}";
        int pipeIdx = args.indexOf('|');
        if (pipeIdx > 0) {
            toolPart = args.substring(0, pipeIdx).trim();
            jsonArgs = args.substring(pipeIdx + 1).trim();
        } else {
            toolPart = args.trim();
        }

        int dotIdx = toolPart.indexOf('.');
        if (dotIdx <= 0) {
            cliManager.feedbackToAI(player, "#mcp_error: 工具名格式错误，应为 serverName.toolName");
            return false;
        }

        String serverName = toolPart.substring(0, dotIdx).trim();
        String toolName = toolPart.substring(dotIdx + 1).trim();

        player.sendMessage(ChatColor.GRAY + "⨁ MCP: " + serverName + "." + toolName);
        cliManager.setGenerating(player.getUniqueId(), false, CLIManager.GenerationStatus.EXECUTING_TOOL);

        // 解析 JSON 参数
        com.google.gson.JsonObject arguments;
        try {
            arguments = gson.fromJson(jsonArgs, com.google.gson.JsonObject.class);
            if (arguments == null) arguments = new com.google.gson.JsonObject();
        } catch (Exception e) {
            arguments = new com.google.gson.JsonObject();
        }

        // 检查是否被禁用
        if (!plugin.getMcpManager().isToolEnabled(serverName, toolName)) {
            cliManager.feedbackToAI(player, "#mcp_error: 工具 " + serverName + "." + toolName
                    + " 已被管理员禁用。可用 #mcp_tools 查看所有可用 MCP 工具。");
            return false;
        }

        final String fServerName = serverName;
        final String fToolName = toolName;
        final com.google.gson.JsonObject fArguments = arguments;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            McpTypes.McpToolCallResult result = plugin.getMcpManager().callExternalTool(fServerName, fToolName, fArguments);

            if (!plugin.isEnabled()) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (result == null) {
                    cliManager.feedbackToAI(player, "#mcp_result: MCP 服务器无响应");
                } else if (result.isError) {
                    String errText = result.content != null && !result.content.isEmpty()
                            && result.content.get(0).text != null
                        ? result.content.get(0).text : "未知错误";
                    cliManager.feedbackToAI(player, "#mcp_error: " + errText);
                } else {
                    String text = result.content != null && !result.content.isEmpty()
                            && result.content.get(0).text != null
                        ? result.content.get(0).text : "(空结果)";
                    cliManager.feedbackToAI(player, "#mcp_result: " + text);
                }
            });
        });
        return true;
    }

    /**
     * 处理 #start 工具 — 结束 Plan Mode，显示执行模式选择
     */
    private void handleStartTool(Player player) {
        cliManager.setGenerating(player.getUniqueId(), false, CLIManager.GenerationStatus.WAITING_CHOICE);
        cliManager.handlePlanStart(player);
    }
}
