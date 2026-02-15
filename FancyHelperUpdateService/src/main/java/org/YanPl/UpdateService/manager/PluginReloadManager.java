package org.YanPl.UpdateService.manager;

import org.YanPl.UpdateService.util.FieldAccessor;
import org.YanPl.UpdateService.util.MethodAccessor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.event.Event;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredListener;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.URLClassLoader;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * 插件重载管理器
 * 基于 PlugManX 的插件管理功能简化实现
 * 支持禁用、启用和重载插件
 */
public class PluginReloadManager {

    private static final Logger LOGGER = Logger.getLogger(PluginReloadManager.class.getName());

    private static Runnable syncCommandsRunnable;

    static {
        try {
            // 初始化 syncCommands Runnable
            var serverClass = Bukkit.getServer().getClass();
            var lookup = MethodHandles.lookup();
            var syncCommandsHandle = lookup.findVirtual(serverClass, "syncCommands", MethodType.methodType(void.class));

            syncCommandsRunnable = (Runnable) LambdaMetafactory.metafactory(
                    lookup,
                    "run",
                    MethodType.methodType(Runnable.class, serverClass),
                    MethodType.methodType(void.class),
                    syncCommandsHandle,
                    MethodType.methodType(void.class)
            ).getTarget().invoke(Bukkit.getServer());
        } catch (Throwable e) {
            LOGGER.log(Level.WARNING, "Failed to initialize syncCommandsRunnable, using fallback", e);
            syncCommandsRunnable = () -> {};
        }
    }

    /**
     * 禁用指定插件
     *
     * @param pluginName 插件名称
     * @return 是否成功禁用
     */
    public static boolean disablePlugin(String pluginName) {
        var plugin = Bukkit.getPluginManager().getPlugin(pluginName);
        if (plugin == null) {
            LOGGER.warning("Plugin '" + pluginName + "' not found!");
            return false;
        }

        if (!plugin.isEnabled()) {
            LOGGER.info("Plugin '" + pluginName + "' is already disabled!");
            return true;
        }

        Bukkit.getPluginManager().disablePlugin(plugin);
        LOGGER.info("Plugin '" + pluginName + "' disabled successfully!");
        return true;
    }

    /**
     * 启用指定插件
     *
     * @param pluginName 插件名称
     * @return 是否成功启用
     */
    public static boolean enablePlugin(String pluginName) {
        var plugin = Bukkit.getPluginManager().getPlugin(pluginName);
        if (plugin == null) {
            LOGGER.warning("Plugin '" + pluginName + "' not found!");
            return false;
        }

        if (plugin.isEnabled()) {
            LOGGER.info("Plugin '" + pluginName + "' is already enabled!");
            return true;
        }

        Bukkit.getPluginManager().enablePlugin(plugin);
        LOGGER.info("Plugin '" + pluginName + "' enabled successfully!");
        return true;
    }

    /**
     * 重载指定插件（先卸载再加载）
     *
     * @param pluginName 插件名称
     * @return 是否成功重载
     */
    public static boolean reloadPlugin(String pluginName) {
        var plugin = Bukkit.getPluginManager().getPlugin(pluginName);
        if (plugin == null) {
            LOGGER.warning("Plugin '" + pluginName + "' not found!");
            return false;
        }

        LOGGER.info("Reloading plugin '" + pluginName + "'...");

        // 在卸载之前先获取文件路径
        var pluginFile = getPluginFileFromPlugin(plugin);
        if (pluginFile == null) {
            LOGGER.warning("Failed to get plugin file from plugin object, trying to find it...");
            pluginFile = findPluginFile(pluginName);
        }

        if (pluginFile == null) {
            LOGGER.warning("Failed to find plugin file for '" + pluginName + "'!");
            return false;
        }

        LOGGER.info("Found plugin file: " + pluginFile.getAbsolutePath());

        // 卸载插件
        if (!unloadPlugin(plugin)) {
            LOGGER.warning("Failed to unload plugin '" + pluginName + "'!");
            return false;
        }

        // 加载插件
        if (!loadPlugin(pluginFile)) {
            LOGGER.warning("Failed to load plugin '" + pluginName + "'!");
            return false;
        }

        LOGGER.info("Plugin '" + pluginName + "' reloaded successfully!");
        return true;
    }

