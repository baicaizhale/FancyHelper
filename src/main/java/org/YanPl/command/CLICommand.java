package org.YanPl.command;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.YanPl.FancyHelper;
import org.YanPl.model.DialogueSession;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

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
        // 命令入口：只允许玩家使用
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "该命令仅限玩家使用。");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
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
                if (!player.hasPermission("fancyhelper.reload")) {
                    player.sendMessage(ChatColor.RED + "你没有权限执行重载。");
                    return true;
                }
                handleReload(player, args);
                break;
            case "status":
                handleStatus(player);
                break;
            case "update":
            case "checkupdate":
                if (!player.hasPermission("fancyhelper.reload")) {
                    player.sendMessage(ChatColor.RED + "你没有权限检查更新。");
                    return true;
                }
                player.sendMessage(ChatColor.GOLD + "[FancyHelper] " + ChatColor.YELLOW + "正在检查更新...");
                plugin.getUpdateManager().checkForUpdates(player);
                break;
            case "upgrade":
            case "download":
                if (!player.hasPermission("fancyhelper.reload")) {
                    player.sendMessage(ChatColor.RED + "你没有权限执行更新。");
                    return true;
                }
                plugin.getUpdateManager().downloadAndInstall(player);
                break;
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
                            // 禁用工具无需验证
                            plugin.getConfigManager().setPlayerToolEnabled(player, tool, false);
                            player.sendMessage(ChatColor.YELLOW + "工具 " + tool + " 已禁用。下次开启需要重新验证。");
                            handleSettings(player);
                        } else {
                            // 启用工具需要验证
                            player.sendMessage(ChatColor.AQUA + "正在为工具 " + tool + " 发起安全验证...");
                            plugin.getVerificationManager().startVerification(player, tool, () -> {
                                plugin.getConfigManager().setPlayerToolEnabled(player, tool, true);
                                player.sendMessage(ChatColor.GREEN + "验证成功！工具 " + tool + " 已启用。");
                                handleSettings(player);
                            });
                        }
                    }
                }
                return true;
            case "error":
                if (!player.isOp()) {
                    player.sendMessage(ChatColor.RED + "该测试命令仅限管理员使用。");
                    return true;
                }
                handleTestError(player);
                return true;
            case "select":
                if (args.length > 1) {
                    String selection = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                    plugin.getCliManager().handleChat(player, selection);
                }
                return true;
            default:
                player.sendMessage(ChatColor.RED + "未知子命令。用法: /fancy [reload|status]");
                break;
        }

        return true;
    }

    private void toggleCLIMode(Player player) {
        // 切换玩家的 CLI 模式
        plugin.getCliManager().toggleCLI(player);
    }

    private void handleReload(Player player, String[] args) {
        if (args.length == 1) {
            plugin.getConfigManager().loadConfig();
            plugin.getWorkspaceIndexer().indexAll();
            plugin.getEulaManager().reload();
            player.sendMessage(ChatColor.GREEN + "配置、工作区与 EULA 已重新加载。");
        } else if (args.length == 2) {
            String target = args[1].toLowerCase();
            if (target.equals("workspace")) {
                plugin.getWorkspaceIndexer().indexAll();
                player.sendMessage(ChatColor.GREEN + "工作区索引已重新加载。");
            } else if (target.equals("config")) {
                plugin.getConfigManager().loadConfig();
                player.sendMessage(ChatColor.GREEN + "配置文件已重新加载。");
            } else if (target.equals("eula")) {
                plugin.getEulaManager().reload();
                player.sendMessage(ChatColor.GREEN + "EULA 文件已重新加载。");
            } else if (target.equals("deeply")) {
                player.sendMessage(ChatColor.YELLOW + "正在深度重载插件...");
                org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                    org.bukkit.plugin.PluginManager pm = org.bukkit.Bukkit.getPluginManager();
                    pm.disablePlugin(plugin);
                    pm.enablePlugin(plugin);
                    player.sendMessage(ChatColor.GREEN + "插件已深度重载（已完成卸载与重新加载）。");
                });
            } else {
                player.sendMessage(ChatColor.RED + "用法: /fancy reload [workspace|config|deeply]");
            }
        }
    }

    private void handleStatus(Player player) {
        player.sendMessage(ChatColor.AQUA + "=== FancyHelper 状态 ===");
        player.sendMessage(ChatColor.WHITE + "已索引命令: " + ChatColor.YELLOW + plugin.getWorkspaceIndexer().getIndexedCommands().size());
        player.sendMessage(ChatColor.WHITE + "已索引预设: " + ChatColor.YELLOW + plugin.getWorkspaceIndexer().getIndexedPresets().size());
        player.sendMessage(ChatColor.WHITE + "CLI 模式玩家: " + ChatColor.YELLOW + plugin.getCliManager().getActivePlayersCount());
        player.sendMessage(ChatColor.WHITE + "插件版本: " + ChatColor.YELLOW + plugin.getDescription().getVersion());
        player.sendMessage(ChatColor.AQUA + "=======================");
    }

    private void handleSettings(Player player) {
        player.sendMessage(ChatColor.GOLD + "--- FancyHelper 设置 ---");
        sendToggleMessage(player, "ls", "列出文件列表");
        sendToggleMessage(player, "read", "读取文件内容");
        sendToggleMessage(player, "diff", "修改文件内容");
        player.sendMessage(ChatColor.GRAY + "注意：关闭后再开启需要重新进行安全验证。");
    }

    private void sendToggleMessage(Player player, String tool, String description) {
        boolean enabled = plugin.getConfigManager().isPlayerToolEnabled(player, tool);
        TextComponent message = new TextComponent(ChatColor.WHITE + "- " + description + " (" + tool + "): ");
        
        TextComponent status = new TextComponent(enabled ? ChatColor.GREEN + "[已启用]" : ChatColor.RED + "[已禁用]");
        status.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cli toggle " + tool));
        status.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(ChatColor.GRAY + "点击切换状态")));
        
        message.addExtra(status);
        player.spigot().sendMessage(message);
    }

    private void handleTestError(Player player) {
        try {
            player.sendMessage(ChatColor.YELLOW + "正在触发测试异常...");
            throw new RuntimeException("这是一个手动触发的测试异常，用于验证云端上报功能。");
        } catch (Exception e) {
            plugin.getCloudErrorReport().report(e);
            player.sendMessage(ChatColor.GREEN + "测试异常已捕获并尝试上报，请查看后台日志。");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subCommands = new ArrayList<>(Arrays.asList("reload", "status", "yolo", "normal", "checkupdate", "upgrade", "read", "set", "settings"));
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
        }
        return new ArrayList<>();
    }
}
