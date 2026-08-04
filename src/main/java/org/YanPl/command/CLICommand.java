package org.YanPl.command;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.YanPl.FancyHelper;
import org.YanPl.manager.InstructionManager;
import org.YanPl.manager.StatsManager;
import org.YanPl.model.DialogueSession;
import org.YanPl.model.SessionRecord;
import org.YanPl.util.ColorUtil;
import org.YanPl.util.I18n;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * /fancyhelper 命令的处理器。
 * 提供切换 CLI、重载、查看状态等子命令, 并支持 Tab 完成。
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
                sender.sendMessage(I18n.t("cli.only.player"));
                return true;
            }

            // Check registration status
            if (!plugin.getFancyConsoleManager().isReady() && !isAnyByokConfigured()) {
                showRegistrationPrompt((Player) sender);
                return true;
            }

            Player player = (Player) sender;
            if (!player.hasPermission("fancyhelper.cli")) {
                player.sendMessage(I18n.t("cli.no.perm.cmd"));
                return true;
            }

            // AI 提供商非 Fancy（BYOK）时也能进入 CLI；但若搜索/网页抓取服务走 Fancy 且未绑定，进入时提醒
            if (plugin.getCliManager().getSession(player.getUniqueId()) == null
                    && !plugin.getFancyConsoleManager().hasApiKey()
                    && (plugin.getConfigManager().isFancyConsoleSearch() || plugin.getConfigManager().isFancyConsoleJina())) {
                showFancyServiceBindWarning(player);
            }

            toggleCLIMode(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();
        switch (subCommand) {
            case "bind":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(I18n.t("cli.only.player"));
                    return true;
                }
                return handleBind((Player) sender, args);
            case "reload":
                if (!sender.hasPermission("fancyhelper.reload")) {
                    sender.sendMessage(I18n.t("cli.no.perm.reload"));
                    return true;
                }
                handleReload(sender, args);
                break;
            case "status":
                handleStatus(sender);
                break;
            case "stats":
                handleStatsCommand(sender);
                break;
            case "update":
            case "checkupdate":
                sender.sendMessage(I18n.t("cli.checking.update"));
                plugin.getUpdateManager().checkForUpdates(sender instanceof Player ? (Player) sender : null);
                break;
            case "notice":
                if (!sender.hasPermission("fancyhelper.notice")) {
                    sender.sendMessage(I18n.t("cli.no.perm.notice"));
                    return true;
                }
                if (args.length > 1 && args[1].equalsIgnoreCase("read")) {
                    if (!(sender instanceof Player)) {
                        sender.sendMessage(I18n.t("cli.only.player.sub"));
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
                    sender.sendMessage(I18n.t("cli.no.perm.update"));
                    return true;
                }
                plugin.getUpdateManager().downloadAndInstall(sender instanceof Player ? (Player) sender : null, true);
                break;
            case "yolo":
            case "normal":
            case "smart":
            case "plan":
            case "plan_clear_y":
            case "plan_clear_n":
            case "plan_start":
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
            case "streaming":
            case "select":
            case "other":
            case "exempt_anti_loop":
            case "todo":
            case "gui":
            case "menu":
            case "retry":
            case "stop":
            case "exit":
            case "memory":
            case "mem":
            case "smart_allow":
            case "smart_deny":
            case "smart_never":
            case "compact":
            case "resume":
            case "resume_confirm":
            case "resume_delete":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(I18n.t("cli.only.player.sub"));
                    return true;
                }
                Player player = (Player) sender;
                return handlePlayerSubCommand(player, subCommand, args);
            case "sound":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(I18n.t("cli.only.player.sub"));
                    return true;
                }
                player = (Player) sender;
                return handlePlayerSubCommand(player, subCommand, args);
            case "skill":
                if (!sender.hasPermission("fancyhelper.skill.use")) {
                    sender.sendMessage(I18n.t("cli.no.perm.skill"));
                    return true;
                }
                return handleSkillCommand(sender, args);
            case "mcp":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(I18n.t("cli.only.player"));
                    return true;
                }
                return handleMcpCommand((Player) sender, args);
            case "help":
                sendHelp(sender);
                return true;
            case "permission":
                handlePermissionCommand(sender, args);
                break;
            case "lib":
                handleLibCommand(sender, args);
                break;
            default:
                sendHelp(sender);
                break;
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(I18n.t("cli.help.title"));
        sender.sendMessage(I18n.t("cli.help.toggle"));
        sender.sendMessage(I18n.t("cli.help.bind"));
        sender.sendMessage(I18n.t("cli.help.reload"));
        sender.sendMessage(I18n.t("cli.help.reload.deep"));
        sender.sendMessage(I18n.t("cli.help.status"));
        sender.sendMessage(I18n.t("cli.help.stats"));
        sender.sendMessage(I18n.t("cli.help.checkupdate"));
        sender.sendMessage(I18n.t("cli.help.upgrade"));
        sender.sendMessage(I18n.t("cli.help.notice"));
        sender.sendMessage(I18n.t("cli.help.notice.read"));
        sender.sendMessage(I18n.t("cli.help.todo"));
        sender.sendMessage(I18n.t("cli.help.settings"));
        sender.sendMessage(I18n.t("cli.help.memory"));
        sender.sendMessage(I18n.t("cli.help.tools"));
        sender.sendMessage(I18n.t("cli.help.toggle.tools"));
        sender.sendMessage(I18n.t("cli.help.display"));
        sender.sendMessage(I18n.t("cli.help.yolo"));
        sender.sendMessage(I18n.t("cli.help.smart"));
        sender.sendMessage(I18n.t("cli.help.normal"));
        sender.sendMessage(I18n.t("cli.help.retry"));
        sender.sendMessage(I18n.t("cli.help.stop"));
        sender.sendMessage(I18n.t("cli.help.exit"));
        sender.sendMessage(I18n.t("cli.help.compact"));
        sender.sendMessage(I18n.t("cli.help.streaming"));
        sender.sendMessage(I18n.t("cli.help.sound"));
        sender.sendMessage(I18n.t("cli.help.resume"));
        sender.sendMessage(I18n.t("cli.help.skill"));
        sender.sendMessage(I18n.t("cli.help.skill.list"));
        sender.sendMessage(I18n.t("cli.help.skill.info"));
        sender.sendMessage(I18n.t("cli.help.skill.load"));
        sender.sendMessage(I18n.t("cli.help.mcp.tools"));
        sender.sendMessage(I18n.t("cli.help.mcp.tools.server"));
        sender.sendMessage(I18n.t("cli.help.mcp.toggle"));
    }

    private boolean handlePlayerSubCommand(Player player, String subCommand, String[] args) {
        switch (subCommand) {
            case "yolo":
                plugin.getCliManager().switchMode(player, DialogueSession.Mode.YOLO);
                return true;
            case "normal":
                plugin.getCliManager().switchMode(player, DialogueSession.Mode.NORMAL);
                return true;
            case "smart":
                plugin.getCliManager().switchMode(player, DialogueSession.Mode.SMART);
                return true;
            case "plan":
                plugin.getCliManager().enterPlanMode(player);
                return true;
            case "plan_clear_y":
                plugin.getCliManager().handlePlanClearY(player);
                return true;
            case "plan_clear_n":
                plugin.getCliManager().handlePlanClearN(player);
                return true;
            case "plan_start":
                if (args.length > 1) {
                    plugin.getCliManager().handlePlanStartMode(player, args[1]);
                }
                return true;
            case "confirm":
                plugin.getCliManager().handleConfirm(player);
                return true;
            case "cancel":
                plugin.getCliManager().handleCancel(player);
                return true;
            case "smart_allow":
                plugin.getCliManager().handleSmartAllow(player);
                return true;
            case "smart_deny":
                plugin.getCliManager().handleSmartDeny(player);
                return true;
            case "smart_never":
                plugin.getCliManager().handleSmartNever(player);
                return true;
            case "agree":
                plugin.getCliManager().handleChat(player, "agree");
                return true;
            case "read":
                plugin.getCliManager().openEulaUrl(player);
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
                    // 接受新分组名 (read/write) 和旧版独立名 (ls/edit) 的兼容
                    if (tool.equals("read") || tool.equals("write") || tool.equals("ls") || tool.equals("edit")) {
                        boolean currentState = plugin.getConfigManager().isPlayerToolEnabled(player, tool);
                        if (currentState) {
                            plugin.getConfigManager().setPlayerToolEnabled(player, tool, false);
                            player.sendMessage(I18n.t("cli.tool.disabled", tool));
                            handleTools(player);
                        } else {
                            player.sendMessage(I18n.t("cli.tool.verifying", tool));
                            plugin.getVerificationManager().startVerification(player, tool, () -> {
                                plugin.getConfigManager().setPlayerToolEnabled(player, tool, true);
                                player.sendMessage(I18n.t("cli.tool.verified", tool));
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
                player.sendMessage(I18n.t("cli.display.switched", newPos));
                handleSettings(player);
                return true;
            case "streaming":
                boolean currentStreaming = plugin.getConfigManager().isPlayerStreamingEnabled(player);
                plugin.getConfigManager().setPlayerStreamingEnabled(player, !currentStreaming);
                player.sendMessage(I18n.t(!currentStreaming ? "cli.streaming.on" : "cli.streaming.off"));
                handleSettings(player);
                return true;
            case "select":
                if (args.length > 1) {
                    String selection = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                    plugin.getCliManager().handleChat(player, selection);
                }
                return true;
            case "other":
                player.sendMessage(I18n.t("cli.other.chat"));
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
            case "exit":
                plugin.getCliManager().exitCLI(player);
                return true;
            case "todo":
                plugin.getCliManager().openTodoBook(player);
                return true;
            case "gui":
            case "menu":
                if (args.length > 1) {
                    String subMenu = args[1];
                    if ("mode".equalsIgnoreCase(subMenu)) {
                        plugin.getGuiManager().openModeSelectionMenu(player);
                    } else {
                        plugin.getGuiManager().openSettingsMenu(player);
                    }
                } else {
                    plugin.getGuiManager().openSettingsMenu(player);
                }
                return true;
            case "memory":
            case "mem":
                handleMemory(player, args);
                return true;
            case "compact":
                plugin.getCliManager().compactContext(player);
                return true;
            case "resume":
                int resumePage = 0;
                if (args.length > 1) {
                    try { resumePage = Integer.parseInt(args[1]) - 1; } catch (NumberFormatException e) {}
                }
                showSessionList(player, resumePage);
                return true;
            case "resume_confirm":
                if (args.length > 1) {
                    plugin.getCliManager().resumeSession(player, args[1]);
                }
                return true;
            case "resume_delete":
                if (args.length > 1) {
                    int returnPage = 0;
                    if (args.length > 2) {
                        try { returnPage = Integer.parseInt(args[2]) - 1; } catch (NumberFormatException e) {}
                    }
                    handleSessionDelete(player, args[1], returnPage);
                }
                return true;
            case "sound":
                boolean disabled = plugin.getConfigManager().isPlayerSoundDisabled(player.getUniqueId());
                plugin.getConfigManager().setPlayerSoundDisabled(player.getUniqueId(), !disabled);
                player.sendMessage(I18n.t(disabled ? "cli.sound.on" : "cli.sound.off"));
                handleSettings(player);
                return true;
        }
        return true;
    }

    // ============================================================
    //  FancyConsole 绑定 / 注册流程
    // ============================================================

    /**
     * 检查是否有可用的 BYOK 配置（OpenAI 等直连模式）
     */
    private boolean isAnyByokConfigured() {
        String provider = plugin.getConfigManager().getProvider();
        if ("openai".equals(provider)) {
            String key = plugin.getConfigManager().getOpenAiApiKey();
            if (key != null && !key.isEmpty() && !"your-openai-api-key".equals(key)) return true;
        }
        if ("cloudflare".equals(provider)) {
            String key = plugin.getConfigManager().getCloudflareCfKey();
            if (key != null && !key.isEmpty()) return true;
        }
        return false;
    }

    /**
     * 显示注册引导提示
     */
    private void showRegistrationPrompt(Player player) {
        String regUrl = plugin.getFancyConsoleManager().getRegistrationUrl();
        String serverId = plugin.getFancyConsoleManager().getServerId();

        player.sendMessage("");
        player.sendMessage(I18n.t("cli.reg.no.key"));
        player.sendMessage(I18n.t("cli.reg.hint"));

        net.md_5.bungee.api.chat.TextComponent link = new net.md_5.bungee.api.chat.TextComponent(
                net.md_5.bungee.api.chat.TextComponent.fromLegacyText(
                        I18n.t("cli.reg.click")));
        link.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
                net.md_5.bungee.api.chat.ClickEvent.Action.OPEN_URL, regUrl));
        link.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(
                net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                new net.md_5.bungee.api.chat.hover.content.Text(I18n.t("cli.reg.hover"))));
        player.spigot().sendMessage(link);

        player.sendMessage(I18n.t("cli.reg.after"));
        player.sendMessage(I18n.t("cli.reg.bind.cmd"));
        player.sendMessage(I18n.t("cli.reg.server.id", serverId));
        player.sendMessage("");
    }

    /**
     * 展示搜索/网页抓取服务走 Fancy 但未完成绑定的提醒（不阻止进入 CLI）
     */
    private void showFancyServiceBindWarning(Player player) {
        boolean searchFancy = plugin.getConfigManager().isFancyConsoleSearch();
        boolean jinaFancy = plugin.getConfigManager().isFancyConsoleJina();

        String servicePart;
        if (searchFancy && jinaFancy) {
            servicePart = I18n.t("cli.service.both");
        } else if (searchFancy) {
            servicePart = I18n.t("cli.service.search");
        } else {
            servicePart = I18n.t("cli.service.jina");
        }

        TextComponent msg = new TextComponent(TextComponent.fromLegacyText(
                I18n.t("cli.fancy.warn", servicePart)));

        TextComponent link = new TextComponent(TextComponent.fromLegacyText(I18n.t("cli.fancy.here")));
        link.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, plugin.getFancyConsoleManager().getRegistrationUrl()));
        link.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(I18n.t("cli.fancy.hover"))));

        msg.addExtra(link);
        msg.addExtra(new TextComponent(TextComponent.fromLegacyText(I18n.t("cli.fancy.done"))));
        player.spigot().sendMessage(msg);
    }

    /**
     * 处理 /cli bind <key> 绑定 API Key
     */
    private boolean handleBind(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(I18n.t("cli.bind.usage"));
            // 仅当尚未绑定（API Key 为空）时才展示注册引导
            if (!plugin.getFancyConsoleManager().hasApiKey()) {
                showRegistrationPrompt(player);
            }
            return true;
        }

        String apiKey = args[1].trim();
        if (apiKey.isEmpty()) {
            player.sendMessage(I18n.t("cli.bind.empty"));
            return true;
        }

        player.sendMessage(I18n.t("cli.bind.verifying"));

        org.YanPl.manager.FancyConsoleManager.ValidateKeyResult result =
                plugin.getFancyConsoleManager().validateKey(apiKey);

        if (result.valid) {
            plugin.getFancyConsoleManager().setApiKey(apiKey);
            player.sendMessage(I18n.t("cli.bind.success"));
            if (result.email != null && !result.email.isEmpty()) {
                player.sendMessage(I18n.t("cli.bind.account", result.email));
            }
            if (result.tier != null && !result.tier.isEmpty()) {
                player.sendMessage(I18n.t("cli.bind.tier", result.tier));
            }
            player.sendMessage(I18n.t("cli.bind.now.use"));
        } else {
            String errorMsg = result.error != null ? result.error : I18n.t("cli.bind.failed");
            player.sendMessage(ColorUtil.translateCustomColors("§zFancyConsole§b§r §7> §c" + errorMsg));
            player.sendMessage(I18n.t("cli.bind.check"));
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
            plugin.getFancyConsoleManager().reload();
            plugin.getWorkspaceIndexer().indexAll();
            plugin.getEulaManager().reload();
            plugin.getSkillManager().reloadSkills();
            if (plugin.getMcpManager() != null) {
                plugin.getMcpManager().reload();
            }
            plugin.getStatsManager().onConfigReload();
            sender.sendMessage(I18n.t("cli.reload.all"));
        } else if (args.length == 2) {
            String target = args[1].toLowerCase();
            if (target.equals("workspace")) {
                plugin.getWorkspaceIndexer().indexAll();
                sender.sendMessage(I18n.t("cli.reload.workspace"));
            } else if (target.equals("config")) {
                plugin.getConfigManager().loadConfig();
                plugin.getStatsManager().onConfigReload();
                sender.sendMessage(I18n.t("cli.reload.config"));
            } else if (target.equals("playerdata")) {
                plugin.getConfigManager().loadPlayerData();
                sender.sendMessage(I18n.t("cli.reload.playerdata"));
            } else if (target.equals("eula")) {
                plugin.getEulaManager().reload();
                sender.sendMessage(I18n.t("cli.reload.eula"));
            } else if (target.equals("skill")) {
                plugin.getSkillManager().reloadSkills();
                sender.sendMessage(I18n.t("cli.reload.skill", plugin.getSkillManager().getSkillCount()));
            } else if (target.equals("mcp")) {
                if (plugin.getMcpManager() != null) {
                    plugin.getMcpManager().reload();
                    sender.sendMessage(I18n.t("cli.reload.mcp"));
                } else {
                    sender.sendMessage(I18n.t("cli.reload.mcp.uninit"));
                }
            } else if (target.equals("deeply") || target.equals("deep")) {
                handleDeepReload(sender);
            } else {
                sender.sendMessage(I18n.t("cli.reload.usage"));
            }
        }
    }

    /**
     * 深度重载:发送 RELOAD 信号给 ReloadService, 然后自卸载。
     * ReloadService 会等待本插件完全下线后清理 Paper 注册缓存并重新加载 JAR。
     */
    private void handleDeepReload(CommandSender sender) {
        try {
            if (plugin.signalReloadService("RELOAD", null)) {
                sender.sendMessage(I18n.t("cli.deep.reloading"));

                // 信号已发送, 立即在主线程自卸载
                Bukkit.getPluginManager().disablePlugin(plugin);
            } else {
                sender.sendMessage(I18n.t("cli.deep.fail"));
            }
        } catch (Exception e) {
            sender.sendMessage(I18n.t("cli.deep.error", e.getMessage()));
            plugin.getCloudErrorReport().report(e);
        }
    }

    private void handleStatus(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.AQUA + "=== FancyHelper 状态 ===");
            sender.sendMessage(ChatColor.WHITE + "已索引命令: " + ChatColor.YELLOW + plugin.getWorkspaceIndexer().getIndexedCommands().size());
            sender.sendMessage(ChatColor.WHITE + "已加载 Skills: " + ChatColor.YELLOW + plugin.getSkillManager().getSkillCount());
            sender.sendMessage(ChatColor.WHITE + "CLI 模式玩家: " + ChatColor.YELLOW + plugin.getCliManager().getActivePlayersCount());
            sender.sendMessage(ChatColor.WHITE + "插件版本: " + ChatColor.YELLOW + plugin.getDescription().getVersion());
            sender.sendMessage(ChatColor.AQUA + "=======================");
            return;
        }

        Player player = (Player) sender;

        player.sendMessage(ColorUtil.translateCustomColors("&8&m----------------------------------------"));
        player.sendMessage(ColorUtil.translateCustomColors("       &zFancyHelper &8| &7Status"));
        player.sendMessage("");

        // Version
        TextComponent versionLine = new TextComponent(ColorUtil.translateCustomColors("&7  Version: "));
        TextComponent versionVal = new TextComponent(ColorUtil.translateCustomColors("&f" + plugin.getDescription().getVersion()));
        versionVal.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(I18n.t("cli.status.hover.version"))));
        versionLine.addExtra(versionVal);
        player.spigot().sendMessage(versionLine);

        // Indexed Commands
        TextComponent cmdLine = new TextComponent(ColorUtil.translateCustomColors("&7  Indexed Commands: "));
        TextComponent cmdVal = new TextComponent(ColorUtil.translateCustomColors("&e" + plugin.getWorkspaceIndexer().getIndexedCommands().size()));
        cmdVal.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(I18n.t("cli.status.hover.commands"))));
        cmdLine.addExtra(cmdVal);
        player.spigot().sendMessage(cmdLine);

        // Indexed Skills
        TextComponent skillLine = new TextComponent(ColorUtil.translateCustomColors("&7  Loaded Skills: "));
        TextComponent skillVal = new TextComponent(ColorUtil.translateCustomColors("&e" + plugin.getSkillManager().getSkillCount()));
        skillVal.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(I18n.t("cli.status.hover.skills"))));
        skillLine.addExtra(skillVal);
        player.spigot().sendMessage(skillLine);

        // Active Players
        TextComponent playerLine = new TextComponent(ColorUtil.translateCustomColors("&7  Active CLI Players: "));
        TextComponent playerVal = new TextComponent(ColorUtil.translateCustomColors("&e" + plugin.getCliManager().getActivePlayersCount()));
        playerVal.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(I18n.t("cli.status.hover.players"))));
        playerLine.addExtra(playerVal);
        player.spigot().sendMessage(playerLine);

        player.sendMessage("");

        // Action Buttons
        TextComponent actionLine = new TextComponent("  ");

        TextComponent refreshBtn = new TextComponent(ColorUtil.translateCustomColors("&8[ &aRefresh &8]"));
        refreshBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cli status"));
        refreshBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(I18n.t("cli.status.refresh"))));

        TextComponent reloadBtn = new TextComponent(ColorUtil.translateCustomColors("&8[ &bReload &8]"));
        reloadBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cli reload"));
        reloadBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(I18n.t("cli.status.reload"))));

        TextComponent settingsBtn = new TextComponent(ColorUtil.translateCustomColors("&8[ &7Settings &8]"));
        settingsBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cli settings"));
        settingsBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(I18n.t("cli.status.settings"))));

        actionLine.addExtra(refreshBtn);
        actionLine.addExtra(new TextComponent(" "));
        actionLine.addExtra(reloadBtn);
        actionLine.addExtra(new TextComponent(" "));
        actionLine.addExtra(settingsBtn);
        player.spigot().sendMessage(actionLine);

        player.sendMessage(ColorUtil.translateCustomColors("&8&m----------------------------------------"));
    }

    /**
     * 手动触发一次统计数据上报（用于测试）
     */
    private void handleStatsCommand(CommandSender sender) {
        if (!plugin.getFancyConsoleManager().isReady()) {
            sender.sendMessage(I18n.t("cli.stats.no.key"));
            return;
        }

        sender.sendMessage(I18n.t("cli.stats.sending"));

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            StatsManager.StatsSnapshot snapshot = plugin.getStatsManager().buildSnapshot();
            boolean success = plugin.getFancyConsoleManager().reportStats(snapshot);

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (success) {
                    sender.sendMessage(I18n.t("cli.stats.success"));
                } else {
                    sender.sendMessage(I18n.t("cli.stats.fail"));
                }
            });
        });
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
        modeBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(I18n.t("cli.settings.mode.hover"))));
        modeLine.addExtra(modeBtn);
        player.spigot().sendMessage(modeLine);

        // 2. Display Position
        String displayPos = plugin.getConfigManager().getPlayerDisplayPosition(player);
        TextComponent posLine = new TextComponent(ColorUtil.translateCustomColors("&7  Display: &f" + displayPos + "  "));
        TextComponent posBtn = new TextComponent(ColorUtil.translateCustomColors("&8[ &7Switch &8]"));
        posBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cli display"));
        posBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(I18n.t("cli.settings.pos.hover"))));
        posLine.addExtra(posBtn);
        player.spigot().sendMessage(posLine);

        player.sendMessage("");

        // 3. Streaming Toggle
        boolean streaming = plugin.getConfigManager().isPlayerStreamingEnabled(player);
        TextComponent streamLine = new TextComponent(ColorUtil.translateCustomColors("&7  Streaming: "));
        TextComponent streamVal = new TextComponent(ColorUtil.translateCustomColors(streaming ? "&aEnabled" : "&cDisabled"));
        streamLine.addExtra(streamVal);
        streamLine.addExtra("  ");
        TextComponent streamBtn = new TextComponent(ColorUtil.translateCustomColors("&8[ &7Toggle &8]"));
        streamBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cli streaming"));
        streamBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(I18n.t("cli.settings.stream.hover"))));
        streamLine.addExtra(streamBtn);
        player.spigot().sendMessage(streamLine);

        player.sendMessage("");

        // 5. Sound Toggle
        boolean soundDisabled = plugin.getConfigManager().isPlayerSoundDisabled(player.getUniqueId());
        TextComponent soundLine = new TextComponent(ColorUtil.translateCustomColors("&7  Sound: "));
        TextComponent soundVal = new TextComponent(ColorUtil.translateCustomColors(soundDisabled ? "&cDisabled" : "&aEnabled"));
        soundLine.addExtra(soundVal);
        soundLine.addExtra("  ");
        TextComponent soundBtn = new TextComponent(ColorUtil.translateCustomColors("&8[ &7Toggle &8]"));
        soundBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cli sound"));
        soundBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(I18n.t("cli.settings.sound.hover"))));
        soundLine.addExtra(soundBtn);
        player.spigot().sendMessage(soundLine);

        player.sendMessage("");

        // 6. Management Buttons
        TextComponent toolsLine = new TextComponent("  ");
        
        TextComponent toolsBtn = new TextComponent(ColorUtil.translateCustomColors("&8[ &6Tools &8]"));
        toolsBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cli tools"));
        toolsBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(I18n.t("cli.settings.tools.hover"))));
        
        TextComponent memBtn = new TextComponent(ColorUtil.translateCustomColors("&8[ &bMemory &8]"));
        memBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cli memory"));
        memBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(I18n.t("cli.settings.mem.hover"))));
        
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

        // READ 权限（包含 #list #read）
        sendToolLine(player, "read", I18n.t("cli.tools.read"), plugin.getConfigManager().isPlayerToolEnabled(player, "read"));

        // WRITE 权限（包含 #edit #write，自动授予 read）
        sendToolLine(player, "write", I18n.t("cli.tools.write"), plugin.getConfigManager().isPlayerToolEnabled(player, "write"));

        player.sendMessage("");
        player.sendMessage(I18n.t("cli.tools.hint"));
        player.sendMessage(I18n.t("cli.tools.verify.hint"));
        
        player.sendMessage("");
        TextComponent backBtn = new TextComponent(ColorUtil.translateCustomColors("&8[ &7<< Back &8]"));
        backBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cli settings"));
        backBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(I18n.t("cli.tools.back.hover"))));
        player.spigot().sendMessage(backBtn);
        
        player.sendMessage(ColorUtil.translateCustomColors("&8&m----------------------------------------"));
    }
    
    private void sendToolLine(Player player, String tool, String description, boolean enabled) {
        TextComponent line = new TextComponent(ColorUtil.translateCustomColors("&7  " + description + ": "));
        
        TextComponent statusBtn;
        if (enabled) {
            statusBtn = new TextComponent(ColorUtil.translateCustomColors("&8[ &aEnabled &8]"));
            statusBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(I18n.t("cli.tools.disable.hover", tool))));
        } else {
            statusBtn = new TextComponent(ColorUtil.translateCustomColors("&8[ &cDisabled &8]"));
            statusBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(I18n.t("cli.tools.enable.hover", tool))));
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
                        player.sendMessage(I18n.t("cli.memory.add.usage"));
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
                            player.sendMessage(I18n.t("cli.memory.invalid.index"));
                        }
                    } else {
                        player.sendMessage(I18n.t("cli.memory.del.usage"));
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
                            player.sendMessage(I18n.t("cli.memory.invalid.index"));
                        }
                    } else {
                        player.sendMessage(I18n.t("cli.memory.edit.usage"));
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

    /**
     * 显示历史对话列表（分页）
     * @param page 0-indexed 页码
     */
    private void showSessionList(Player player, int page) {
        List<SessionRecord> records = plugin.getCliManager().getSessionHistory(player.getUniqueId());

        player.sendMessage(ColorUtil.translateCustomColors("&8&m----------------------------------------"));
        player.sendMessage(ColorUtil.translateCustomColors("       &zFancyHelper &8| &7Resume"));
        player.sendMessage("");

        if (records.isEmpty()) {
            player.sendMessage(I18n.t("cli.resume.empty"));
            player.sendMessage(I18n.t("cli.resume.empty.hint"));
        } else {
            int totalPages = Math.max(1, (int) Math.ceil(records.size() / 6.0));
            if (page < 0) page = 0;
            if (page >= totalPages) page = totalPages - 1;

            int startIdx = page * 6;
            int endIdx = Math.min(startIdx + 6, records.size());
            List<SessionRecord> pageRecords = records.subList(startIdx, endIdx);

            java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

            for (int i = 0; i < pageRecords.size(); i++) {
                SessionRecord record = pageRecords.get(i);
                String sessionUUID = record.getSessionUUID();
                String title = record.getTitle() != null ? record.getTitle() : I18n.t("cli.resume.no.title");
                String timeStr = java.time.Instant.ofEpochMilli(record.getTimestamp())
                        .atZone(java.time.ZoneId.systemDefault())
                        .format(formatter);

                // 检查是否是待删除状态
                String pendingDelete = plugin.getCliManager().getPendingDeleteSession(player.getUniqueId());
                boolean isPendingDelete = sessionUUID.equals(pendingDelete);

                // 标题行
                TextComponent line = new TextComponent("  ");

                TextComponent dot = new TextComponent(ChatColor.GRAY + "● ");
                line.addExtra(dot);

                TextComponent titleText = new TextComponent(ColorUtil.translateCustomColors("&f" + title));
                if (!isPendingDelete) {
                    // 点击恢复
                    titleText.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cli resume_confirm " + sessionUUID));
                    titleText.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(I18n.t("cli.resume.restore.hover"))));
                }
                line.addExtra(titleText);

                TextComponent timeText = new TextComponent(ColorUtil.translateCustomColors(" &8(" + timeStr + ")"));
                if (!isPendingDelete) {
                    timeText.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cli resume_confirm " + sessionUUID));
                    timeText.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(I18n.t("cli.resume.restore.hover"))));
                }
                line.addExtra(timeText);

                // 删除按钮
                line.addExtra("    ");

                if (isPendingDelete) {
                    TextComponent confirmDelBtn = new TextComponent(I18n.t("cli.resume.confirm.del"));
                    confirmDelBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cli resume_delete " + sessionUUID + " " + (page + 1)));
                    confirmDelBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(I18n.t("cli.resume.confirm.del.hover"))));
                    line.addExtra(confirmDelBtn);
                } else {
                    TextComponent delBtn = new TextComponent(ChatColor.RED + "✘");
                    delBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cli resume_delete " + sessionUUID + " " + (page + 1)));
                    delBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(I18n.t("cli.resume.del.hover"))));
                    line.addExtra(delBtn);
                }

                player.spigot().sendMessage(line);
                player.sendMessage(""); // 间距
            }

            // 分页导航
            if (totalPages > 1) {
                player.sendMessage("");
                TextComponent navLine = new TextComponent("  ");

                // 上一页按钮
                if (page > 0) {
                    TextComponent prevBtn = new TextComponent(I18n.t("cli.resume.prev"));
                    prevBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cli resume " + page)); // page 是1-indexed显示
                    prevBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(I18n.t("cli.resume.page.hover", page))));
                    navLine.addExtra(prevBtn);
                } else {
                    TextComponent prevBtn = new TextComponent(ColorUtil.translateCustomColors("&8[ &7◀ 上一页 &8]"));
                    navLine.addExtra(prevBtn);
                }

                navLine.addExtra(new TextComponent("    "));

                // 页码指示
                TextComponent pageInfo = new TextComponent(I18n.t("cli.resume.page", page + 1, totalPages));
                navLine.addExtra(pageInfo);

                navLine.addExtra(new TextComponent("    "));

                // 下一页按钮
                if (page < totalPages - 1) {
                    TextComponent nextBtn = new TextComponent(I18n.t("cli.resume.next"));
                    nextBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cli resume " + (page + 2))); // 1-indexed
                    nextBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(I18n.t("cli.resume.page.hover", page + 2))));
                    navLine.addExtra(nextBtn);
                } else {
                    TextComponent nextBtn = new TextComponent(ColorUtil.translateCustomColors("&8[ &7下一页 ▶ &8]"));
                    navLine.addExtra(nextBtn);
                }

                player.spigot().sendMessage(navLine);
            }
        }

        player.sendMessage(ColorUtil.translateCustomColors("&8&m----------------------------------------"));
    }

    /**
     * 处理会话删除（二次确认）
     */
    private void handleSessionDelete(Player player, String sessionUUID, int returnPage) {
        String pendingDelete = plugin.getCliManager().getPendingDeleteSession(player.getUniqueId());

        if (sessionUUID.equals(pendingDelete)) {
            // 二次确认，执行删除
            plugin.getCliManager().deleteSession(player.getUniqueId(), sessionUUID);
            plugin.getCliManager().clearPendingDeleteSession(player.getUniqueId());
            player.sendMessage(I18n.t("cli.resume.deleted"));
            // 刷新列表（保持当前页）
            showSessionList(player, returnPage);
        } else {
            // 设置待删除状态
            plugin.getCliManager().setPendingDeleteSession(player.getUniqueId(), sessionUUID);
            player.sendMessage(I18n.t("cli.resume.confirm.again"));
            // 刷新列表以显示确认按钮（保持当前页）
            showSessionList(player, returnPage);
        }
    }

    private void showMemoryList(Player player) {
        java.util.List<InstructionManager.PlayerInstruction> instructions = 
            plugin.getInstructionManager().getInstructions(player.getUniqueId());
        
        player.sendMessage(ColorUtil.translateCustomColors("&8&m----------------------------------------"));
        player.sendMessage(ColorUtil.translateCustomColors("       &zFancyHelper &8| &7Memory Management"));
        player.sendMessage("");
        
        if (instructions.isEmpty()) {
            player.sendMessage(I18n.t("cli.memory.empty"));
            player.sendMessage(I18n.t("cli.memory.empty.hint"));
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
                editBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(I18n.t("cli.memory.edit.hover"))));
                actions.addExtra(editBtn);
                
                actions.addExtra(" ");
                
                TextComponent delBtn = new TextComponent(ColorUtil.translateCustomColors("&8[ &cDel &8]"));
                delBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cli memory del " + (i + 1)));
                delBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(I18n.t("cli.memory.del.hover"))));
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
        addBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(I18n.t("cli.memory.add.hover"))));
        bottomLine.addExtra(addBtn);
        
        if (!instructions.isEmpty()) {
            bottomLine.addExtra("     ");
            TextComponent clearBtn = new TextComponent(ColorUtil.translateCustomColors("&8[ &cClear All &8]"));
            clearBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cli memory clear"));
            clearBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(I18n.t("cli.memory.clear.hover"))));
            bottomLine.addExtra(clearBtn);
        }
        
        player.spigot().sendMessage(bottomLine);
        
        player.sendMessage("");
        TextComponent backBtn = new TextComponent(ColorUtil.translateCustomColors("&8[ &7<< Back &8]"));
        backBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cli settings"));
        backBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(I18n.t("cli.tools.back.hover"))));
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
            player.sendMessage(I18n.t("cli.memory.empty.content"));
            return;
        }
        
        String result = plugin.getInstructionManager().addInstruction(player, memoryContent, category);
        if (result.startsWith("success")) {
            player.sendMessage(I18n.t("cli.memory.added", memoryContent));
        } else {
            player.sendMessage(ColorUtil.translateCustomColors("§zFancyHelper§b§r §7> §c" + result));
        }
        showMemoryList(player);
    }

    private void handleMemoryDelete(Player player, int index) {
        String result = plugin.getInstructionManager().removeInstruction(player, index);
        if (result.startsWith("success")) {
            player.sendMessage(I18n.t("cli.memory.deleted", index));
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
            player.sendMessage(I18n.t("cli.memory.empty.content"));
            return;
        }
        
        String result = plugin.getInstructionManager().updateInstruction(player, index, memoryContent, category);
        if (result.startsWith("success")) {
            player.sendMessage(I18n.t("cli.memory.edited", index));
        } else {
            player.sendMessage(ColorUtil.translateCustomColors("§zFancyHelper§b§r §7> §c" + result));
        }
        showMemoryList(player);
    }

    private void handleMemoryClear(Player player) {
        String result = plugin.getInstructionManager().clearInstructions(player);
        if (result.startsWith("success")) {
            player.sendMessage(I18n.t("cli.memory.cleared"));
        } else {
            player.sendMessage(ColorUtil.translateCustomColors("§zFancyHelper§b§r §7> §c" + result));
        }
        showMemoryList(player);
    }

    /**
     * 处理 Skill 子命令
     */
    private boolean handleSkillCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            // 显示 Skill 帮助
            sender.sendMessage(I18n.t("cli.skill.title"));
            sender.sendMessage(I18n.t("cli.help.skill.list"));
            sender.sendMessage(I18n.t("cli.help.skill.info"));
            sender.sendMessage(I18n.t("cli.help.skill.load"));
            if (sender.hasPermission("fancyhelper.skill.admin")) {
                sender.sendMessage(I18n.t("cli.help.skill.reload"));
                sender.sendMessage(I18n.t("cli.help.skill.checkupdate"));
                sender.sendMessage(I18n.t("cli.help.skill.upgrade"));
                sender.sendMessage(I18n.t("cli.help.skill.list-remote"));
                sender.sendMessage(I18n.t("cli.help.skill.install"));
            }
            return true;
        }

        String subCommand = args[1].toLowerCase();

        switch (subCommand) {
            case "list":
                handleSkillList(sender);
                break;
            case "info":
                if (args.length < 3) {
                    sender.sendMessage(I18n.t("cli.skill.need.id"));
                    sender.sendMessage(I18n.t("cli.skill.info.usage"));
                } else {
                    handleSkillInfo(sender, args[2]);
                }
                break;
            case "load":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(I18n.t("cli.only.player.sub"));
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(I18n.t("cli.skill.need.id"));
                    sender.sendMessage(I18n.t("cli.skill.load.usage"));
                } else {
                    handleSkillLoad((Player) sender, args[2]);
                }
                break;
            case "reload":
                if (!sender.hasPermission("fancyhelper.skill.admin")) {
                    sender.sendMessage(I18n.t("cli.skill.no.perm"));
                    return true;
                }
                plugin.getSkillManager().reloadSkills();
                sender.sendMessage(I18n.t("cli.skill.reloaded"));
                sender.sendMessage(I18n.t("cli.skill.reloaded.count", plugin.getSkillManager().getSkillCount()));
                break;
            case "checkupdate":
                if (!sender.hasPermission("fancyhelper.skill.admin")) {
                    sender.sendMessage(I18n.t("cli.skill.no.perm"));
                    return true;
                }
                plugin.getSkillUpdateManager().checkForUpdates(sender instanceof Player ? (Player) sender : null);
                break;
            case "upgrade":
                if (!sender.hasPermission("fancyhelper.skill.admin")) {
                    sender.sendMessage(I18n.t("cli.skill.no.perm"));
                    return true;
                }
                plugin.getSkillUpdateManager().downloadUpdates(sender instanceof Player ? (Player) sender : null);
                break;
            case "list-remote":
                if (!sender.hasPermission("fancyhelper.skill.admin")) {
                    sender.sendMessage(I18n.t("cli.skill.no.perm"));
                    return true;
                }
                handleSkillListRemote(sender);
                break;
            case "install":
                if (!sender.hasPermission("fancyhelper.skill.admin")) {
                    sender.sendMessage(I18n.t("cli.skill.no.perm"));
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(I18n.t("cli.skill.install.need.id"));
                    sender.sendMessage(I18n.t("cli.skill.install.usage"));
                } else {
                    handleSkillInstall(sender, args[2]);
                }
                break;
            default:
                sender.sendMessage(I18n.t("cli.skill.unknown.sub", subCommand));
                break;
        }

        return true;
    }

    /**
     * 处理 Skill 列表
     */
    private void handleSkillList(CommandSender sender) {
        List<String> lines = plugin.getSkillManager().getFormattedSkillList();
        for (String line : lines) {
            sender.sendMessage(ColorUtil.translateCustomColors(line));
        }
    }

    /**
     * 处理 Skill 详情
     */
    private void handleSkillInfo(CommandSender sender, String skillId) {
        org.YanPl.model.Skill skill = plugin.getSkillManager().getSkill(skillId);

        if (skill == null) {
            // 尝试搜索
            List<org.YanPl.model.Skill> matches = plugin.getSkillManager().searchSkills(skillId);
            if (matches.isEmpty()) {
                sender.sendMessage(I18n.t("cli.skill.not.found", skillId));
                return;
            }
            skill = matches.get(0);
        }

        List<String> infoLines = skill.getDetailedInfo();
        for (String line : infoLines) {
            sender.sendMessage(ColorUtil.translateCustomColors(line));
        }
    }

    /**
     * 处理加载 Skill
     */
    private void handleSkillLoad(Player player, String skillId) {
        // 检查是否在 CLI 模式
        if (!plugin.getCliManager().isInCLI(player)) {
            player.sendMessage(I18n.t("cli.skill.need.cli"));
            return;
        }

        org.YanPl.model.Skill skill = plugin.getSkillManager().getSkill(skillId);

        if (skill == null) {
            // 尝试搜索
            List<org.YanPl.model.Skill> matches = plugin.getSkillManager().searchSkills(skillId);
            if (matches.isEmpty()) {
                player.sendMessage(I18n.t("cli.skill.not.found", skillId));
                return;
            }
            skill = matches.get(0);
        }

        // 加载 Skill 到当前对话
        plugin.getSkillManager().loadSkillForPlayer(player, skill.getId());

        // 将 Skill 内容添加到对话上下文
        org.YanPl.model.DialogueSession session = plugin.getCliManager().getSession(player.getUniqueId());
        if (session != null) {
        }

        player.sendMessage(I18n.t("cli.skill.loaded", skill.getMetadata().getName()));
    }

    /**
     * 处理 list-remote 子命令：列出远程仓库中可安装的 Skill
     */
    private void handleSkillListRemote(CommandSender sender) {
        sender.sendMessage(I18n.t("cli.skill.fetching.remote"));

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Map<String, String> remote = plugin.getSkillUpdateManager().listRemoteSkills();

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (remote.isEmpty()) {
                    sender.sendMessage(I18n.t("cli.skill.remote.empty"));
                    return;
                }

                sender.sendMessage(I18n.t("cli.skill.remote.title"));
                for (Map.Entry<String, String> entry : remote.entrySet()) {
                    sender.sendMessage(" §7- §b" + entry.getKey() + " §7v" + entry.getValue());
                }
                sender.sendMessage(ColorUtil.translateCustomColors("§6========================================="));
                sender.sendMessage(I18n.t("cli.skill.remote.install"));
            });
        });
    }

    /**
     * 处理 install 子命令：安装远程 Skill
     */
    private void handleSkillInstall(CommandSender sender, String skillId) {
        if (plugin.getSkillManager().hasSkill(skillId)) {
            sender.sendMessage(I18n.t("cli.skill.installed", skillId));
            return;
        }

        sender.sendMessage(I18n.t("cli.skill.installing", skillId));

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean success = plugin.getSkillUpdateManager().installSkill(skillId, sender instanceof Player ? (Player) sender : null);
            if (!success) {
                // installSkill 已经发送了具体错误消息，这里只补充
                if (sender != null && !(sender instanceof Player)) {
                    sender.sendMessage(I18n.t("cli.skill.install.fail"));
                }
            }
        });
    }

    // ============================================================
    //  MCP 子命令
    // ============================================================

    private boolean handleMcpCommand(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(I18n.t("cli.mcp.title"));
            player.sendMessage(I18n.t("cli.help.mcp.tools"));
            player.sendMessage(I18n.t("cli.help.mcp.tools.server"));
            player.sendMessage(I18n.t("cli.help.mcp.toggle"));
            return true;
        }

        String subCommand = args[1].toLowerCase();
        switch (subCommand) {
            case "tools":
                if (args.length >= 3) {
                    handleMcpServerTools(player, args[2]);
                } else {
                    handleMcpToolsOverview(player);
                }
                break;
            case "toggle":
                if (args.length < 4) {
                    player.sendMessage(I18n.t("cli.mcp.toggle.usage"));
                } else {
                    handleMcpToggle(player, args[2], args[3]);
                }
                break;
            default:
                player.sendMessage(I18n.t("cli.mcp.unknown", subCommand));
                break;
        }
        return true;
    }

    private void handleMcpToolsOverview(Player player) {
        if (plugin.getMcpManager() == null || !plugin.getMcpManager().isEnabled()) {
            player.sendMessage(I18n.t("cli.mcp.not.enabled"));
            return;
        }

        List<org.YanPl.mcp.client.McpClientManager.ExternalToolInfo> allTools = plugin.getMcpManager().getAllToolsWithState();

        player.sendMessage(I18n.t("cli.mcp.tools.header"));

        if (allTools.isEmpty()) {
            player.sendMessage(I18n.t("cli.mcp.no.tools"));
        } else {
            // 按 serverName 分组
            Map<String, List<org.YanPl.mcp.client.McpClientManager.ExternalToolInfo>> grouped = new java.util.LinkedHashMap<>();
            for (org.YanPl.mcp.client.McpClientManager.ExternalToolInfo info : allTools) {
                grouped.computeIfAbsent(info.serverName, k -> new java.util.ArrayList<>()).add(info);
            }

            for (Map.Entry<String, List<org.YanPl.mcp.client.McpClientManager.ExternalToolInfo>> entry : grouped.entrySet()) {
                String serverName = entry.getKey();
                List<org.YanPl.mcp.client.McpClientManager.ExternalToolInfo> tools = entry.getValue();

                org.YanPl.mcp.client.McpClientManager.ExternalToolInfo first = tools.get(0);
                String status;
                if (!first.serverConnected) {
                    status = I18n.t("cli.mcp.status.disconnected");
                } else if (tools.isEmpty() || (tools.size() == 1 && first.tool == null)) {
                    status = I18n.t("cli.mcp.status.no.tools");
                } else {
                    long enabledCount = tools.stream().filter(t -> t.enabled).count();
                    status = I18n.t("cli.mcp.status.enabled", enabledCount, tools.size());
                }

                TextComponent line = new TextComponent("  " + ChatColor.WHITE + serverName + " " + status + "  ");
                String colorHex = ColorUtil.getColorZ();
                TextComponent viewBtn = new TextComponent(I18n.t("cli.mcp.view.tools"));
                viewBtn.setColor(net.md_5.bungee.api.ChatColor.of(colorHex));
                viewBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cli mcp tools " + serverName));
                viewBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(I18n.t("cli.mcp.view.hover", serverName))));
                line.addExtra(viewBtn);
                player.spigot().sendMessage(line);
            }
        }

        player.sendMessage(ChatColor.DARK_GRAY + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage(I18n.t("cli.mcp.hint"));
    }

    private void handleMcpServerTools(Player player, String serverName) {
        if (plugin.getMcpManager() == null || !plugin.getMcpManager().isEnabled()) {
            player.sendMessage(I18n.t("cli.mcp.not.enabled.short"));
            return;
        }

        List<org.YanPl.mcp.client.McpClientManager.ExternalToolInfo> allTools = plugin.getMcpManager().getAllToolsWithState();
        List<org.YanPl.mcp.client.McpClientManager.ExternalToolInfo> serverTools = new java.util.ArrayList<>();
        for (org.YanPl.mcp.client.McpClientManager.ExternalToolInfo info : allTools) {
            if (info.serverName.equalsIgnoreCase(serverName)) {
                serverTools.add(info);
            }
        }

        if (serverTools.isEmpty()) {
            player.sendMessage(I18n.t("cli.mcp.server.not.found", serverName));
            return;
        }

        org.YanPl.mcp.client.McpClientManager.ExternalToolInfo first = serverTools.get(0);
        String connStatus = first.serverConnected ? I18n.t("cli.mcp.connected") : I18n.t("cli.mcp.disconnected");

        player.sendMessage(I18n.t("cli.mcp.server.header", serverName));
        player.sendMessage(I18n.t("cli.mcp.conn.status", connStatus));

        int enabledCount = 0;
        for (org.YanPl.mcp.client.McpClientManager.ExternalToolInfo info : serverTools) {
            if (info.tool == null) continue;
            boolean e = info.enabled;
            if (e) enabledCount++;

            String icon = e ? ChatColor.GREEN + "☑" : ChatColor.GRAY + "☐";
            String toolName = e ? ChatColor.WHITE + info.tool.name : ChatColor.GRAY + info.tool.name;

            TextComponent line = new TextComponent(icon + " " + toolName + "   ");
            TextComponent toggleBtn = e
                ? new TextComponent(ChatColor.GRAY + "[" + ChatColor.RED + I18n.t("cli.mcp.disable") + ChatColor.GRAY + "]")
                : new TextComponent(ChatColor.GRAY + "[" + ChatColor.GREEN + I18n.t("cli.mcp.enable") + ChatColor.GRAY + "]");
            toggleBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cli mcp toggle " + serverName + " " + info.tool.name));
            toggleBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(e ? I18n.t("cli.mcp.disable.hover") : I18n.t("cli.mcp.enable.hover"))));
            line.addExtra(toggleBtn);

            player.spigot().sendMessage(line);

            if (info.tool.description != null && !info.tool.description.isEmpty()) {
                player.sendMessage(ChatColor.GRAY + "   " + info.tool.description);
            }
        }

        player.sendMessage(ChatColor.DARK_GRAY + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage(I18n.t("cli.mcp.enabled.summary",
                enabledCount, serverTools.stream().filter(t -> t.tool != null).count(), serverName, connStatus));
    }

    private void handleMcpToggle(Player player, String serverName, String toolName) {
        if (plugin.getMcpManager() == null || !plugin.getMcpManager().isEnabled()) {
            player.sendMessage(I18n.t("cli.mcp.not.enabled.short"));
            return;
        }

        if (plugin.getMcpManager().getClientManager() == null) {
            player.sendMessage(I18n.t("cli.mcp.manager.uninit"));
            return;
        }

        plugin.getMcpManager().getClientManager().toggleTool(serverName, toolName);
        boolean nowEnabled = plugin.getMcpManager().getClientManager().isToolEnabled(serverName, toolName);
        String status = nowEnabled ? I18n.t("cli.mcp.enabled") : I18n.t("cli.mcp.disabled");
        player.sendMessage(I18n.t("cli.mcp.toggled", serverName, toolName, status));

        // 刷新工具列表 UI
        handleMcpServerTools(player, serverName);
    }

    private void handleNotice(CommandSender sender) {
        sender.sendMessage(I18n.t("cli.notice.fetching"));

        plugin.getNoticeManager().fetchNoticeAsync().thenAccept(noticeData -> {
            if (noticeData == null || !noticeData.enabled) {
                sender.sendMessage(I18n.t("cli.notice.empty"));
                return;
            }
            
            if (sender instanceof Player) {
                plugin.getNoticeManager().showNoticeToPlayer((Player) sender, noticeData);
            } else {
                plugin.getNoticeManager().showNoticeToConsole(noticeData);
            }
        });
    }

    /**
     * 处理库管理命令, 如安装ProtocolLib
     * @param sender 命令发送者
     * @param args 命令参数
     */
    private void handleLibCommand(CommandSender sender, String[] args) {
        // 检查发送者是否有OP权限
        if (!(sender instanceof Player) || !sender.isOp()) {
            sender.sendMessage(I18n.t("cli.lib.need.op"));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(I18n.t("cli.lib.usage"));
            return;
        }

        String action = args[1].toLowerCase();
        if (!action.equals("install")) {
            sender.sendMessage(I18n.t("cli.lib.usage"));
            return;
        }

        if (args.length < 3) {
            sender.sendMessage(I18n.t("cli.lib.need.library"));
            return;
        }

        String library = args[2].toLowerCase();
        if (!library.equals("protocolib")) {
            sender.sendMessage(I18n.t("cli.lib.only.protocollib"));
            return;
        }

        // 检查是否已加载 ProtocolLib
        if (Bukkit.getPluginManager().getPlugin("ProtocolLib") != null) {
            sender.sendMessage(I18n.t("cli.lib.already"));
            return;
        }

        // 检查插件目录是否已有 ProtocolLib 文件
        File pluginsDir = plugin.getDataFolder().getParentFile();
        boolean protocolLibExists = false;
        if (pluginsDir.exists() && pluginsDir.isDirectory()) {
            File[] files = pluginsDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.getName().matches("(?i)ProtocolLib.*\\.jar")) {
                        protocolLibExists = true;
                        break;
                    }
                }
            }
        }

        if (protocolLibExists) {
            sender.sendMessage(I18n.t("cli.lib.exists"));
            return;
        }

        // 开始下载并安装 ProtocolLib
        sender.sendMessage(I18n.t("cli.lib.downloading"));

        // 异步执行下载操作
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // 下载 URL
                String downloadUrl = "https://fancy.baicaizhale.top/download/libs%2FProtocolLib.jar";
                // 目标文件路径
                File targetFile = new File(pluginsDir, "ProtocolLib.jar");

                // 下载文件
                java.net.URL url = new java.net.URL(downloadUrl);
                java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(30000);
                connection.setRequestMethod("GET");

                int responseCode = connection.getResponseCode();
                if (responseCode == 200) {
                    // 读取输入流并写入文件
                    try (java.io.InputStream in = connection.getInputStream();
                         java.io.FileOutputStream out = new java.io.FileOutputStream(targetFile)) {
                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = in.read(buffer)) != -1) {
                            out.write(buffer, 0, bytesRead);
                        }
                    }

                    // 下载完成, 提示用户
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        sender.sendMessage(I18n.t("cli.lib.done"));
                        sender.sendMessage(I18n.t("cli.lib.restart"));
                    });
                } else {
                    // 下载失败
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        sender.sendMessage(I18n.t("cli.lib.http.fail", responseCode));
                    });
                }
            } catch (Exception e) {
                // 处理异常
                Bukkit.getScheduler().runTask(plugin, () -> {
                    sender.sendMessage(I18n.t("cli.lib.fail", e.getMessage()));
                });
                e.printStackTrace();
            }
        });
    }

    /**
     * 处理 /fancy permission <ls|read|edit> <enable|disable> <playername>
     * 仅控制台可用，不提供 tab 补全。
     */
    private void handlePermissionCommand(CommandSender sender, String[] args) {
        if (sender instanceof Player) {
            sender.sendMessage(I18n.t("cli.only.console"));
            return;
        }

        if (args.length < 4) {
            sender.sendMessage(ChatColor.RED + "用法: /fancy permission <ls|read|edit> <enable|disable> <playername>");
            return;
        }

        String tool = args[1].toLowerCase();
        if (!tool.equals("ls") && !tool.equals("read") && !tool.equals("edit")) {
            sender.sendMessage(ChatColor.RED + "无效的工具类型: " + args[1] + " (可选: ls, read, edit)");
            return;
        }

        boolean enabled;
        String action = args[2].toLowerCase();
        if (action.equals("enable")) {
            enabled = true;
        } else if (action.equals("disable")) {
            enabled = false;
        } else {
            sender.sendMessage(ChatColor.RED + "无效的操作: " + args[2] + " (可选: enable, disable)");
            return;
        }

        String playerName = args[3];
        Player target = Bukkit.getPlayer(playerName);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "未找到在线玩家: " + playerName);
            return;
        }

        plugin.getConfigManager().setPlayerToolEnabled(target, tool, enabled);
        sender.sendMessage(ChatColor.GREEN + "已" + (enabled ? "启用" : "禁用") + "玩家 " + target.getName() + " 的 " + tool + " 工具权限。");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subCommands = new ArrayList<>(Arrays.asList(
                "bind", "reload", "status", "stats", "yolo", "normal", "smart", "plan", "checkupdate", "upgrade",
                "read", "set", "settings", "tools", "display", "streaming", "toggle",
                "notice", "retry", "todo", "memory", "mem", "confirm",
                "cancel", "agree", "thought", "select", "exempt_anti_loop",
                "stop", "exit", "download", "help", "lib", "compact", "skill", "sound", "resume"
            ));
            subCommands.add("mcp");
            return subCommands.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("reload")) {
            return Arrays.asList("workspace", "config", "playerdata", "eula", "skill", "mcp", "deeply").stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("toggle")) {
            return Arrays.asList("ls", "read", "edit").stream()
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
        } else if (args.length == 2 && args[0].equalsIgnoreCase("lib")) {
            return Arrays.asList("install").stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        } else if (args.length == 3 && args[0].equalsIgnoreCase("lib") && args[1].equalsIgnoreCase("install")) {
            return Arrays.asList("protocolib").stream()
                    .filter(s -> s.startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("plan_start")) {
            return Arrays.asList("normal", "smart", "yolo").stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("skill")) {
            List<String> skillSubCommands = new ArrayList<>(Arrays.asList("list", "info", "load"));
            if (sender.hasPermission("fancyhelper.skill.admin")) {
                skillSubCommands.add("reload");
                skillSubCommands.add("checkupdate");
                skillSubCommands.add("upgrade");
                skillSubCommands.add("list-remote");
                skillSubCommands.add("install");
            }
            return skillSubCommands.stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        } else if (args.length == 3 && args[0].equalsIgnoreCase("skill")) {
            if (args[1].equalsIgnoreCase("info") || args[1].equalsIgnoreCase("load")) {
                // 返回所有 Skill ID
                return plugin.getSkillManager().getSkillIdsForPrompt().stream()
                        .filter(s -> s.startsWith(args[2].toLowerCase()))
                        .collect(Collectors.toList());
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("mcp")) {
            return Arrays.asList("tools", "toggle").stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        } else if (args.length == 3 && args[0].equalsIgnoreCase("mcp")) {
            if (args[1].equalsIgnoreCase("tools") || args[1].equalsIgnoreCase("toggle")) {
                // 返回 MCP 服务器名称列表
                if (plugin.getMcpManager() != null && plugin.getMcpManager().getClientManager() != null) {
                    return plugin.getMcpManager().getClientManager().getClients().keySet().stream()
                            .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                            .collect(Collectors.toList());
                }
            }
        }
        return new ArrayList<>();
    }
}