    /**
     * 卸载插件
     */
    private static boolean unloadPlugin(Plugin plugin) {
        try {
            var pluginManager = Bukkit.getPluginManager();
            pluginManager.disablePlugin(plugin);

            // 提取插件管理器数据
            var plugins = FieldAccessor.<List<Plugin>>getValue(pluginManager.getClass(), "plugins", pluginManager);
            var lookupNames = FieldAccessor.<Map<String, Plugin>>getValue(pluginManager.getClass(), "lookupNames", pluginManager);

            Map<Event, SortedSet<RegisteredListener>> listeners = null;
            boolean reloadListeners = true;
            try {
                listeners = FieldAccessor.getValue(pluginManager.getClass(), "listeners", pluginManager);
            } catch (Exception e) {
                reloadListeners = false;
            }

            var commandMap = getCommandMap();
            var knownCommands = getKnownCommands();

            pluginManager.disablePlugin(plugin);

            // 清理监听器
            if (listeners != null && reloadListeners) {
                listeners.values().forEach(set -> set.removeIf(value -> value.getPlugin() == plugin));
            }

            // 清理命令
            cleanupCommands(plugin, commandMap, knownCommands);

            // 同步命令
            syncCommands();

            // 从插件列表中移除
            if (plugins != null) {
                plugins.removeIf(p -> p.getName().equalsIgnoreCase(plugin.getName()));
            }
            if (lookupNames != null) {
                lookupNames.remove(plugin.getName());
            }

            // 关闭 ClassLoader
            closeClassLoader(plugin);

            // 垃圾回收释放 JAR 文件锁
            System.gc();

            return true;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to unload plugin '" + plugin.getName() + "'", e);
            return false;
        }
    }

    /**
     * 加载插件
     */
    private static boolean loadPlugin(File pluginFile) {
        try {
            var target = Bukkit.getPluginManager().loadPlugin(pluginFile);
            if (target == null) {
                return false;
            }

            target.onLoad();
            Bukkit.getPluginManager().enablePlugin(target);

            // 安排命令同步
            scheduleCommandSync();

            return true;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to load plugin: " + pluginFile.getName(), e);
            return false;
        }
    }

    /**
     * 查找插件文件
     */
    private static File findPluginFile(String pluginName) {
        var pluginDir = new File("plugins");
        if (!pluginDir.isDirectory()) {
            LOGGER.warning("plugins directory not found!");
            return null;
        }

        // 直接查找（精确匹配）
        var pluginFile = new File(pluginDir, pluginName + ".jar");
        if (pluginFile.isFile()) {
            LOGGER.info("Found plugin file by exact match: " + pluginFile.getName());
            return pluginFile;
        }

        // 遍历所有 JAR 文件查找
        var files = pluginDir.listFiles();
        if (files == null) {
            LOGGER.warning("Cannot list files in plugins directory!");
            return null;
        }

        LOGGER.info("Searching for plugin '" + pluginName + "' in " + files.length + " files...");

        for (var file : files) {
            if (!file.getName().endsWith(".jar")) {
                continue;
            }

            try {
                var desc = getPluginDescription(file);
                if (desc != null) {
                    LOGGER.info("Checking file: " + file.getName() + " -> " + desc.getName());
                    if (desc.getName().equalsIgnoreCase(pluginName)) {
                        LOGGER.info("Found plugin file: " + file.getName());
                        return file;
                    }
                }
            } catch (Exception e) {
                LOGGER.warning("Failed to read plugin description from " + file.getName() + ": " + e.getMessage());
            }
        }

        LOGGER.warning("Plugin file for '" + pluginName + "' not found!");
        return null;
    }

    /**
     * 使用反射获取插件描述
     */
    private static org.bukkit.plugin.PluginDescriptionFile getPluginDescription(File file) throws Exception {
        // 尝试从服务器获取 PluginLoader
        var server = Bukkit.getServer();
        var pluginLoader = FieldAccessor.getValue(server.getClass(), "pluginLoader", server);
        if (pluginLoader == null) {
            LOGGER.warning("PluginLoader is null!");
            return null;
        }

        // 尝试多种方法名
        String[] methodNames = {"getPluginDescription", "loadPluginDescription"};
        for (var methodName : methodNames) {
            try {
                var method = MethodAccessor.getMethod(pluginLoader.getClass(), methodName, File.class);
                if (method != null) {
                    var desc = (org.bukkit.plugin.PluginDescriptionFile) MethodAccessor.invoke(methodName, pluginLoader, new Class<?>[]{File.class}, file);
                    if (desc != null) {
                        return desc;
                    }
                }
            } catch (Exception e) {
                LOGGER.fine("Method '" + methodName + "' not available: " + e.getMessage());
            }
        }

        return null;
    }

