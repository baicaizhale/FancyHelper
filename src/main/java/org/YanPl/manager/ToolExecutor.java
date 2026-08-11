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
import org.YanPl.util.I18n;
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

    // 命令输出捕获的静默窗口：有输出后持续无新输出达该时长即收尾（捕获缓冲长度增长判定）
    private static final long CAPTURE_QUIET_MS = 2000L;
    // 命令从未产生任何输出时的首次输出宽限（有输出的命令不受此限，安静即可收尾）
    private static final long CAPTURE_NO_OUTPUT_MS = 5000L;
    // 命令输出的绝对等待上限（防止命令持续吐输出时无限等待）
    private static final long CAPTURE_MAX_WAIT_MS = 15_000L;
    // 静默窗口轮询间隔
    private static final long CAPTURE_POLL_TICKS = 10L; // 0.5 秒

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
     * @param force 是否跳过命令存在性校验（原生 run 调用的 force 键）
     * @return 是否成功执行
     */
    public boolean executeTool(Player player, String toolCall, DialogueSession session, boolean force) {
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
                success = handleRunTool(player, args, session, force);
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

            // 服务器级记忆工具（仅管理员，影响所有玩家会话）
            case "#remember_global":
                handleRememberGlobalTool(player, args);
                break;
            case "#forget_global":
                handleForgetGlobalTool(player, args);
                break;
            case "#edit_global":
                handleEditGlobalTool(player, args);
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
                player.sendMessage(I18n.t("tool.unknown", toolName));
                String error = "#error: 未知工具 " + toolName + "。请仅使用系统提示中定义的工具。";
                cliManager.feedbackToAI(player, error);
                if (session != null) {
                    session.setLastError(error);
                }
                success = false;
                break;
        }

        // 记录工具调用统计
        if (success) {
            plugin.getStatsManager().incrementToolSuccess();
        } else {
            plugin.getStatsManager().incrementToolFailure();
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
    public static ToolParseResult parseToolCall(String toolCall) {
        String toolName;
        String args = "";
        if (toolCall == null) {
            return new ToolParseResult("", "");
        }

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
            TextComponent message = new TextComponent(TextComponent.fromLegacyText(I18n.t("tool.memory.remembering")));
            TextComponent manageBtn = new TextComponent(TextComponent.fromLegacyText(I18n.t("tool.memory.manage")));
            manageBtn.setColor(net.md_5.bungee.api.ChatColor.of(ColorUtil.getColorZ()));
            manageBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cli memory"));
            manageBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(I18n.t("tool.memory.manage.hover"))));
            message.addExtra(manageBtn);
            player.spigot().sendMessage(message);
        } else if (lowerToolName.equals("#remember_global") || lowerToolName.equals("#forget_global") ||
            lowerToolName.equals("#edit_global")) {
            TextComponent message = new TextComponent(TextComponent.fromLegacyText(I18n.t("tool.memory.server.remembering")));
            TextComponent manageBtn = new TextComponent(TextComponent.fromLegacyText(I18n.t("tool.memory.server.manage")));
            manageBtn.setColor(net.md_5.bungee.api.ChatColor.of(ColorUtil.getColorZ()));
            manageBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cli servermemory"));
            manageBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(I18n.t("tool.memory.server.manage.hover"))));
            message.addExtra(manageBtn);
            player.spigot().sendMessage(message);
        } else if (lowerToolName.equals("#exit")) {
            player.sendMessage(I18n.t("tool.exit"));
        } else if (lowerToolName.equals("#skill")) {
            String skillId = args.trim().toLowerCase();
            org.YanPl.model.Skill skill = plugin.getSkillManager().getSkill(skillId);
            String skillName = skill != null ? skill.getDisplayName() : args;
            player.sendMessage(I18n.t("tool.skill.run", skillName));
        }
    }

    /**
     * 处理 #run 工具。
     *
     * <p>命令拦截共三层防线，force 只跳第一层，玩家确认始终保留：
     * <ol>
     *   <li><b>命令存在性校验</b>（{@link #checkCommandExists}）：首词不在命令表但斜杠变体命中 → 疑似斜杠写错，拦截并教育。
     *       <code>force=true</code> 跳过本层（模型确信命令真实存在，如命令表未索引的懒注册命令，避免被技术性误拦）。</li>
     *   <li><b>SMART 风险评估</b>：评分超阈值弹确认按钮。force 不生效。</li>
     *   <li><b>YOLO 风险词检查</b>（{@link #isRiskyCommand}）：op/ban 等风险词要求确认，递归检查 execute 子命令。force 不生效。</li>
     * </ol>
     * NORMAL/PLAN 模式所有命令一律弹确认按钮，force 同样不生效。
     *
     * <p>force 来源：原生 run 调用 JSON 键 {@code {"command":"...","force":true}}，
     * 经 {@link org.YanPl.manager.ToolRegistry#isForceCall} 提取，被拦截后模型按
     * {@link #handleBlockedCommand} 反馈文案二次尝试时带上；刻意不进 run 的 schema，见 ToolRegistry 处注释。
     */
    private boolean handleRunTool(Player player, String command, DialogueSession session, boolean force) {
        if (command.isEmpty()) {
            player.sendMessage(I18n.t("tool.run.need.args"));
            String error = "#error: #run 工具需要提供命令参数，例如 #run: say hello";
            cliManager.feedbackToAI(player, error);
            if (session != null) {
                session.setLastError(error);
            }
            return false;
        }

        UUID uuid = player.getUniqueId();
        // 注释化：不再删除前导 /，命令保留原样执行（服务器端 dispatchCommand 会自行处理 /）
        String cleanCommand = command;
        // String cleanCommand = command.startsWith("/") ? command.substring(1) : command;

        // 命令存在性校验：首词与命令表比对，疑似斜杠错误时拦截教育模型。
        // force=true（原生 run 调用 JSON 键）跳过此校验。
        if (!force) {
            CommandCheckResult check = checkCommandExists(cleanCommand);
            if (check.blocked) {
                return handleBlockedCommand(player, session, cleanCommand, check);
            }
        }

        // SMART 模式下评估风险
        if (session != null && session.getMode() == DialogueSession.Mode.SMART) {
            player.sendMessage(I18n.t("tool.run.assessing"));
            cliManager.setGenerating(uuid, false, CLIManager.GenerationStatus.THINKING);
            
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                RiskAssessmentManager.RiskAssessment assessment = 
                    riskAssessmentManager.assessRisk("run", cleanCommand);
                
                Bukkit.getScheduler().runTask(plugin, () -> {
                    int threshold = plugin.getConfigManager().getSmartRiskThreshold();
                    if (assessment.level >= threshold) {
                        cliManager.sendSmartRiskConfirm(player, "run", cleanCommand, assessment);
                    } else {
                        player.sendMessage(I18n.t("tool.run.smart", cleanCommand));
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
                player.sendMessage(I18n.t("tool.run.risky"));
                cliManager.setPendingCommand(uuid, cleanCommand);
                cliManager.setGenerating(uuid, false, CLIManager.GenerationStatus.WAITING_CONFIRM);
                sendConfirmButtons(player, cleanCommand);
                return true;
            } else {
                player.sendMessage(I18n.t("tool.run.yolo", cleanCommand));
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
     * 命令存在性校验结果。
     * @param blocked    是否拦截（首词不在命令表，但存在斜杠变体，疑似斜杠错误）
     * @param suggestion 建议的正确命令（如 "give"），blocked 为 true 时有效
     */
    record CommandCheckResult(boolean blocked, String suggestion) {
    }

    /**
     * 命令存在性校验：提取 #run 参数首词，与命令表完全比对。
     * <p>
     * 首词不命中时尝试斜杠变体（0/1/2 个前导斜杠）：
     * <ul>
     *   <li>存在变体命中 → 疑似斜杠错误，拦截并给出建议命令</li>
     *   <li>无变体命中或命令表为空 → 放行（懒注册/未知命令不误杀）</li>
     * </ul>
     * 校验只查命令表，绝不执行命令。
     */
    CommandCheckResult checkCommandExists(String command) {
        List<String> indexed = plugin.getWorkspaceIndexer().getIndexedCommands();
        return checkCommand(command, indexed);
    }

    /**
     * 命令存在性校验纯逻辑：提取首词，与命令表完全比对。
     * <p>
     * 首词不命中时尝试斜杠变体（0/1/2 个前导斜杠）：
     * <ul>
     *   <li>存在变体命中 → 疑似斜杠错误，拦截并给出建议命令</li>
     *   <li>无变体命中或命令表为空 → 放行（懒注册/未知命令不误杀）</li>
     * </ul>
     * 校验只查命令表，绝不执行命令。
     *
     * @param command         待校验命令（含参数）
     * @param indexedCommands 命令表；null 或空视为无法校验
     */
    static CommandCheckResult checkCommand(String command, List<String> indexedCommands) {
        String trimmed = command == null ? "" : command.trim();
        if (trimmed.isEmpty()) {
            return new CommandCheckResult(false, null);
        }
        if (indexedCommands == null || indexedCommands.isEmpty()) {
            // 命令表为空（未索引/懒注册），无法校验，放行避免误杀
            return new CommandCheckResult(false, null);
        }
        java.util.Set<String> table = new java.util.HashSet<>(indexedCommands);

        // 提取首词：空格或制表符分割的第一个 token
        int sp = trimmed.indexOf(' ');
        int tab = trimmed.indexOf('\t');
        int cut = (sp == -1) ? tab : (tab == -1 ? sp : Math.min(sp, tab));
        String firstToken = (cut == -1) ? trimmed : trimmed.substring(0, cut);

        if (firstToken.isEmpty()) {
            return new CommandCheckResult(false, null);
        }
        if (table.contains(firstToken)) {
            return new CommandCheckResult(false, null);
        }

        // 尝试斜杠变体：base（去掉全部前导 /）、/base、//base
        String base = firstToken.replaceAll("^/+", "");
        if (base.isEmpty()) {
            return new CommandCheckResult(false, null);
        }
        String[] variants = { base, "/" + base, "//" + base };
        for (String v : variants) {
            if (!v.equals(firstToken) && table.contains(v)) {
                return new CommandCheckResult(true, v);
            }
        }
        return new CommandCheckResult(false, null);
    }

    /**
     * 拦截疑似斜杠错误的命令：不展示给玩家，feedback 教育模型 + 警告进上下文。
     */
    private boolean handleBlockedCommand(Player player, DialogueSession session, String command, CommandCheckResult check) {
        String suggestion = check.suggestion() == null ? "" : check.suggestion();
        String warning = "Warning: \"" + command + "\" was not executed. Unrecognized command. "
                + (suggestion.isEmpty() ? "" : "Did you mean \"" + suggestion + "\"? ")
                + "If you are certain, set \"force\": true in your run call, e.g. "
                + "{\"command\": \"/give @p tnt\", \"force\": true}.";
        plugin.getLogger().warning("[CLI] 命令存在性校验拦截 " + player.getName() + ": " + command
                + (suggestion.isEmpty() ? "" : " → 建议 " + suggestion));
        if (session != null) {
            session.setLastError(warning);
        }
        cliManager.feedbackToAI(player, warning);
        return false;
    }

    /**
     * 检查是否为风险命令
     */
    private boolean isRiskyCommand(String cmd) {
        return isRiskyCommand(cmd, plugin.getConfigManager().getYoloRiskCommands());
    }

    /**
     * 检查是否为风险命令（静态版，供 CLIManager 批量预筛使用）。
     */
    static boolean isRiskyCommandPublic(String cmd, List<String> riskyCommands) {
        return isRiskyCommand(cmd, riskyCommands);
    }

    private static boolean isRiskyCommand(String cmd, List<String> risky) {
        String cleanCmd = cmd.trim();
        // 注释化后命令可能带前导 /，此处单独去掉，保证风险检测仍然生效
        if (cleanCmd.startsWith("/")) {
            cleanCmd = cleanCmd.substring(1).trim();
        }
        if (cleanCmd.toLowerCase().startsWith("minecraft:")) {
            cleanCmd = cleanCmd.substring(10).trim();
        }

        // 处理 execute 命令的递归检查
        if (cleanCmd.toLowerCase().startsWith("execute")) {
            String lower = cleanCmd.toLowerCase();
            int runIndex = lower.indexOf(" run ");
            if (runIndex != -1) {
                String subCmd = cleanCmd.substring(runIndex + 5).trim();
                return isRiskyCommand(subCmd, risky);
            }
        }

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
            player.sendMessage(I18n.t("tool.file.display", displayType, displayPath));

            // 检查是否被冻结
            long freezeRemaining = plugin.getVerificationManager().getPlayerFreezeRemaining(player);
            if (freezeRemaining > 0) {
                player.sendMessage(I18n.t("tool.verify.frozen", freezeRemaining));
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
                player.sendMessage(I18n.t("tool.verify.first.use", toolName));
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
            String writePath = extractFilePathFromArgs(pathArg);
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
                        player.sendMessage(ColorUtil.translateCustomColors("§zFancyHelper§b§r §7> §f" + errorMsg));
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

        // 确认时提前推送 view-fancy 预览链接，玩家可在点击 ✔ 前查看（YOLO 模式则在执行成功后推送）
        File root = Bukkit.getWorldContainer();
        pushPreviewLinkAsync(player, root, type, args);

        String filePath = extractFilePathFromArgs(pathArg);
        String label = "edit".equals(type) ? I18n.t("tool.edit.modifying", filePath) : I18n.t("tool.edit.overwriting", filePath);
        TextComponent msg = new TextComponent(label);
        TextComponent yBtn = new TextComponent(ChatColor.GREEN + "✔");
        yBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cli confirm"));
        yBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(I18n.t("tool.confirm.yes"))));
        msg.addExtra(yBtn);
        msg.addExtra(new TextComponent(" / "));
        TextComponent nBtn = new TextComponent(ChatColor.RED + "✘");
        nBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cli cancel"));
        nBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(I18n.t("tool.confirm.no"))));
        msg.addExtra(nBtn);
        player.spigot().sendMessage(msg);
    }

    /**
     * 确认权限时异步推送 view-fancy 预览链接。
     * #edit 用 dry-run 计算出修改前后 diff；#write 直接提交目标内容。
     * 推送失败不阻塞确认流程。
     */
    private void pushPreviewLinkAsync(Player player, File root, String type, String args) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String viewUrl = null;
            try {
                if ("edit".equals(type)) {
                    EditPreview preview = computeEditPreview(root, args.trim());
                    if (preview.success) {
                        viewUrl = submitEditToViewFancy(preview.path, preview.before, preview.after);
                    }
                } else if ("write".equals(type)) {
                    viewUrl = submitToViewFancy(args);
                }
            } catch (IOException ignored) {
                // 预览失败不影响确认与执行
            }
            if (viewUrl != null && plugin.isEnabled()) {
                sendViewLink(player, viewUrl);
            }
        });
    }

    /**
     * 发送确认按钮
     */
    public void sendConfirmButtons(Player player, String displayAction) {
        TextComponent message = new TextComponent(displayAction != null && !displayAction.trim().isEmpty() 
            ? (ChatColor.GRAY + ">> " + ChatColor.WHITE + displayAction + "   ") : "");

        TextComponent yBtn = new TextComponent(ChatColor.GREEN + "✔");
        yBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cli confirm"));
        yBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(I18n.t("tool.confirm.yes"))));

        TextComponent spacer = new TextComponent(" / ");

        TextComponent nBtn = new TextComponent(ChatColor.RED + "✘");
        nBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cli cancel"));
        nBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(I18n.t("tool.confirm.no"))));

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

                // #write / #edit 成功后推送到 view-fancy（仅 YOLO 模式；NORMAL/SMART 已在确认时推送预览）
                String viewUrl = null;
                if ("write".equals(type) && result.startsWith("成功写入文件:")) {
                    viewUrl = submitToViewFancy(args);
                } else if ("edit".equals(type) && result.startsWith("成功修改文件:")
                        && isYoloMode(player)) {
                    EditPreview preview = computeEditPreview(root, args.trim());
                    if (preview.success) {
                        viewUrl = submitEditToViewFancy(preview.path, preview.before, preview.after);
                    }
                }
                if (viewUrl != null && plugin.isEnabled()) {
                    sendViewLink(player, viewUrl);
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

    private boolean isYoloMode(Player player) {
        DialogueSession session = cliManager.getSession(player.getUniqueId());
        return session != null && session.getMode() == DialogueSession.Mode.YOLO;
    }

    private void sendViewLink(Player player, String viewUrl) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            TextComponent link = new TextComponent(I18n.t("tool.view.online"));
            TextComponent urlComp = new TextComponent(ChatColor.AQUA + "" + ChatColor.UNDERLINE + viewUrl);
            urlComp.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, viewUrl));
            urlComp.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(I18n.t("tool.view.hover"))));
            link.addExtra(urlComp);
            player.spigot().sendMessage(link);
        });
    }

    private String submitToViewFancy(String args) {
        if (args == null) return null;
        String trimmed = args.trim();
        String path;
        String content;
        if (trimmed.startsWith("{")) {
            try {
                com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(trimmed).getAsJsonObject();
                path = json.has("path") ? json.get("path").getAsString() : "";
                content = json.has("content") ? json.get("content").getAsString() : "";
            } catch (Exception e) {
                return null;
            }
        } else {
            int pipeIdx = args.indexOf("|");
            if (pipeIdx == -1) return null;
            path = args.substring(0, pipeIdx).trim();
            content = args.substring(pipeIdx + 1);
            // AI 用 \n 表示换行，\\n 表示字面 \n
            content = content.replace("\\\\n", "\u0001");
            content = content.replace("\\n", "\n");
            content = content.replace("\u0001", "\\n");
        }

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
     * 推送 #edit 的修改前后内容到 view-fancy，前端渲染成 git diff 风格
     * 可由确认时预览或 YOLO 执行成功后调用
     */
    private String submitEditToViewFancy(String path, String before, String after) {
        if (path == null || before == null || after == null) return null;
        try {
            final String baseUrl = "https://view.fancy.baicaizhale.top";
            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            String json = new com.google.gson.Gson().toJson(java.util.Map.of(
                "path", path,
                "type", "edit",
                "before", before,
                "after", after
            ));
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
                plugin.getLogger().warning("[ViewFancy] edit 推送失败: " + e.getMessage());
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
        return computeEdit(root, pathArg, true).result;
    }

    /**
     * #edit 的 dry-run 预览：与执行共用同一套匹配/替换逻辑，但不写盘
     */
    private EditPreview computeEditPreview(File root, String pathArg) throws IOException {
        return computeEdit(root, pathArg, false);
    }

    /**
     * edit 操作的执行结果：成功时为完整修改前后内容，失败时为错误消息
     */
    private static class EditPreview {
        boolean success;
        String result;
        String path;
        String before;
        String after;
    }

    /**
     * #edit 核心逻辑：解析参数、匹配、替换。writeToDisk 为 true 时写入文件，为 false 时仅计算（dry-run 预览）
     */
    private EditPreview computeEdit(File root, String pathArg, boolean writeToDisk) throws IOException {
        EditPreview out = new EditPreview();
        String trimmedArg = pathArg.trim();

        // 支持两种格式：
        // 1. JSON 行（推荐，标准 JSON 转义，无 | 分隔符冲突）：
        //    #edit: {"path":"...","range":"10-10","original":"...","replacement":"..."}  range 可省略(默认 auto)
        // 2. 旧格式（兼容保留）：path|range|original|replacement 或 path|original|replacement
        String path;
        String rangeStr;
        String original;
        String replacement;
        boolean autoSearch = false;

        if (trimmedArg.startsWith("{")) {
            try {
                com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(trimmedArg).getAsJsonObject();
                path = json.has("path") ? json.get("path").getAsString() : "";
                rangeStr = json.has("range") ? json.get("range").getAsString() : "auto";
                original = json.has("original") ? json.get("original").getAsString() : "";
                replacement = json.has("replacement") ? json.get("replacement").getAsString() : "";
                if (path.isEmpty() || original.isEmpty()) {
                    out.result = "错误: #edit JSON 缺少 path 或 original 字段";
                    return out;
                }
                if (rangeStr.isEmpty() || "auto".equalsIgnoreCase(rangeStr)) {
                    autoSearch = true;
                }
            } catch (Exception e) {
                out.result = "错误: #edit JSON 解析失败: " + e.getMessage();
                return out;
            }
        } else {
            String[] editParts = pathArg.split("\\|", 4);

            if (editParts.length < 3) {
                out.result = "错误: #edit 至少需要3个参数，格式：#edit: path|original|replacement、#edit: path|range|original|replacement 或 #edit: {\"path\":\"...\",\"original\":\"...\",\"replacement\":\"...\"}";
                return out;
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
                    out.result = "错误: 行号范围格式不正确，正确格式：10-15、10 或 auto";
                    return out;
                }
            } else if (rangeStr.matches("^\\d+$")) {
                // 单行格式：10
                try {
                    startLine = Integer.parseInt(rangeStr);
                    endLine = startLine;
                } catch (NumberFormatException ignored) {
                    out.result = "错误: 行号格式不正确，正确格式：10-15、10 或 auto";
                    return out;
                }
            } else {
                out.result = "错误: 行号范围格式不正确，正确格式：10-15、10 或 auto";
                return out;
            }
        }

        File file = resolvePathCaseInsensitive(root, path);

        if (!isWithinRoot(root, file)) {
            out.result = "错误: 路径超出服务器目录限制";
            return out;
        }
        if (!file.exists()) {
            out.result = "错误: 文件不存在";
            return out;
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
                out.result = "错误: 起始行号无效 (文件总行数: " + lines.size() + ")";
                return out;
            }
            if (endLine > lines.size()) {
                endLine = lines.size();
            }
            if (startLine > endLine) {
                out.result = "错误: 起始行号不能大于结束行号";
                return out;
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
                out.result = "错误: 在文件中未找到包含指定内容的行\n" +
                             "查找内容: " + original + "\n" +
                             "提示：请提供更简短的关键内容（如 'enabled: true' 而不是整行）";
                return out;
            } else {
                // 构建实际内容用于显示
                StringBuilder rangeContent = new StringBuilder();
                for (int i = startLine - 1; i < endLine; i++) {
                    rangeContent.append(lines.get(i));
                    if (i < endLine - 1) {
                        rangeContent.append("\n");
                    }
                }
                out.result = "错误: 在给定行号范围 " + rangeStr + " 内未找到包含指定内容的行\n" +
                             "查找内容: " + original + "\n" +
                             "行号范围内的实际内容:\n" + rangeContent.toString() + "\n" +
                             "提示：请提供更简短的关键内容（如 'enabled: true' 而不是整行）";
                return out;
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
            out.result = sb.toString();
            return out;
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

        // 写盘（仅实际执行时）
        if (writeToDisk) {
            Files.write(file.toPath(), newLines, StandardCharsets.UTF_8);
        }

        // 返回修改前后的对比
        int actualStartLine = matchStartIndex + 1;
        int actualEndLine = matchEndIndex;
        StringBuilder result = new StringBuilder();
        result.append("成功修改文件: ").append(path).append("\n");
        result.append("行号范围: ").append(actualStartLine).append("-").append(actualEndLine).append("\n");
        result.append("修改前:\n").append(original).append("\n");
        result.append("修改后:\n").append(replacement);

        out.success = true;
        out.path = path;
        out.before = joinLines(lines);
        out.after = joinLines(newLines);
        out.result = result.toString();
        return out;
    }

    private String joinLines(List<String> lines) {
        return String.join("\n", lines);
    }

    /**
     * 从 #write / #edit 参数中提取文件路径（用于 read-before-write 检查、确认按钮展示、view-fancy 预览）。
     * 兼容 JSON 行格式（{"path":"..."}）和旧 | 分隔格式（path|...）。
     */
    private String extractFilePathFromArgs(String pathArg) {
        if (pathArg == null) return "";
        String trimmed = pathArg.trim();
        if (trimmed.startsWith("{")) {
            try {
                com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(trimmed).getAsJsonObject();
                if (json.has("path")) return json.get("path").getAsString();
                return "";
            } catch (Exception ignored) {
                return "";
            }
        }
        int pipeIdx = pathArg.indexOf("|");
        return pipeIdx == -1 ? trimmed : pathArg.substring(0, pipeIdx).trim();
    }

    private String executeWriteOperation(File root, String pathArg) throws IOException {
        // 支持两种格式：
        // 1. JSON 行（推荐）：#write: {"path":"...","content":"..."}  标准 JSON 转义，\n 表示真实换行、\\n 表示字面 \n
        // 2. 旧格式（兼容保留）：#write: <path>|<content>
        String path;
        String content;
        String trimmedArg = pathArg.trim();
        if (trimmedArg.startsWith("{")) {
            try {
                com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(trimmedArg).getAsJsonObject();
                path = json.has("path") ? json.get("path").getAsString() : "";
                content = json.has("content") ? json.get("content").getAsString() : "";
                if (path.isEmpty()) {
                    return "错误: #write JSON 缺少 path 字段";
                }
            } catch (Exception e) {
                return "错误: #write JSON 解析失败: " + e.getMessage();
            }
        } else {
            int pipeIndex = pathArg.indexOf("|");
            if (pipeIndex == -1) {
                return "错误: #write 格式不正确，正确格式：#write: path|content 或 #write: {\"path\":\"...\",\"content\":\"...\"}";
            }
            path = pathArg.substring(0, pipeIndex).trim();
            content = pathArg.substring(pipeIndex + 1);
            // \\n → 字面 \n, \n → 真实换行（仅旧格式需要转义，JSON 格式由解析器处理）
            content = content.replace("\\\\n", "\u0001");
            content = content.replace("\\n", "\n");
            content = content.replace("\u0001", "\\n");
        }

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
            player.sendMessage(I18n.t("tool.ls.done"));
        } else if (type.equals("read")) {
            player.sendMessage(I18n.t("tool.read.done", String.format("%.1f", result.length() / 1024.0)));
        } else if (type.equals("edit")) {
            player.sendMessage(I18n.t("tool.edit.done"));
            if (result.contains("修改前:\n") && result.contains("修改后:\n")) {
                String[] parts = result.split("修改前:\n|修改后:\n");
                if (parts.length >= 3) {
                    String[] beforeLines = parts[1].split("\n");
                    String[] afterLines = parts[2].split("\n");
                    player.sendMessage(ChatColor.GRAY + "─────────────────────────────────");
                    player.sendMessage(I18n.t("tool.edit.before"));
                    for (String line : beforeLines) {
                        player.sendMessage(ChatColor.GRAY + "  " + line);
                    }
                    player.sendMessage(ChatColor.GRAY + "─────────────────────────────────");
                    player.sendMessage(I18n.t("tool.edit.after"));
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
     * <p>
     * 以真实玩家身份执行（player.performCommand），保证 instanceof CraftPlayer、
     * 原版命令、身份比对、权限校验均按真实玩家处理。
     * 命令输出通过 PacketCaptureManager（ProtocolLib 数据包层）捕获。
     */
    public void executeCommand(Player player, String command) {
        if (!plugin.isEnabled()) return;

        // 注释化：不再删除前导 /，命令保留原样执行（服务器端 dispatchCommand 会自行处理 /）
        // String trimmed = command.trim();
        // final String cleanCommand = trimmed.startsWith("/") ? trimmed.substring(1) : trimmed;
        final String cleanCommand = command.trim();
        if (cleanCommand.isEmpty()) return;

        // Paper 1.21.11+ 将游戏规则名从 camelCase 改为 snake_case：版本较高时自动转换格式
        final String executedCommand = convertGameruleForServer(cleanCommand);

        if (plugin.getPacketCaptureManager() != null) {
            plugin.getPacketCaptureManager().startCapture(player);
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            player.sendMessage(I18n.t("tool.run.dispatched"));

            boolean success;
            String commandError = null;
            try {
                // 以真实玩家身份执行命令
                success = player.performCommand(executedCommand);
            } catch (Throwable t) {
                plugin.getCloudErrorReport().report(t);
                // 提取被 VanillaCommandWrapper 包装的底层真实报错（如 Incorrect argument ...）
                commandError = extractRootCauseMessage(t);
                plugin.getLogger().warning("[CLI] 执行命令时出错: " + commandError);
                plugin.getLogger().log(java.util.logging.Level.WARNING, "[CLI] 命令执行异常完整堆栈: " + executedCommand, t);
                success = false;
            }

            boolean finalSuccess = success;
            final String finalCommandError = commandError;

            if (!plugin.isEnabled()) return;

            // 命令执行失败时立即收尾，不进入静默等待
            if (!finalSuccess) {
                String errorPacket = (plugin.getPacketCaptureManager() != null)
                        ? plugin.getPacketCaptureManager().stopCapture(player) : "";
                finalizeRun(player, executedCommand, finalSuccess, finalCommandError, errorPacket, "");
                return;
            }

            // 静默驱动窗口：捕获缓冲长度增长即"有输出"，重置静默计时；连续无新输出超过
            // CAPTURE_QUIET_MS 即认为命令输出已完整，停止捕获并把结果反馈给 AI。
            // 相比固定 1s/5s 延迟，既能吃满 spark 这类持续输出的命令，又不会让秒回命令白等。
            final long[] lastLen = {0L};
            final boolean[] hasOutput = {false};
            final long[] quietSince = {System.currentTimeMillis()};
            final long captureStart = System.currentTimeMillis();
            final org.bukkit.scheduler.BukkitTask[] taskHolder = new org.bukkit.scheduler.BukkitTask[1];

            org.bukkit.scheduler.BukkitTask timer = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
                @Override
                public void run() {
                    if (!plugin.isEnabled() || !player.isOnline()) {
                        stop();
                        return;
                    }

                    long len = 0;
                    if (plugin.getPacketCaptureManager() != null) {
                        len = plugin.getPacketCaptureManager().captureLength(player);
                    }
                    long now = System.currentTimeMillis();
                    boolean quietLongEnough;
                    if (len > lastLen[0]) {
                        // 有新输出：记录已产生输出并重置静默计时
                        lastLen[0] = len;
                        hasOutput[0] = true;
                        quietSince[0] = now;
                        return;
                    }
                    if (hasOutput[0]) {
                        // 有输出后安静 2s → 输出已完整
                        quietLongEnough = now - quietSince[0] >= CAPTURE_QUIET_MS;
                    } else {
                        // 从未有输出：等待首次输出宽限 5s
                        quietLongEnough = now - captureStart >= CAPTURE_NO_OUTPUT_MS;
                    }
                    boolean timedOut = now - captureStart >= CAPTURE_MAX_WAIT_MS;
                    if (quietLongEnough || timedOut) {
                        stop();
                        if (timedOut && !quietLongEnough) {
                            player.sendMessage(I18n.t("tool.run.no.feedback"));
                        }
                        String packetOutput = (plugin.getPacketCaptureManager() != null)
                                ? plugin.getPacketCaptureManager().stopCapture(player) : "";
                        // 兜底：数据包仍无输出时，尝试从控制台日志抓取命令反馈
                        String consoleFeedback = packetOutput.isEmpty()
                                ? getConsoleFeedback(player, executedCommand) : "";
                        finalizeRun(player, executedCommand, finalSuccess, finalCommandError, packetOutput, consoleFeedback);
                    }
                }

                private void stop() {
                    org.bukkit.scheduler.BukkitTask self = taskHolder[0];
                    if (self != null) {
                        self.cancel();
                    }
                }
            }, 0L, CAPTURE_POLL_TICKS);
            taskHolder[0] = timer;
        });
    }

    /**
     * 收尾命令执行：构建结果并反馈给 AI（单工具路径与静默窗口共用）。
     */
    private void finalizeRun(Player player, String executedCommand, boolean success, String serverError, String packetOutput, String consoleFeedback) {
        String result = buildCommandResult(executedCommand, packetOutput, success, serverError, consoleFeedback);
        cliManager.feedbackToAI(player, "#run_result: " + result);
    }

    /**
     * 构建命令执行结果
     * <p>
     * 反馈优先级（从高到低）：
     * 1. 数据包捕获的玩家消息（最真实）
     * 2. 命令成功时控制台日志兜底反馈（issued server command 的下一句）
     * 3. 命令失败时服务器抛出的真实报错（已提取根因）
     * 4. 通用失败文案
     *
     * @param serverError    服务器抛出的真实报错（已提取根因），无异常时为 null
     * @param consoleFeedback 控制台日志兜底反馈，仅在成功且无数据包输出时传入，无内容为空串
     */
    private String buildCommandResult(String command, String packetOutput, boolean success, String serverError, String consoleFeedback) {
        if (!packetOutput.isEmpty()) {
            return packetOutput;
        }
        if (success) {
            // 兜底：数据包没抓到输出时，控制台反馈比"结果未知"更有价值
            if (consoleFeedback != null && !consoleFeedback.isEmpty()) {
                return "控制台反馈: " + consoleFeedback;
            }
            if (command.toLowerCase().startsWith("tp")) {
                return "命令执行结果未知 (你可以用choose工具问一下用户)";
            } else if (command.toLowerCase().startsWith("op") || command.toLowerCase().startsWith("deop")) {
                return "命令执行结果未知 (权限变更指令通常仅显示在控制台或被静默处理)";
            }
            return "命令执行结果未知 (你可以用choose工具问一下用户)";
        }
        // 失败时优先反馈服务器真实报错，让 AI 能根据错误修正命令后重试
        if (serverError != null && !serverError.isEmpty()) {
            String hint = "";
            if (command.toLowerCase().startsWith("gamerule") && isGameruleSnakeCaseServer()) {
                hint = " 提示：该服务器游戏规则名使用 snake_case（如 keepInventory → keep_inventory），且部分规则已合并或改名，请检查规则名后重试。";
            }
            return "命令执行失败。服务器返回错误: " + serverError + "。" + hint + " 请根据错误信息修正命令后重试。";
        }
        return "命令执行失败。可能原因：\n1. 命令语法错误\n2. 权限不足\n3. 命令内部要求特定执行者\n请检查语法或换一种实现方式。";
    }

    /**
     * 控制台日志兜底：找到玩家执行该命令时服务器记录的 "xxx issued server command" 行，
     * 抓取其后的下一句（或多句）作为命令反馈。
     * 仅在数据包捕获无输出时调用，作为最后的信息来源。
     */
    private String getConsoleFeedback(Player player, String command) {
        try {
            File logFile = new File("logs", "latest.log");
            if (!logFile.isFile()) return "";

            List<String> tail = readLogTail(logFile, 64 * 1024);
            return extractConsoleFeedback(tail, player.getName(), command);
        } catch (Exception e) {
            if (plugin.getConfigManager().isDebug()) {
                plugin.getLogger().warning("[CLI] 控制台日志兜底读取失败: " + e.getMessage());
            }
            return "";
        }
    }

    /**
     * 读取日志文件末尾的内容（最多 maxBytes 字节，限制在最后 200 行）
     */
    private static List<String> readLogTail(File file, int maxBytes) throws IOException {
        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(file, "r")) {
            long length = raf.length();
            long start = Math.max(0, length - maxBytes);
            raf.seek(start);
            byte[] buf = new byte[(int) (length - start)];
            raf.readFully(buf);
            String text = new String(buf, StandardCharsets.UTF_8);
            String[] allLines = text.split("\r?\n");
            // 从文件中间开始读时第一行是截断残片，跳过；再限制最多 200 行
            int skipFirst = (start > 0) ? 1 : 0;
            int from = Math.max(skipFirst, allLines.length - 200);
            List<String> tail = new java.util.ArrayList<>();
            for (int i = from; i < allLines.length; i++) {
                tail.add(allLines[i]);
            }
            return tail;
        }
    }

    /**
     * 从日志行中提取指定玩家执行指定命令后的控制台反馈（纯逻辑，便于单测）。
     * 匹配 "&lt;玩家&gt; issued server command: /&lt;命令&gt;" 行，取其后最多 5 行非空内容，
     * 遇到下一条 "issued server command" 即停止。找不到匹配返回空串。
     */
    static String extractConsoleFeedback(List<String> logLines, String playerName, String command) {
        if (logLines == null || playerName == null || command == null) return "";

        // 日志中的命令带一个前导 /
        String normalized = command.startsWith("/") ? command.substring(1) : command;
        String marker = playerName + " issued server command: /" + normalized;

        for (int i = logLines.size() - 1; i >= 0; i--) {
            String line = logLines.get(i);
            if (line == null) continue;
            // issued server command 行中命令一定在行尾，用 endsWith 精确匹配，
            // 避免命令 A 被更长的命令 B（如 say hello vs say hello world）误匹配
            if (!line.trim().endsWith(marker)) continue;

            StringBuilder sb = new StringBuilder();
            int grabbed = 0;
            for (int j = i + 1; j < logLines.size() && grabbed < 5; j++) {
                String next = stripLogPrefix(logLines.get(j));
                if (next.isEmpty()) continue;
                if (next.contains(" issued server command:")) break;
                if (grabbed > 0) sb.append("\n");
                sb.append(next);
                grabbed++;
            }
            return sb.toString();
        }
        return "";
    }

    /**
     * 去掉控制台日志行的前缀（形如 "[HH:MM:SS LEVEL]: "），保留正文
     */
    static String stripLogPrefix(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        int idx = s.indexOf("]:");
        if (idx != -1) {
            s = s.substring(idx + 2).trim();
        }
        return s;
    }

    /**
     * 提取异常链中最深层（根因）的真实错误信息。
     * CraftBukkit 的 VanillaCommandWrapper 会把底层 Brigadier 异常包装成
     * "Unhandled exception executing ..." 的 CommandException，真正的错误
     * （如 CommandSyntaxException: Incorrect argument ...）藏在 cause 链深处，
     * 只有取出根因，AI 才能根据真实报错自我修正。
     */
    static String extractRootCauseMessage(Throwable t) {
        // 先沿 cause 链找到最深层（根因）异常
        Throwable current = t;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        // 根因没有可读消息时，向上回溯到最近一个有消息的异常，避免返回 null
        String msg = current.getMessage();
        if (msg == null || msg.trim().isEmpty()) {
            Throwable walker = t;
            while (walker != null) {
                String m = walker.getMessage();
                if (m != null && !m.trim().isEmpty()) {
                    return m;
                }
                walker = walker.getCause();
            }
            return current.getClass().getSimpleName();
        }
        return msg;
    }

    /**
     * gamerule 格式自适应：当服务器版本 &gt;= 1.21.11（游戏规则名改为 snake_case）时，
     * 自动把命令里的 camelCase 规则名转为 snake_case（如 keepInventory → keep_inventory）。
     * 旧版本服务器不做转换（camelCase 仍是正确格式）。
     */
    static String convertGameruleForServer(String command) {
        if (command == null || command.isEmpty()) return command;
        if (!isGameruleSnakeCaseServer()) return command;
        return convertGameruleCommand(command);
    }

    /**
     * 检测服务器是否已切换为 snake_case 游戏规则名（Paper 1.21.11+）。
     * Bukkit.getBukkitVersion() 形如 "1.21.11-R0.1-SNAPSHOT"，先截掉 -R 后缀再比较。
     */
    static boolean isGameruleSnakeCaseServer() {
        try {
            String version = Bukkit.getBukkitVersion();
            if (version == null) return false;
            int dash = version.indexOf('-');
            if (dash != -1) version = version.substring(0, dash);
            return compareVersions(version, "1.21.11") >= 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 将 gamerule 命令中的 camelCase 规则名转换为 snake_case，只改规则名、不动其他部分。
     * 例: "gamerule keepInventory true" → "gamerule keep_inventory true"
     */
    static String convertGameruleCommand(String command) {
        if (command == null || command.isEmpty()) return command;
        String prefix = command.startsWith("/") ? "/" : "";
        String body = command.startsWith("/") ? command.substring(1) : command;
        String[] parts = body.split("\\s+", 3);
        if (parts.length < 2) return command;
        String cmdName = parts[0].toLowerCase();
        if (cmdName.startsWith("minecraft:")) {
            cmdName = cmdName.substring("minecraft:".length());
        }
        if (!cmdName.equals("gamerule")) return command;
        String newName = camelToSnake(parts[1]);
        if (newName.equals(parts[1])) return command;
        if (parts.length == 2) {
            return prefix + parts[0] + " " + newName;
        }
        return prefix + parts[0] + " " + newName + " " + parts[2];
    }

    /**
     * camelCase → snake_case：在大写字母前插入下划线并转小写。
     * 例: keepInventory → keep_inventory, randomTickSpeed → random_tick_speed
     */
    static String camelToSnake(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) {
                    sb.append('_');
                }
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * 数值化版本比较：逐段按数字比较（"1.21.10" &lt; "1.21.11" &lt; "26.1"）
     */
    static int compareVersions(String a, String b) {
        if (a == null || a.isEmpty()) a = "0";
        if (b == null || b.isEmpty()) b = "0";
        String[] pa = a.split("\\.");
        String[] pb = b.split("\\.");
        int n = Math.max(pa.length, pb.length);
        for (int i = 0; i < n; i++) {
            int x = i < pa.length ? parseVersionPart(pa[i]) : 0;
            int y = i < pb.length ? parseVersionPart(pb[i]) : 0;
            if (x != y) return Integer.compare(x, y);
        }
        return 0;
    }

    private static int parseVersionPart(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
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

    // ==================== Skill Sidecar 文件工具 ====================

    /**
     * 处理 #skill 工具
     * 支持子命令语法：
     *   #skill: <id>              - 加载 Skill（自动读取 skill.md）
     *   #skill: <id> list         - 列出 Skill 目录中的附属文件
     *   #skill: <id> read <file>  - 读取 Skill 目录中的指定附属文件
     */
    private void handleSkillTool(Player player, String args, DialogueSession session) {
        String trimmed = args.trim();
        if (trimmed.isEmpty()) {
            cliManager.feedbackToAI(player, "#skill_result: 错误 - 请提供 Skill ID");
            return;
        }

        String[] tokens = trimmed.split("\\s+");
        String skillId = tokens[0].toLowerCase();

        // 仅当第二个 token 明确是 list / read 时才走子命令分支
        if (tokens.length >= 2) {
            String subcommand = tokens[1].toLowerCase();
            if (subcommand.equals("list")) {
                handleSkillList(player, skillId);
                return;
            }
            if (subcommand.equals("read")) {
                if (tokens.length < 3) {
                    cliManager.feedbackToAI(player, "#skill_result: 错误 - 格式: #skill: <id> read <filename>");
                    return;
                }
                String fileName = trimmed.substring(trimmed.toLowerCase().indexOf(" read ") + 6).trim();
                handleSkillRead(player, skillId, fileName);
                return;
            }
        }

        // 默认行为：加载 Skill
        handleSkillLoad(player, skillId, session);
    }

    /**
     * 加载 Skill（原 #skill: <id> 行为）
     * 加载后会在反馈末尾提示 AI 可以用 read 子命令读取更多文件
     */
    private void handleSkillLoad(Player player, String skillId, DialogueSession session) {
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
        String suffix = added ? "" : " (already loaded)";
        Map<String, String> templateContext = new HashMap<>();
        templateContext.put("player", player.getName());
        StringBuilder result = new StringBuilder();
        result.append("#skill_result: Loaded Skill [").append(skill.getMetadata().getName()).append("]").append(suffix).append("\n\n");
        result.append(skill.getFormattedProcessedContent(templateContext));

        // 提示 AI 可以读取附属文件
        File skillDir = skill.getSkillDirectory();
        if (skillDir != null) {
            File[] files = skillDir.listFiles();
            int sidecarCount = 0;
            if (files != null) {
                for (File f : files) {
                    if (f.isFile() && !"skill.md".equalsIgnoreCase(f.getName())) {
                        sidecarCount++;
                    }
                }
            }
            if (sidecarCount > 0) {
                result.append("\n\n---\nThis Skill has ").append(sidecarCount)
                      .append(" additional file(s) in its directory.\n")
                      .append("Use `#skill: ").append(skill.getId()).append(" read <filename>` to read them.\n")
                      .append("Use `#skill: ").append(skill.getId()).append(" list` to see available files.");
            }
        }

        cliManager.feedbackToAI(player, result.toString());
    }

    /**
     * 列出 Skill 目录中的附属文件
     */
    private void handleSkillList(Player player, String skillId) {
        cliManager.setGenerating(player.getUniqueId(), false, CLIManager.GenerationStatus.EXECUTING_TOOL);

        org.YanPl.model.Skill skill = plugin.getSkillManager().getSkill(skillId);
        if (skill == null) {
            cliManager.feedbackToAI(player, "#skill_result: 错误 - 未找到 Skill: " + skillId);
            return;
        }

        File skillDir = skill.getSkillDirectory();
        if (skillDir == null) {
            cliManager.feedbackToAI(player, "#skill_result: 该 Skill 为平面格式，无附加文件目录");
            return;
        }

        File[] files = skillDir.listFiles();
        if (files == null) {
            cliManager.feedbackToAI(player, "#skill_result: 错误 - 无法读取 Skill 目录");
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Skill [").append(skillDir.getName()).append("] directory contents:\n");

        Arrays.sort(files, (f1, f2) -> {
            if (f1.isDirectory() && !f2.isDirectory()) return -1;
            if (!f1.isDirectory() && f2.isDirectory()) return 1;
            return f1.getName().compareToIgnoreCase(f2.getName());
        });

        boolean hasExtra = false;
        for (File f : files) {
            if (f.isFile() && "skill.md".equalsIgnoreCase(f.getName())) {
                sb.append("  [SKILL] skill.md (loaded)\n");
            } else if (f.isDirectory()) {
                sb.append("  [DIR] ").append(f.getName()).append("/\n");
                hasExtra = true;
            } else {
                sb.append("  [FILE] ").append(f.getName())
                  .append(" (").append(f.length() / 1024).append("KB)\n");
                hasExtra = true;
            }
        }

        if (!hasExtra) {
            sb.append("  No additional files in this directory.\n");
        }

        sb.append("Use `#skill: ").append(skillId).append(" read <filename>` to read a file.");

        cliManager.feedbackToAI(player, "#skill_result: " + sb.toString());
    }

    /**
     * 读取 Skill 目录中的附属文件
     * 安全: 使用 isWithinRoot() 防止路径遍历
     */
    private void handleSkillRead(Player player, String skillId, String fileName) {
        cliManager.setGenerating(player.getUniqueId(), false, CLIManager.GenerationStatus.EXECUTING_TOOL);

        org.YanPl.model.Skill skill = plugin.getSkillManager().getSkill(skillId);
        if (skill == null) {
            cliManager.feedbackToAI(player, "#skill_result: 错误 - 未找到 Skill: " + skillId);
            return;
        }

        File skillDir = skill.getSkillDirectory();
        if (skillDir == null) {
            cliManager.feedbackToAI(player, "#skill_result: 该 Skill 为平面格式，无附加文件目录");
            return;
        }

        // 安全：限制在 Skill 目录内
        File resolvedFile = resolvePathCaseInsensitive(skillDir, fileName);
        if (!isWithinRoot(skillDir, resolvedFile)) {
            cliManager.feedbackToAI(player, "#skill_result: 错误 - 路径超出 Skill 目录限制");
            return;
        }

        if (!resolvedFile.exists()) {
            cliManager.feedbackToAI(player, "#skill_result: 错误 - 文件不存在: " + fileName);
            return;
        }
        if (resolvedFile.isDirectory()) {
            cliManager.feedbackToAI(player, "#skill_result: 错误 - 这是一个目录，请使用 #skill: " + skillId + " list");
            return;
        }
        if ("skill.md".equalsIgnoreCase(resolvedFile.getName())) {
            cliManager.feedbackToAI(player, "#skill_result: 错误 - skill.md 已通过 #skill: " + skillId + " 加载，无需重复读取");
            return;
        }

        // 异步读取
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String content = readSkillSidecarFile(resolvedFile);
                if (!plugin.isEnabled()) return;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    cliManager.feedbackToAI(player, "#skill_result: " + content);
                });
            } catch (Exception e) {
                plugin.getCloudErrorReport().report(e);
                if (!plugin.isEnabled()) return;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    cliManager.feedbackToAI(player, "#skill_result: 错误 - " + e.getMessage());
                });
            }
        });
    }

    /**
     * 读取 Skill 附属文件内容（带行号，限制大小）
     */
    private String readSkillSidecarFile(File file) throws IOException {
        if (file.length() > 1024 * 1024) {
            return "Error: file too large (" + (file.length() / 1024) + "KB), max 1MB.";
        }

        StringBuilder content = new StringBuilder();
        content.append("--- ").append(file.getName()).append(" ---\n");

        try (java.io.BufferedReader reader = java.nio.file.Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            String line;
            int lineNum = 1;
            int maxLines = 2000;

            while ((line = reader.readLine()) != null) {
                if (lineNum > maxLines) {
                    content.append("\n... (truncated at ").append(maxLines).append(" lines) ...");
                    break;
                }
                content.append(lineNum).append(": ").append(line).append("\n");
                lineNum++;
            }
        }

        return content.toString();
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
                player.sendMessage(I18n.t("tool.ask.missing.question"));
                // 反馈 AI 知晓失败：否则 AI 无感知地干等（批次中屏障 60s 超时兜底，单路径永久挂起）
                cliManager.feedbackToAI(player, "#ask_error: 提问缺少 question 字段，请检查格式后重试");
            }
        } catch (Exception e) {
            player.sendMessage(I18n.t("tool.ask.parse.fail", e.getMessage()));
            // 同上：解析失败必须反馈 AI，避免对话卡在 EXECUTING_TOOL
            cliManager.feedbackToAI(player, "#ask_error: 提问参数解析失败 - " + e.getMessage());
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
            try {
                if (query.toLowerCase().contains("widely")) {
                    result = performWideSearch(query);
                } else {
                    result = performWikiSearch(query, player);
                }
            } catch (Exception e) {
                // 异步异常兜底：必须反馈 AI，否则对话卡在 EXECUTING_TOOL（批次中 60s 超时兜底）
                plugin.getCloudErrorReport().report(e);
                result = "搜索执行异常: " + e.getMessage();
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
            // 工具调用失败（多为模型格式问题）是内部细节，不展示给玩家，
            // 仅回灌 AI 让它自行修正，控制台已由 TodoManager 记录 warning。
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
     * 处理 #remember_global 工具 - 保存服务器级记忆（仅管理员）
     * 格式: #remember_global: 内容 或 #remember_global: 分类|内容
     */
    private void handleRememberGlobalTool(Player player, String args) {
        if (!checkAdminPermission(player, "#remember_global_result")) {
            return;
        }
        UUID uuid = player.getUniqueId();
        cliManager.setGenerating(uuid, false, CLIManager.GenerationStatus.EXECUTING_TOOL);

        if (args == null || args.trim().isEmpty()) {
            cliManager.feedbackToAI(player, "#remember_global_result: error - 需要提供要记住的内容，格式: #remember_global: 内容 或 #remember_global: 分类|内容");
            return;
        }

        String content = args.trim();
        String category = "rule";

        if (content.contains("|")) {
            String[] parts = content.split("\\|", 2);
            if (parts.length == 2) {
                category = parts[0].trim();
                content = parts[1].trim();
            }
        }

        if (content.isEmpty()) {
            cliManager.feedbackToAI(player, "#remember_global_result: error - 服务器记忆内容不能为空");
            return;
        }

        String result = plugin.getServerMemoryManager().addMemory(content, category, player.getName());
        cliManager.feedbackToAI(player, "#remember_global_result: " + result);
    }

    /**
     * 处理 #forget_global 工具 - 删除服务器级记忆（仅管理员）
     * 格式: #forget_global: 序号 或 #forget_global: all
     */
    private void handleForgetGlobalTool(Player player, String args) {
        if (!checkAdminPermission(player, "#forget_global_result")) {
            return;
        }
        UUID uuid = player.getUniqueId();
        cliManager.setGenerating(uuid, false, CLIManager.GenerationStatus.EXECUTING_TOOL);

        if (args == null || args.trim().isEmpty()) {
            cliManager.feedbackToAI(player, "#forget_global_result: error - 需要提供序号或 'all'，格式: #forget_global: 序号 或 #forget_global: all");
            return;
        }

        String arg = args.trim();

        if (arg.equalsIgnoreCase("all")) {
            String result = plugin.getServerMemoryManager().clearMemories();
            cliManager.feedbackToAI(player, "#forget_global_result: " + result);
            return;
        }

        try {
            int index = Integer.parseInt(arg);
            String result = plugin.getServerMemoryManager().removeMemory(index);
            cliManager.feedbackToAI(player, "#forget_global_result: " + result);
        } catch (NumberFormatException e) {
            cliManager.feedbackToAI(player, "#forget_global_result: error - 无效的序号: " + arg);
        }
    }

    /**
     * 处理 #edit_global 工具 - 修改服务器级记忆（仅管理员）
     * 格式: #edit_global: 序号|新内容 或 #edit_global: 序号|分类|新内容
     */
    private void handleEditGlobalTool(Player player, String args) {
        if (!checkAdminPermission(player, "#edit_global_result")) {
            return;
        }
        UUID uuid = player.getUniqueId();
        cliManager.setGenerating(uuid, false, CLIManager.GenerationStatus.EXECUTING_TOOL);

        if (args == null || args.trim().isEmpty()) {
            cliManager.feedbackToAI(player, "#edit_global_result: error - 格式错误，正确格式: #edit_global: 序号|新内容 或 #edit_global: 序号|分类|新内容");
            return;
        }

        String[] parts = args.trim().split("\\|", 3);

        if (parts.length < 2) {
            cliManager.feedbackToAI(player, "#edit_global_result: error - 格式错误，正确格式: #edit_global: 序号|新内容 或 #edit_global: 序号|分类|新内容");
            return;
        }

        try {
            int index = Integer.parseInt(parts[0].trim());
            String content;
            String category;

            if (parts.length == 2) {
                category = "rule";
                content = parts[1].trim();
            } else {
                category = parts[1].trim();
                content = parts[2].trim();
            }

            if (content.isEmpty()) {
                cliManager.feedbackToAI(player, "#edit_global_result: error - 服务器记忆内容不能为空");
                return;
            }

            String result = plugin.getServerMemoryManager().updateMemory(index, content, category);
            cliManager.feedbackToAI(player, "#edit_global_result: " + result);
        } catch (NumberFormatException e) {
            cliManager.feedbackToAI(player, "#edit_global_result: error - 无效的序号: " + parts[0].trim());
        }
    }

    /**
     * 检查玩家是否拥有 fancyhelper.admin 权限，无权限时回喂 AI 并返回 false
     */
    private boolean checkAdminPermission(Player player, String feedbackPrefix) {
        if (!player.hasPermission("fancyhelper.admin")) {
            String error = "#error: 无权限（需要 fancyhelper.admin），仅管理员可修改服务器级记忆。普通偏好请用 #remember。";
            cliManager.feedbackToAI(player, feedbackPrefix + ": " + error);
            return false;
        }
        return true;
    }

    /**
     * 处理 #webfetch 工具 - 读取网页内容
     * 格式: #webfetch: https://example.com
     */
    private void handleWebFetchTool(Player player, String args, DialogueSession session) {
        UUID uuid = player.getUniqueId();
        cliManager.setGenerating(uuid, false, CLIManager.GenerationStatus.EXECUTING_TOOL);

        if (args == null || args.trim().isEmpty()) {
            player.sendMessage(I18n.t("tool.webfetch.need.url"));
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
        player.sendMessage(I18n.t("tool.webfetch.fetching", url));
        
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
                    player.sendMessage(I18n.t("tool.webfetch.fail", e.getMessage()));
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
            String jinaUrl = "https://jina.proxy.baicaizhale.top/" + url;
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
            McpTypes.McpToolCallResult result;
            try {
                result = plugin.getMcpManager().callExternalTool(fServerName, fToolName, fArguments);
            } catch (Exception e) {
                // 异步异常兜底：MCP 客户端连接/协议错误等必须反馈 AI，否则对话卡在 EXECUTING_TOOL
                plugin.getCloudErrorReport().report(e);
                if (!plugin.isEnabled()) return;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    cliManager.feedbackToAI(player, "#mcp_error: 调用异常 - " + e.getMessage());
                });
                return;
            }

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
