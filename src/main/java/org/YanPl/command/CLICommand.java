package org.YanPl.command;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.YanPl.FancyHelper;
import org.YanPl.manager.InstructionManager;
import org.YanPl.model.DialogueSession;
import org.YanPl.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * /fancyhelper 命令的处理器。
 * 提供切换 CLI、重载、查看状态等子命令，并支持 Tab 完成。
 */
public class CLICommand implements CommandExecutor, TabCompleter {
    private final FancyHelper plugin;

    public CLICommand(FancyHelper plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "该命令仅限玩家使用。");
                return true;
            }
            Player player = (Player) sender;
            if (!player.hasPermission("fancyhelper.cli")) {
                player.sendMessage(ChatColor.RED + "你没有权限使用此命令。");
                return true;
            }
            toggleCLIMode(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();
        switch (subCommand) {
            case "reload":
                if (!sender.hasPermission("fancyhelper.reload")) {
                    sender.sendMessage(ColorUtil.translateCustomColors("§zFancyHelper§b§r §7> §f你没有权限执行重载。"));
                    return true;
                }
                handleReload(sender, args);
                break;
            case "status":
                handleStatus(sender);
                break;
            case "update":
            case "checkupdate":
                if (!(sender.isOp() || sender.hasPermission("fancyhelper.reload"))) {
                    sender.sendMessage(ColorUtil.translateCustomColors("§zFancyHelper§b§r §7> §f你没有权限检查更新。"));
                    return true;
                }
                sender.sendMessage(ColorUtil.translateCustomColors("§zFancyHelper§b§r §7> §f正在检查更新..."));
                plugin.getUpdateManager().checkForUpdates(sender instanceof Player ? (Player) sender : null);
                break;
            case "notice":
                if (!sender.hasPermission("fancyhelper.notice")) {
                    sender.sendMessage(ColorUtil.translateCustomColors("§zFancyHelper§b§r §7> §f你没有权限查看公告。"));
                    return true;
                }
                if (args.length > 1 && args[1].equalsIgnoreCase("read")) {
                    if (!(sender instanceof Player)) {
                        sender.sendMessage(ChatColor.RED + "该子命令仅限玩家使用。");
                        return true;
                    }
                    plugin.getNoticeManager().markAsRead((Player) sender);
                } else {
                    handleNotice(sender);
                }
                break;
            case "upgrade":
            case "download":
                if (!sender.hasPermission("fancyhelper.reload")) {
                    sender.sendMessage(ColorUtil.translateCustomColors("§zFancyHelper§b§r §7> §f你没有权限执行更新。"));
                    return true;
                }
                plugin.getUpdateManager().downloadAndInstall(sender instanceof Player ? (Player) sender : null, true);
                break;
            case "yolo":
            case "normal":
            case "confirm":
            case "cancel":
            case "agree":
            case "read":
            case "thought":
            case "settings":
            case "set":
            case "toggle":
            case "tools":
            case "display":
            case "select":
            case "exempt_anti_loop":
            case "todo":
            case "gui":
            case "menu":
            case "retry":
            case "stop":
            case "memory":
            case "mem":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "该子命令仅限玩家使用。");
                    return true;
                }
                Player player = (Player) sender;
                return handlePlayerSubCommand(player, subCommand, args);
            case "help":
                sendHelp(sender);
                return true;
            default:
                sendHelp(sender);
                break;
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ColorUtil.translateCustomColors("§zFancyHelper§b§r §7> §f可用子命令:"));
        sender.sendMessage(" §7- §b/cli §f: 切换进入/退出 CLI 模式");
        sender.sendMessage(" §7- §b/cli reload §f: 重新加载配置与工作区");
        sender.sendMessage(" §7- §b/cli reload deeply §f: 深度重载（完全重启插件）");
        sender.sendMessage(" §7- §b/cli status §f: 查看插件运行状态");
        sender.sendMessage(" §7- §b/cli checkupdate §f: 检查更新");
        sender.sendMessage(" §7- §b/cli upgrade §f: 下载并安装更新");
        sender.sendMessage(" §7- §b/cli notice §f: 查看系统公告");
        sender.sendMessage(" §7- §b/cli notice read §f: 将公告标记为已读");
        sender.sendMessage(" §7- §b/cli todo §f: 查看待办事项列表");
        sender.sendMessage(" §7- §b/cli settings §f: 打开个人设置界面");
        sender.sendMessage(" §7- §b/cli memory §f: 管理偏好记忆");
        sender.sendMessage(" §7- §b/cli tools §f: 查看工具列表");
        sender.sendMessage(" §7- §b/cli toggle <ls|read|diff> §f: 启用/禁用工具");
        sender.sendMessage(" §7- §b/cli display §f: 切换显示位置");
        sender.sendMessage(" §7- §b/cli yolo §f: 切换到 YOLO 模式（自动执行命令）");
        sender.sendMessage(" §7- §b/cli normal §f: 切换到普通模式（需要确认）");
        sender.sendMessage(" §7- §b/cli retry §f: 重试上一次失败的 AI 调用");
        sender.sendMessage(" §7- §b/cli stop §f: 停止当前 AI 对话");
    }

    private boolean handlePlayerSubCommand(Player player, String subCommand, String[] args) {
        switch (subCommand) {
            case "yolo":
                plugin.getCliManager().switchMode(player, DialogueSession.Mode.YOLO);
                return true;
            case "normal":
                plugin.getCliManager().switchMode(player, DialogueSession.Mode.NORMAL);
                return true;
            case "confirm":
                plugin.getCliManager().handleConfirm(player);
                return true;
            case "cancel":
                plugin.getCliManager().handleCancel(player);
                return true;
            case "agree":
                plugin.getCliManager().handleChat(player, "agree");
                return true;
            case "read":
                plugin.getCliManager().openEulaBook(player);
                return true;
            case "thought":
                plugin.getCliManager().handleThought(player, Arrays.copyOfRange(args, 1, args.length));
                return true;
            case "settings":
            case "set":
                handleSettings(player);
                return true;
            case "toggle":
                if (args.length > 1) {
                    String tool = args[1].toLowerCase();
                    if (tool.equals("ls") || tool.equals("read") || tool.equals("diff")) {
                        boolean currentState = plugin.getConfigManager().isPlayerToolEnabled(player, tool);
                        if (currentState) {
                            plugin.getConfigManager().setPlayerToolEnabled(player, tool, false);
                            player.sendMessage(ChatColor.YELLOW + "工具 " + tool + " 已禁用。下次开启需要重新验证。");
                            handleTools(player);
                        } else {
                            player.sendMessage(ChatColor.AQUA + "正在为工具 " + tool + " 发起安全验证...");
                            plugin.getVerificationManager().startVerification(player, tool, () -> {
                                plugin.getConfigManager().setPlayerToolEnabled(player, tool, true);
                                player.sendMessage(ChatColor.GREEN + "验证成功！工具 " + tool + " 已启用。");
                                handleTools(player);
                            });
                        }
                    }
                }
                return true;
            case "tools":
                handleTools(player);
                return true;
            case "display":
                String currentPos = plugin.getConfigManager().getPlayerDisplayPosition(player);
                String newPos = currentPos.equalsIgnoreCase("actionbar") ? "subtitle" : "actionbar";
                plugin.getConfigManager().setPlayerDisplayPosition(player, newPos);
                player.sendMessage(ChatColor.GREEN + "状态显示位置已切换为: " + ChatColor.YELLOW + newPos);
                handleSettings(player);
                return true;
            case "select":
                if (args.length > 1) {
                    String selection = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                    plugin.getCliManager().handleChat(player, selection);
                }
                return true;
            case "exempt_anti_loop":
                plugin.getCliManager().handleChat(player, "/cli exempt_anti_loop");
                return true;
            case "retry":
                plugin.getCliManager().handleRetry(player);
                return true;
            case "stop":
                plugin.getCliManager().handleChat(player, "stop");
                return true;
            case "todo":
                plugin.getCliManager().openTodoBook(player);
                return true;
            case "gui":
            case "menu":
                plugin.getGuiManager().openSettingsMenu(player);
                return true;
            case "memory":
            case "mem":
                handleMemory(player, args);
                return true;
        }
        return true;
    }

    private void toggleCLIMode(Player player) {
        // 切换玩家的 CLI 模式
        plugin.getCliManager().toggleCLI(player);
    }

    private void handleReload(CommandSender sender, String[] args) {
        if (args.length == 1) {
            plugin.getConfigManager().loadConfig();
            plugin.getConfigManager().loadPlayerData();
            plugin.getWorkspaceIndexer().indexAll();
            plugin.getEulaManager().reload();
            sender.sendMessage(ColorUtil.translateCustomColors("§zFancyHelper§b§r §7> §f配置、玩家数据、工作区与 EULA 已重新加载。"));
        } else if (args.length == 2) {
            String target = args[1].toLowerCase();
            if (target.equals("workspace")) {
                plugin.getWorkspaceIndexer().indexAll();
                sender.sendMessage(ColorUtil.translateCustomColors("§zFancyHelper§b§r §7> §f工作区索引已重新加载。"));
            } else if (target.equals("config")) {
                plugin.getConfigManager().loadConfig();
                sender.sendMessage(ColorUtil.translateCustomColors("§zFancyHelper§b§r §7> §f配置文件已重新加载。"));
            } else if (target.equals("playerdata")) {
                plugin.getConfigManager().loadPlayerData();
                sender.sendMessage(ColorUtil.translateCustomColors("§zFancyHelper§b§r §7> §f玩家数据已重新加载。"));
            } else if (target.equals("eula")) {
                plugin.getEulaManager().reload();
                sender.sendMessage(ColorUtil.translateCustomColors("§zFancyHelper§b§r §7> §fEULA 文件已重新加载。"));
            } else if (target.equals("deeply") || target.equals("deep")) {
                handleDeepReload(sender);
            } else {
                sender.sendMessage(ColorUtil.translateCustomColors("§zFancyHelper§b§r §7> §f用法: /fancy reload [workspace|config|playerdata|eula|deeply]"));
            }
        }
    }

    /**
     * 深度重载：尽可能“卸载”当前插件实例，然后从 plugins 目录重新加载 jar 并启用。
     * 说明：Bukkit 并不官方支持真正的卸载/重载，这里做的是常见的 best-effort 热重载流程。
     */
    private void handleDeepReload(CommandSender sender) {
        PluginManager pluginManager = Bukkit.getPluginManager();

        // 不再调用 PlugMan，而是使用内置的热重载服务
        File libDir = new File(plugin.getDataFolder(), "lib");
        if (!libDir.exists()) {
            libDir.mkdirs();
        }
        File reloadServiceJar = new File(libDir, "FancyHelperReloadService.jar");

        if (!reloadServiceJar.exists()) {
            sender.sendMessage(ChatColor.RED + "Deep reload failed: Helper jar not found at " + reloadServiceJar.getAbsolutePath());
            return;
        }

        try {
            // 尝试加载或获取已加载的重载服务插件
            Plugin reloadPlugin = pluginManager.getPlugin("FancyHelperReloadService");
            if (reloadPlugin == null) {
                reloadPlugin = pluginManager.loadPlugin(reloadServiceJar);
            }

            if (reloadPlugin == null) {
                sender.sendMessage(ChatColor.RED + "Deep reload failed: Could not load FancyHelperReloadService.");
                return;
            }

            // 确保启用该插件以触发其内部的重载逻辑
            if (reloadPlugin.isEnabled()) {
                // 如果已启用，先禁用再启用以触发 onEnable
                pluginManager.disablePlugin(reloadPlugin);
            }
            pluginManager.enablePlugin(reloadPlugin);

            sender.sendMessage(ChatColor.GREEN + ColorUtil.translateCustomColors("§zFancyHelper§b§r §7> §f正在深度重载，可能需要20s左右的时间等待响应"));
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + ColorUtil.translateCustomColors("§zFancyHelper§b§r §7> §f启动重载服务时发生错误: " + e.getMessage()));
            e.printStackTrace();
            plugin.getCloudErrorReport().report(e);
        }
    }

    private void handleStatus(CommandSender sender) {
        sender.sendMessage(ChatColor.AQUA + "=== FancyHelper 状态 ===");
        sender.sendMessage(ChatColor.WHITE + "已索引命令: " + ChatColor.YELLOW + plugin.getWorkspaceIndexer().getIndexedCommands().size());
        sender.sendMessage(ChatColor.WHITE + "已索引预设: " + ChatColor.YELLOW + plugin.getWorkspaceIndexer().getIndexedPresets().size());
        sender.sendMessage(ChatColor.WHITE + "CLI 模式玩家: " + ChatColor.YELLOW + plugin.getCliManager().getActivePlayersCount());
        sender.sendMessage(ChatColor.WHITE + "插件版本: " + ChatColor.YELLOW + plugin.getDescription().getVersion());
        sender.sendMessage(ChatColor.AQUA + "=======================");
    }

    private void handleSettings(Player player) {
        player.sendMessage(ColorUtil.translateCustomColors("&8&m----------------------------------------"));
        player.sendMessage(ColorUtil.translateCustomColors("       &zFancyHelper &8| &7Settings"));
        player.sendMessage("");
        
        // 1. Mode Switch
        boolean isYolo = plugin.getCliManager().getSession(player.getUniqueId()) != null && 
                         plugin.getCliManager().getSession(player.getUniqueId()).getMode() == org.YanPl.model.DialogueSession.Mode.YOLO;
        
        TextComponent modeLine = new TextComponent(ColorUtil.translateCustomColors("&7  Mode: "));
        TextComponent modeVal = new TextComponent(ColorUtil.translateCustomColors(isYolo ? "&cYOLO" : "&aNormal"));
        modeLine.addExtra(modeVal);
        modeLine.addExtra("  ");
        
        TextComponent modeBtn = new TextComponent(ColorUtil.translateCustomColors("&8[ &7Switch &8]"));
        // modeBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cli " + (isYolo ? "normal" : "yolo")));
        modeBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(ChatColor.GRAY + "不可在此处更改")));
        modeLine.addExtra(modeBtn);
        player.spigot().sendMessage(modeLine);

        // 2. Display Position
        String displayPos = plugin.getConfigManager().getPlayerDisplayPosition(player);
        TextComponent posLine = new TextComponent(ColorUtil.translateCustomColors("&7  Display: &f" + displayPos + "  "));
        TextComponent posBtn = new TextComponent(ColorUtil.translateCustomColors("&8[ &7Switch &8]"));
        posBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cli display"));
        posBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(ChatColor.GRAY + "点击切换状态显示位置 (actionbar/subtitle)")));
        posLine.addExtra(posBtn);
        player.spigot().sendMessage(posLine);
        
        player.sendMessage("");

        // 3. Management Buttons
        TextComponent toolsLine = new TextComponent("  ");
        
        TextComponent toolsBtn = new TextComponent(ColorUtil.translateCustomColors("&8[ &6Tools &8]"));
        toolsBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cli tools"));
        toolsBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(ChatColor.GRAY + "点击管理文件工具权限")));
        
        TextComponent memBtn = new TextComponent(ColorUtil.translateCustomColors("&8[ &bMemory &8]"));
        memBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cli memory"));
        memBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(ChatColor.GRAY + "点击管理持久化记忆")));
        
        toolsLine.addExtra(toolsBtn);
        toolsLine.addExtra(new TextComponent(" "));
        toolsLine.addExtra(memBtn);
        player.spigot().sendMessage(toolsLine);
        
        player.sendMessage("");
        
        // 4. GUI Button (Hidden as requested)
        /*
        TextComponent guiLine = new TextComponent("      ");
        TextComponent guiBtn = new TextComponent(ColorUtil.translateCustomColors("&z[OPEN GUI MENU]"));
        guiBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cli gui"));
        guiBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(ChatColor.GRAY + "Open Inventory Menu")));
        guiLine.addExtra(guiBtn);
        player.spigot().sendMessage(guiLine);
        */
        
        player.sendMessage(ColorUtil.translateCustomColors("&8&m----------------------------------------"));
    }

    private void handleTools(Player player) {
        player.sendMessage(ColorUtil.translateCustomColors("&8&m----------------------------------------"));
        player.sendMessage(ColorUtil.translateCustomColors("       &zFancyHelper &8| &7Tool Permissions"));
        player.sendMessage("");
        
        // LS Tool
        sendToolLine(player, "ls", "列出目录(ls)", plugin.getConfigManager().isPlayerToolEnabled(player, "ls"));
        
        // READ Tool
        sendToolLine(player, "read", "读取文件(read)", plugin.getConfigManager().isPlayerToolEnabled(player, "read"));
        
        // DIFF Tool
        sendToolLine(player, "diff", "编辑文件(diff)", plugin.getConfigManager().isPlayerToolEnabled(player, "diff"));
        
        player.sendMessage("");
        player.sendMessage(ChatColor.GRAY + "  点击按钮切换工具权限。");
        player.sendMessage(ChatColor.GRAY + "  重新启用需要验证。");
        
        player.sendMessage("");
        TextComponent backBtn = new TextComponent(ColorUtil.translateCustomColors("&8[ &7<< Back &8]"));
        backBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cli settings"));
        backBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(ChatColor.GRAY + "Return to main settings")));
        player.spigot().sendMessage(backBtn);
        
        player.sendMessage(ColorUtil.translateCustomColors("&8&m----------------------------------------"));
    }
    
    private void sendToolLine(Player player, String tool, String description, boolean enabled) {
        TextComponent line = new TextComponent(ColorUtil.translateCustomColors("&7  " + description + ": "));
        
        TextComponent statusBtn;
        if (enabled) {
            statusBtn = new TextComponent(ColorUtil.translateCustomColors("&8[ &aEnabled &8]"));
            statusBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(ChatColor.RED + "点击禁用 " + tool)));
        } else {
            statusBtn = new TextComponent(ColorUtil.translateCustomColors("&8[ &cDisabled &8]"));
            statusBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(ChatColor.GREEN + "点击启用 " + tool + " (需要验证)")));
        }
        statusBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cli toggle " + tool));
        
        line.addExtra(statusBtn);
        player.spigot().sendMessage(line);
    }

    private void handleMemory(Player player, String[] args) {
        if (args.length == 1) {
            showMemoryList(player);
        } else if (args.length >= 2) {
            String action = args[1].toLowerCase();
            switch (action) {
                case "add":
                case "new":
                    if (args.length >= 3) {
                        String content = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                        handleMemoryAdd(player, content);
                    } else {
                        player.sendMessage(ChatColor.RED + "用法: /cli memory add <内容> 或 /cli memory add <分类>|<内容>");
                    }
                    break;
                case "del":
                case "delete":
                case "remove":
                    if (args.length >= 3) {
                        try {
                            int index = Integer.parseInt(args[2]);
                            handleMemoryDelete(player, index);
                        } catch (NumberFormatException e) {
                            player.sendMessage(ChatColor.RED + "请输入有效的序号");
                        }
                    } else {
                        player.sendMessage(ChatColor.RED + "用法: /cli memory del <序号>");
                    }
                    break;
                case "edit":
                case "modify":
                    if (args.length >= 4) {
                        try {
                            int index = Integer.parseInt(args[2]);
                            String content = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
                            handleMemoryEdit(player, index, content);
                        } catch (NumberFormatException e) {
                            player.sendMessage(ChatColor.RED + "请输入有效的序号");
                        }
                    } else {
                        player.sendMessage(ChatColor.RED + "用法: /cli memory edit <序号> <新内容>");
                    }
                    break;
                case "clear":
                case "clearall":
                    handleMemoryClear(player);
                    break;
                default:
                    showMemoryList(player);
                    break;
            }
        }
    }

    private void showMemoryList(Player player) {
        java.util.List<InstructionManager.PlayerInstruction> instructions = 
            plugin.getInstructionManager().getInstructions(player.getUniqueId());
        
        player.sendMessage(ColorUtil.translateCustomColors("&8&m----------------------------------------"));
        player.sendMessage(ColorUtil.translateCustomColors("       &zFancyHelper &8| &7Memory Management"));
        player.sendMessage("");
        
        if (instructions.isEmpty()) {
            player.sendMessage(ChatColor.GRAY + "  暂无记忆记录。");
            player.sendMessage(ChatColor.GRAY + "  AI 将不会保留任何长期记忆。");
        } else {
            for (int i = 0; i < instructions.size(); i++) {
                InstructionManager.PlayerInstruction inst = instructions.get(i);
                
                // Memory Content Line
                TextComponent line = new TextComponent(ColorUtil.translateCustomColors("&8  " + (i + 1) + ". "));
                
                TextComponent category = new TextComponent(ColorUtil.translateCustomColors("&3" + inst.getCategory() + " &8| "));
                line.addExtra(category);
                
                TextComponent content = new TextComponent(ColorUtil.translateCustomColors("&7" + inst.getContent()));
                line.addExtra(content);
                
                player.spigot().sendMessage(line);
                
                // Action Buttons Line
                TextComponent actions = new TextComponent("     ");
                
                TextComponent editBtn = new TextComponent(ColorUtil.translateCustomColors("&8[ &eEdit &8]"));
                editBtn.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/cli memory edit " + (i + 1) + " " + inst.getContent()));
                editBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(ChatColor.GRAY + "点击修改此记忆")));
                actions.addExtra(editBtn);
                
                actions.addExtra(" ");
                
                TextComponent delBtn = new TextComponent(ColorUtil.translateCustomColors("&8[ &cDel &8]"));
                delBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cli memory del " + (i + 1)));
                delBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(ChatColor.GRAY + "点击删除此记忆")));
                actions.addExtra(delBtn);
                
                player.spigot().sendMessage(actions);
                player.sendMessage(""); // Spacer
            }
        }
        
        player.sendMessage("");
        
        // Bottom Buttons
        TextComponent bottomLine = new TextComponent("  ");
        
        TextComponent addBtn = new TextComponent(ColorUtil.translateCustomColors("&8[ &aAdd Memory &8]"));
        addBtn.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/cli memory add "));
        addBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(ChatColor.GRAY + "点击添加新记忆\n格式: 内容 或 分类|内容")));
        bottomLine.addExtra(addBtn);
        
        if (!instructions.isEmpty()) {
            bottomLine.addExtra("     ");
            TextComponent clearBtn = new TextComponent(ColorUtil.translateCustomColors("&8[ &cClear All &8]"));
            clearBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cli memory clear"));
            clearBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(ChatColor.RED + "点击清空所有记忆")));
            bottomLine.addExtra(clearBtn);
        }
        
        player.spigot().sendMessage(bottomLine);
        
        player.sendMessage("");
        TextComponent backBtn = new TextComponent(ColorUtil.translateCustomColors("&8[ &7<< Back &8]"));
        backBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cli settings"));
        backBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(ChatColor.GRAY + "Return to main settings")));
        player.spigot().sendMessage(backBtn);
        
        player.sendMessage(ColorUtil.translateCustomColors("&8&m----------------------------------------"));
    }

    private void handleMemoryAdd(Player player, String content) {
        String category = "general";
        String memoryContent = content;
        
        if (content.contains("|")) {
            String[] parts = content.split("\\|", 2);
            if (parts.length == 2) {
                category = parts[0].trim();
                memoryContent = parts[1].trim();
            }
        }
        
        if (memoryContent.isEmpty()) {
            player.sendMessage(ColorUtil.translateCustomColors("§zFancyHelper§b§r §7> §c记忆内容不能为空"));
            return;
        }
        
        String result = plugin.getInstructionManager().addInstruction(player, memoryContent, category);
        if (result.startsWith("success")) {
            player.sendMessage(ColorUtil.translateCustomColors("§zFancyHelper§b§r §7> §a已添加记忆: " + memoryContent));
        } else {
            player.sendMessage(ColorUtil.translateCustomColors("§zFancyHelper§b§r §7> §c" + result));
        }
        showMemoryList(player);
    }

    private void handleMemoryDelete(Player player, int index) {
        String result = plugin.getInstructionManager().removeInstruction(player, index);
        if (result.startsWith("success")) {
            player.sendMessage(ColorUtil.translateCustomColors("§zFancyHelper§b§r §7> §e已删除第 " + index + " 条记忆"));
        } else {
            player.sendMessage(ColorUtil.translateCustomColors("§zFancyHelper§b§r §7> §c" + result));
        }
        showMemoryList(player);
    }

    private void handleMemoryEdit(Player player, int index, String content) {
        String category = "general";
        String memoryContent = content;
        
        if (content.contains("|")) {
            String[] parts = content.split("\\|", 2);
            if (parts.length == 2) {
                category = parts[0].trim();
                memoryContent = parts[1].trim();
            }
        }
        
        if (memoryContent.isEmpty()) {
            player.sendMessage(ColorUtil.translateCustomColors("§zFancyHelper§b§r §7> §c记忆内容不能为空"));
            return;
        }
        
        String result = plugin.getInstructionManager().updateInstruction(player, index, memoryContent, category);
        if (result.startsWith("success")) {
            player.sendMessage(ColorUtil.translateCustomColors("§zFancyHelper§b§r §7> §a已修改第 " + index + " 条记忆"));
        } else {
            player.sendMessage(ColorUtil.translateCustomColors("§zFancyHelper§b§r §7> §c" + result));
        }
        showMemoryList(player);
    }

    private void handleMemoryClear(Player player) {
        String result = plugin.getInstructionManager().clearInstructions(player);
        if (result.startsWith("success")) {
            player.sendMessage(ColorUtil.translateCustomColors("§zFancyHelper§b§r §7> §e已清空所有记忆"));
        } else {
            player.sendMessage(ColorUtil.translateCustomColors("§zFancyHelper§b§r §7> §c" + result));
        }
        showMemoryList(player);
    }



    private void handleNotice(CommandSender sender) {
        sender.sendMessage("§zFancyHelper§b§r §7> §f正在获取公告...");
        
        plugin.getNoticeManager().fetchNoticeAsync().thenAccept(noticeData -> {
            if (noticeData == null || !noticeData.enabled) {
                sender.sendMessage(ChatColor.GRAY + "§zFancyHelper§b§r §7> §f当前没有可显示的公告。");
                return;
            }
            
            if (sender instanceof Player) {
                plugin.getNoticeManager().showNoticeToPlayer((Player) sender, noticeData);
            } else {
                plugin.getNoticeManager().showNoticeToConsole(noticeData);
            }
        });
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subCommands = new ArrayList<>(Arrays.asList(
                "reload", "status", "yolo", "normal", "checkupdate", "upgrade", 
                "read", "set", "settings", "tools", "display", "toggle", 
                "notice", "retry", "todo", "memory", "mem", "confirm", 
                "cancel", "agree", "thought", "select", "exempt_anti_loop", 
                "stop", "download", "help"
            ));
            return subCommands.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("reload")) {
            return Arrays.asList("workspace", "config", "eula", "deeply").stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("toggle")) {
            return Arrays.asList("ls", "read", "diff").stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("notice")) {
            return Arrays.asList("read").stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("memory") || args[0].equalsIgnoreCase("mem"))) {
            return Arrays.asList("add", "del", "edit", "clear").stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
