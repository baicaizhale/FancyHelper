package org.YanPl.command;

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
            player.sendMessage(ChatColor.GREEN + "配置与工作区已重新加载。");
        } else if (args.length == 2) {
            String target = args[1].toLowerCase();
            if (target.equals("workspace")) {
                plugin.getWorkspaceIndexer().indexAll();
                player.sendMessage(ChatColor.GREEN + "工作区索引已重新加载。");
            } else if (target.equals("config")) {
                plugin.getConfigManager().loadConfig();
                player.sendMessage(ChatColor.GREEN + "配置文件已重新加载。");
            } else {
                player.sendMessage(ChatColor.RED + "用法: /fancy reload [workspace|config]");
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
            return Arrays.asList("reload", "status").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("reload")) {
            return Arrays.asList("workspace", "config").stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