    /**
     * 从插件对象获取其文件路径（使用正则匹配）
     */
    private static File getPluginFileFromPlugin(Plugin plugin) {
        try {
            var desc = plugin.getDescription();
            if (desc == null) {
                return null;
            }

            var pluginName = desc.getName();
            var pluginDir = new File("plugins");
            if (!pluginDir.isDirectory()) {
                return null;
            }

            // 构建正则表达式，匹配插件名后跟 "-" 和任意字符，最后以 .jar 结尾
            var pattern = java.util.regex.Pattern.compile(
                    "^" + java.util.regex.Pattern.quote(pluginName) + "-.*\\.jar$",
                    java.util.regex.Pattern.CASE_INSENSITIVE
            );

            var files = pluginDir.listFiles();
            if (files == null) {
                return null;
            }

            for (var file : files) {
                if (!file.getName().endsWith(".jar")) {
                    continue;
                }

                var matcher = pattern.matcher(file.getName());
                if (matcher.matches()) {
                    LOGGER.info("Found plugin file using regex: " + file.getName());
                    return file;
                }
            }

            return null;
        } catch (Exception e) {
            LOGGER.warning("Failed to get plugin file from plugin object: " + e.getMessage());
            return null;
        }
    }

    /**
     * 获取 CommandMap
     */
    private static SimpleCommandMap getCommandMap() {
        try {
            var craftBukkitPrefix = Bukkit.getServer().getClass().getPackage().getName();
            var craftServerClass = Class.forName(craftBukkitPrefix + ".CraftServer");
            return FieldAccessor.getValue(craftServerClass, "commandMap", Bukkit.getServer());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to get command map", e);
            return null;
        }
    }

    /**
     * 获取已知命令
     */
    private static Map<String, org.bukkit.command.Command> getKnownCommands() {
        try {
            var commandMap = getCommandMap();
            if (commandMap == null) {
                return new HashMap<>();
            }
            return FieldAccessor.getValue(SimpleCommandMap.class, "knownCommands", commandMap);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to get known commands", e);
            return new HashMap<>();
        }
    }

    /**
     * 清理插件命令
     */
    private static void cleanupCommands(Plugin plugin, SimpleCommandMap commandMap, Map<String, org.bukkit.command.Command> knownCommands) {
        if (commandMap == null || knownCommands == null) {
            return;
        }

        var modifiedKnownCommands = new HashMap<>(knownCommands);

        for (var entry : modifiedKnownCommands.entrySet()) {
            var command = entry.getValue();

            if (command instanceof PluginCommand) {
                var pluginCommand = (PluginCommand) command;
                if (pluginCommand.getPlugin() == plugin) {
                    pluginCommand.unregister(commandMap);
                    knownCommands.remove(entry.getKey());
                }
            } else {
                // 处理非插件命令
                try {
                    var pluginField = findPluginField(command.getClass());
                    if (pluginField != null) {
                        var owningPlugin = FieldAccessor.<Plugin>getValue(command.getClass(), pluginField, command);
                        if (owningPlugin != null && owningPlugin.getName().equalsIgnoreCase(plugin.getName())) {
                            command.unregister(commandMap);
                            knownCommands.remove(entry.getKey());
                        }
                    }
                } catch (Exception e) {
                    // 跳过无法处理的命令
                }
            }
        }

        syncCommands();
    }

    /**
     * 查找命令中的插件字段
     */
    private static String findPluginField(Class<?> clazz) {
        for (var field : clazz.getDeclaredFields()) {
            if (Plugin.class.isAssignableFrom(field.getType())) {
                return field.getName();
            }
        }
        return null;
    }

    /**
     * 关闭 ClassLoader
     */
    private static void closeClassLoader(Plugin plugin) {
        var classLoader = plugin.getClass().getClassLoader();
        if (!(classLoader instanceof URLClassLoader)) {
            return;
        }

        try {
            FieldAccessor.setValue("plugin", classLoader, null);
            FieldAccessor.setValue("pluginInit", classLoader, null);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error removing plugin from classloader", e);
        }

        try {
            ((Closeable) classLoader).close();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error closing plugin classloader", e);
        }
    }

    /**
     * 同步命令
     */
    private static void syncCommands() {
        syncCommandsRunnable.run();
        Bukkit.getOnlinePlayers().forEach(player -> player.updateCommands());
    }

    /**
     * 安排命令同步
     */
    private static void scheduleCommandSync() {
        Bukkit.getScheduler().runTaskLater(Bukkit.getPluginManager().getPlugin("FancyHelperUpdateService"), PluginReloadManager::syncCommands, 10L);
    }
}