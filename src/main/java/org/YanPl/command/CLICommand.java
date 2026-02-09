package org.YanPl.command;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.YanPl.FancyHelper;
import org.YanPl.model.DialogueSession;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.SimplePluginManager;

import java.io.File;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
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
                plugin.getUpdateManager().downloadAndInstall(player, true);
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
            } else if (target.equals("deeply") || target.equals("deep")) {
                handleDeepReload(player);
            } else {
                player.sendMessage(ChatColor.RED + "用法: /fancy reload [workspace|config|eula|deeply]");
            }
        }
    }

    /**
     * 深度重载：尽可能“卸载”当前插件实例，然后从 plugins 目录重新加载 jar 并启用。
     * 说明：Bukkit 并不官方支持真正的卸载/重载，这里做的是常见的 best-effort 热重载流程。
     */
    private void handleDeepReload(Player player) {
        PluginManager pluginManager = Bukkit.getPluginManager();

        // 优先检查是否有 PlugMan 插件，利用成熟插件进行重载更安全
        Plugin plugMan = pluginManager.getPlugin("PlugMan");
        if (plugMan == null) {
            plugMan = pluginManager.getPlugin("PlugManX");
        }

        if (plugMan != null && plugMan.isEnabled()) {
            player.sendMessage(ChatColor.GREEN + "检测到 " + plugMan.getName() + "，正在调用其重载指令...");
            // 尝试执行 /plm reload FancyHelper 或 /plugman reload FancyHelper
            String reloadCmd = (plugMan.getName().equalsIgnoreCase("PlugManX") ? "plm" : "plugman")
                    + " reload " + plugin.getName();
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), reloadCmd);
            return;
        }

        if (isModernPaperPluginSystem()) {
            player.sendMessage(ChatColor.RED + "检测到 Paper 现代插件系统：运行时卸载/重载 Bukkit 插件会被阻止。");
            player.sendMessage(ChatColor.YELLOW + "请改用重启服务器来重新加载插件文件（例如使用 /restart）。");
            return;
        }

        File pluginsDir = plugin.getDataFolder().getParentFile();
        if (pluginsDir == null || !pluginsDir.exists() || !pluginsDir.isDirectory()) {
            player.sendMessage(ChatColor.RED + "无法定位 plugins 目录，深度重载已取消。");
            return;
        }

        File jarFile = findReloadJarFile(pluginsDir);
        if (jarFile == null || !jarFile.isFile()) {
            player.sendMessage(ChatColor.RED + "未找到可用于重新加载的插件 jar 文件（需位于 plugins 目录）。");
            return;
        }

        player.sendMessage(ChatColor.YELLOW + "正在深度重载 FancyHelper...");
        player.sendMessage(ChatColor.GRAY + "目标文件: " + jarFile.getName());

        try {
            unregisterPluginCommands(plugin);
            HandlerList.unregisterAll(plugin);
            Bukkit.getScheduler().cancelTasks(plugin);

            pluginManager.disablePlugin(plugin);
            forceRemovePluginFromManager(pluginManager, plugin);
            closePluginClassLoader(plugin);

            Plugin reloaded = pluginManager.loadPlugin(jarFile);
            pluginManager.enablePlugin(reloaded);

            player.sendMessage(ChatColor.GREEN + "深度重载完成: " + reloaded.getDescription().getFullName());
        } catch (Throwable t) {
            plugin.getCloudErrorReport().report(t);
            player.sendMessage(ChatColor.RED + "深度重载失败: " + t.getClass().getSimpleName() + " - " + (t.getMessage() == null ? "无错误信息" : t.getMessage()));
        }
    }

    /**
     * 判断当前是否运行在 Paper 的“现代插件系统”上。
     * 该系统会阻止运行时重复加载同一插件标识符，导致热重载（unload/load）失败。
     */
    private boolean isModernPaperPluginSystem() {
        try {
            Class.forName("io.papermc.paper.plugin.manager.PaperPluginManagerImpl");
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    /**
     * 在 plugins 目录中挑选最合适的 FancyHelper jar 文件用于重新加载。
     * 优先选择文件名包含 fancyhelper 或插件名的 jar，且按最后修改时间降序。
     */
    private File findReloadJarFile(File pluginsDir) {
        String pluginNameLower = plugin.getDescription().getName().toLowerCase();

        File[] jars = pluginsDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".jar"));
        if (jars == null || jars.length == 0) {
            return null;
        }

        List<File> preferred = Arrays.stream(jars)
                .filter(File::isFile)
                .filter(f -> {
                    String n = f.getName().toLowerCase();
                    return n.contains("fancyhelper") || n.contains(pluginNameLower);
                })
                .sorted(Comparator.comparingLong(File::lastModified).reversed())
                .collect(Collectors.toList());

        if (!preferred.isEmpty()) {
            return preferred.get(0);
        }

        File current = getCurrentJarFile();
        if (current != null && current.isFile() && pluginsDir.equals(current.getParentFile())) {
            return current;
        }

        return null;
    }

    /**
     * 获取当前运行中的插件 jar 文件（从 Class ProtectionDomain 推断）。
     */
    private File getCurrentJarFile() {
        try {
            URL jarUrl = plugin.getClass().getProtectionDomain().getCodeSource().getLocation();
            URI jarUri = jarUrl.toURI();
            File jarFile = new File(jarUri);
            return jarFile.isFile() ? jarFile : null;
        } catch (Exception e) {
            plugin.getCloudErrorReport().report(e);
            return null;
        }
    }

    /**
     * 从 CommandMap 中移除本插件注册的命令，避免重载后出现重复注册或旧引用残留。
     */
    @SuppressWarnings("unchecked")
    private void unregisterPluginCommands(Plugin targetPlugin) {
        try {
            Field commandMapField = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            commandMapField.setAccessible(true);
            SimpleCommandMap commandMap = (SimpleCommandMap) commandMapField.get(Bukkit.getServer());

            Field knownCommandsField = SimpleCommandMap.class.getDeclaredField("knownCommands");
            knownCommandsField.setAccessible(true);
            Map<String, Command> knownCommands = (Map<String, Command>) knownCommandsField.get(commandMap);

            Iterator<Map.Entry<String, Command>> it = knownCommands.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, Command> entry = it.next();
                Command cmd = entry.getValue();
                if (cmd instanceof PluginCommand) {
                    PluginCommand pluginCommand = (PluginCommand) cmd;
                    if (pluginCommand.getPlugin().equals(targetPlugin)) {
                        it.remove();
                    }
                }
            }
        } catch (Exception e) {
            plugin.getCloudErrorReport().report(e);
        }
    }

    /**
     * 从 SimplePluginManager 的内部容器中移除插件引用，尽量降低“假卸载”残留。
     */
    @SuppressWarnings("unchecked")
    private void forceRemovePluginFromManager(PluginManager pluginManager, Plugin targetPlugin) {
        if (!(pluginManager instanceof SimplePluginManager)) {
            return;
        }

        try {
            SimplePluginManager spm = (SimplePluginManager) pluginManager;

            Field pluginsField = SimplePluginManager.class.getDeclaredField("plugins");
            pluginsField.setAccessible(true);
            List<Plugin> plugins = (List<Plugin>) pluginsField.get(spm);
            plugins.remove(targetPlugin);

            Field lookupNamesField = SimplePluginManager.class.getDeclaredField("lookupNames");
            lookupNamesField.setAccessible(true);
            Map<String, Plugin> lookupNames = (Map<String, Plugin>) lookupNamesField.get(spm);
            lookupNames.remove(targetPlugin.getName());
        } catch (Exception e) {
            plugin.getCloudErrorReport().report(e);
        }
    }

    /**
     * 尝试关闭插件的 ClassLoader（若底层为 URLClassLoader/PluginClassLoader），释放 jar 文件句柄以便重新加载。
     */
    private void closePluginClassLoader(Plugin targetPlugin) {
        try {
            ClassLoader classLoader = targetPlugin.getClass().getClassLoader();
            if (classLoader instanceof URLClassLoader) {
                ((URLClassLoader) classLoader).close();
                return;
            }

            try {
                java.lang.reflect.Method closeMethod = classLoader.getClass().getMethod("close");
                closeMethod.setAccessible(true);
                closeMethod.invoke(classLoader);
            } catch (NoSuchMethodException ignored) {
            }
        } catch (Throwable t) {
            plugin.getCloudErrorReport().report(t);
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
