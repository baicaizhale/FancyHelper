package org.YanPl;

import org.YanPl.api.MetasoAPI;
import org.YanPl.api.TavilyAPI;
import org.YanPl.command.CLICommand;
import org.YanPl.listener.ChatListener;
import org.YanPl.listener.GUIListener;
import org.YanPl.gui.GUIManager;
import org.YanPl.manager.CLIManager;
import org.YanPl.manager.ConfigManager;
import org.YanPl.manager.PacketCaptureManager;
import org.YanPl.manager.VerificationManager;
import org.YanPl.manager.EulaManager;
import org.YanPl.manager.UpdateManager;
import org.YanPl.manager.WorkspaceIndexer;
import org.YanPl.manager.TodoManager;
import org.YanPl.manager.NoticeManager;
import org.YanPl.manager.FileWatcherManager;
import org.YanPl.manager.InstructionManager;
import org.YanPl.manager.PlanManager;
import org.YanPl.util.CloudErrorReport;
import org.YanPl.util.ErrorHandler;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * 插件主类：初始化各管理器并注册命令/事件。
 */
public final class FancyHelper extends JavaPlugin {
    private ConfigManager configManager;
    private WorkspaceIndexer workspaceIndexer;
    private CLIManager cliManager;
    private UpdateManager updateManager;
    private EulaManager eulaManager;
    private CloudErrorReport cloudErrorReport;
    private VerificationManager verificationManager;
    private PacketCaptureManager packetCaptureManager;
    private TodoManager todoManager;
    private NoticeManager noticeManager;
    private FileWatcherManager fileWatcherManager;
    private TavilyAPI tavilyAPI;
    private MetasoAPI metasoAPI;
    private ErrorHandler errorHandler;
    private InstructionManager instructionManager;
    private PlanManager planManager;
    private org.YanPl.gui.GUIManager guiManager;

    @Override
    public void onEnable() {
        try {
            // 初始化云端错误上报
            cloudErrorReport = new CloudErrorReport(this);
            
            // 初始化统一错误处理器
            errorHandler = new ErrorHandler(this);

            // 执行旧插件清理（清理带有 mineagent 关键词的文件）
            cleanOldPluginFiles();

            // 释放热重载服务 jar 到 plugins/FancyHelper/lib
            saveResource("lib/FancyHelperReloadService.jar", true);

            // 初始化 EULA 管理器（优先于配置，以便更新时强制替换 EULA）
            eulaManager = new EulaManager(this);

            // 初始化配置管理器
            configManager = new ConfigManager(this);
            
            // 初始化验证管理器
            verificationManager = new VerificationManager(this);
            
            // 检查 ProtocolLib 依赖并初始化数据包捕获管理器
            if (getServer().getPluginManager().isPluginEnabled("ProtocolLib")) {
                initPacketCapture();
                // 初始化告示牌编辑器
                org.YanPl.gui.SignEditor.init(this);
            } else {
                getLogger().warning("==================");
                getLogger().warning("未检测到 ProtocolLib！");
                getLogger().warning("FancyHelper 的部分高级功能（如命令输出捕获、告示牌编辑）将无法使用。");
                getLogger().warning("建议前往 https://www.spigotmc.org/resources/protocollib.1997/ 下载并安装以获得最佳体验。");
                getLogger().warning("==================");
                packetCaptureManager = null;
            }
            
            // 异步索引服务器命令与预设文件
            workspaceIndexer = new WorkspaceIndexer(this);
            Bukkit.getScheduler().runTaskAsynchronously(this, () -> workspaceIndexer.indexAll());

            // 初始化待办管理器
            todoManager = new TodoManager(this);

            // 初始化偏好记忆管理器
            instructionManager = new InstructionManager(this);

            // 初始化 CLI 管理器（管理玩家的 AI 会话）
            cliManager = new CLIManager(this);

            // 初始化计划模式管理器
            planManager = new PlanManager(this, cliManager, cliManager.getToolExecutor());

            // 初始化更新管理器并检查更新
            updateManager = new UpdateManager(this);
            updateManager.checkForUpdates();

            // 初始化公告管理器（构造函数中会自动开始定期获取公告）
            noticeManager = new NoticeManager(this);

            // 初始化文件监听管理器
            fileWatcherManager = new FileWatcherManager(this);

            // 初始化 Tavily API
            tavilyAPI = new TavilyAPI(this);

            // 初始化 Metaso API
            metasoAPI = new MetasoAPI(this);

            CLICommand cliCommand = new CLICommand(this);
            PluginCommand command = getCommand("fancyhelper");
            if (command != null) {
                command.setExecutor(cliCommand);
                command.setTabCompleter(cliCommand);
            } else {
                getLogger().severe("无法注册命令 'fancyhelper' - 请检查 plugin.yml！");
            }

            // 初始化 GUI 栈管理器
            guiManager = new org.YanPl.gui.GUIManager(this);

            // 注册事件监听器
            getServer().getPluginManager().registerEvents(new ChatListener(this), this);
            getServer().getPluginManager().registerEvents(new GUIListener(this, guiManager), this);

            // bStats 统计
            int pluginId = 29036;
            new Metrics(this, pluginId);

            // 检查 server.properties 中的安全配置并提示
            checkSecureProfile();

            // 检测是否为 Spigot 服务端（非 Paper 及下游），显示警告
            checkSpigotServer();

            // 打印启动 ASCII 艺术（模仿 LuckPerms 风格，包含颜色）
            // 使用 ANSI 颜色代码：\u001B[38;5;81m 深青色, \u001B[38;5;208m 橙色, \u001B[36m 青色, \u001B[38;5;155m 灰色, \u001B[0m 重置
            getLogger().info(" \u001B[38;5;81m_\u001B[0m       ");
            getLogger().info("\u001B[38;5;81m|_\u001B[0m   \u001B[38;5;81m|_|\u001B[0m   \u001B[38;5;208mFancyHelper\u001B[0m \u001B[36mv" + getDescription().getVersion() + "\u001B[0m");
            getLogger().info("\u001B[38;5;81m|\u001B[0m    \u001B[38;5;81m| |\u001B[0m   \u001B[38;5;155mRunning on Spigot - " + getServer().getName().split("-")[0] + "\u001B[0m");
            getLogger().info("");

            // 尝试同步命令，修复热重载后的 Brigadier 缓存问题
            syncCommands();
        } catch (Throwable e) {
            getLogger().severe("FancyHelper 启动失败: " + e.getMessage());
            e.printStackTrace();
            setEnabled(false);
        }
    }

// 下面一些代码只是为了清理旧插件防止干扰，没有任何恶意
    private void cleanOldPluginFiles() {
        java.io.File pluginsDir = getDataFolder().getParentFile();
        if (pluginsDir == null || !pluginsDir.exists() || !pluginsDir.isDirectory()) {
            return;
        }
        java.io.File oldDir = new java.io.File(getDataFolder(), "old");
        java.io.File[] targets = pluginsDir.listFiles((dir, name) -> {
            // 正则匹配包含 mineagent (不区分大小写)，且排除 "old" 文件夹本身
            return name.toLowerCase().contains("mineagent") && !name.equalsIgnoreCase("old");
        });

        if (targets == null || targets.length == 0) {
            return;
        }

        if (!oldDir.exists()) {
            oldDir.mkdirs();
        }

        for (java.io.File target : targets) {
            java.io.File dest = new java.io.File(oldDir, target.getName());
            
            // 如果目标已存在，先删除旧的以防移动失败
            if (dest.exists()) {
                if (dest.isDirectory()) {
                    deleteDirectory(dest);
                } else {
                    dest.delete();
                }
            }

            if (target.renameTo(dest)) {
                getLogger().info("已将旧插件文件/文件夹 [" + target.getName() + "] 移动至 plugins/FancyHelper/old/");
            } else {
                getLogger().warning("无法移动旧插件文件 [" + target.getName() + "]，请检查权限。");
            }
        }
    }

    /**
     * 递归删除目录
     */
    private void deleteDirectory(java.io.File directory) {
        java.io.File[] files = directory.listFiles();
        if (files != null) {
            for (java.io.File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }
        directory.delete();
    }

    private void checkSecureProfile() {
        try {
            java.lang.reflect.Method method = getServer().getClass().getMethod("shouldEnforceSecureProfile");
            boolean enforce = (boolean) method.invoke(getServer());
            if (enforce) {
                getLogger().warning("====================================================");
                getLogger().warning("检测到服务器启用了 'enforce-secure-profile'。");
                getLogger().warning("这可能会导致 CLI 模式下的聊天拦截出现警告：");
                getLogger().warning("'Failed to update secure chat state for <player>:");
                getLogger().warning("Chat disabled due to missing profile public key.'");
                getLogger().warning("");
                getLogger().warning("正在尝试自动禁用此功能...");
                getLogger().warning("====================================================");
                
                // 尝试自动修改 server.properties
                try {
                    java.io.File serverProperties = new java.io.File("server.properties");
                    if (serverProperties.exists() && serverProperties.isFile()) {
                        java.util.List<String> lines = java.nio.file.Files.readAllLines(serverProperties.toPath());
                        boolean modified = false;
                        java.util.List<String> newLines = new java.util.ArrayList<>();
                        
                        for (String line : lines) {
                            String trimmed = line.trim();
                            if (trimmed.toLowerCase().startsWith("enforce-secure-profile")) {
                                newLines.add("enforce-secure-profile=false");
                                modified = true;
                            } else {
                                newLines.add(line);
                            }
                        }
                        
                        if (modified) {
                            java.nio.file.Files.write(serverProperties.toPath(), newLines);
                            getLogger().info("已成功将 server.properties 中的 enforce-secure-profile 设置为 false");
                            getLogger().info("请重启服务器以使更改生效");
                        } else {
                            getLogger().warning("未在 server.properties 中找到 enforce-secure-profile 配置项");
                            getLogger().warning("请手动添加 enforce-secure-profile=false 到 server.properties");
                        }
                    } else {
                        getLogger().warning("未找到 server.properties 文件");
                        getLogger().warning("请手动添加 enforce-secure-profile=false 到 server.properties");
                    }
                } catch (Exception e) {
                    getLogger().warning("自动修改 server.properties 失败: " + e.getMessage());
                    getLogger().warning("请手动添加 enforce-secure-profile=false 到 server.properties");
                }
            }
        } catch (Exception e) {
            // 反射调用可能失败，可安全忽略
        }
    }

    /**
     * 检测是否为 Spigot 服务端（非 Paper 及下游分支），如果是则显示警告。
     * Paper 及其下游分支（如 Purpur、Pufferfish 等）通常具有更好的性能和 API 支持。
     */
    private void checkSpigotServer() {
        String serverName = getServer().getName();
        String serverVersion = getServer().getVersion();
        
        // 检测是否为 Paper 及其下游分支
        // Paper 服务端名称通常包含 "Paper"，下游分支如 Purpur、Pufferfish 等也基于 Paper
        boolean isPaperOrFork = false;
        
        // 方法1：检查服务端名称
        String lowerName = serverName.toLowerCase();
        if (lowerName.contains("paper") || lowerName.contains("purpur") || 
            lowerName.contains("pufferfish") || lowerName.contains("airplane") ||
            lowerName.contains("tuinity") || lowerName.contains("empirecraft")) {
            isPaperOrFork = true;
        }
        
        // 方法2：检查版本字符串
        if (!isPaperOrFork) {
            String lowerVersion = serverVersion.toLowerCase();
            if (lowerVersion.contains("paper") || lowerVersion.contains("purpur") || 
                lowerVersion.contains("pufferfish") || lowerVersion.contains("airplane") ||
                lowerVersion.contains("tuinity") || lowerVersion.contains("empirecraft")) {
                isPaperOrFork = true;
            }
        }
        
        // 方法3：通过反射检测 Paper 特有的类
        if (!isPaperOrFork) {
            try {
                // Paper 1.16+ 有这个特有类
                Class.forName("com.destroystokyo.paper.PaperConfig");
                isPaperOrFork = true;
            } catch (ClassNotFoundException ignored) {
                try {
                    // Paper 1.20.5+ 使用新的配置类路径
                    Class.forName("io.papermc.paper.configuration.Configuration");
                    isPaperOrFork = true;
                } catch (ClassNotFoundException ignored2) {
                    // 不是 Paper
                }
            }
        }
        
        // 如果检测到是 Spigot（非 Paper 及下游），显示警告并禁用自动升级
        if (!isPaperOrFork && (lowerName.contains("spigot") || lowerName.contains("craftbukkit"))) {
            getLogger().warning("====================");
            getLogger().warning("您正在使用 Spigot 服务端，可能导致一些奇怪的问题。");
            getLogger().warning("您可以考虑转移到 Paper 服务端哦。");
            getLogger().warning("Paper 服务端地址: https://papermc.io/");
            getLogger().warning("====================");
            
            // 禁用自动升级功能（Spigot 服务端可能存在兼容性问题）
            if (configManager.isAutoUpgrade()) {
                getConfig().set("settings.auto_upgrade", false);
                configManager.save();
                getLogger().warning("已自动禁用自动升级功能（auto_upgrade = false），以避免潜在的兼容性问题。");
            }
        }
    }

    private void initPacketCapture() {
        // 初始化数据包捕获管理器
        packetCaptureManager = new PacketCaptureManager(this);
        getLogger().info("已检测到 ProtocolLib，启用高级功能。");
    }

    @Override
    public void onDisable() {
        getLogger().info("FancyHelper 正在禁用...");

        // 注销命令，防止重载后命令指向旧插件实例
        unregisterCommands();

        // 关闭 CLI 管理器，释放资源
        if (cliManager != null) {
            cliManager.shutdown();
        }

        // 关闭 EULA 管理器，释放监听资源
        if (eulaManager != null) {
            eulaManager.shutdown();
        }

        // 关闭公告管理器
        if (noticeManager != null) {
            noticeManager.shutdown();
        }

        // 关闭文件监听管理器
        if (fileWatcherManager != null) {
            fileWatcherManager.shutdown();
        }

        // 关闭 Metaso API
        if (metasoAPI != null) {
            metasoAPI.shutdown();
        }

        // 关闭偏好记忆管理器
        if (instructionManager != null) {
            instructionManager.shutdown();
        }

        // 等待短暂时间以确保后台任务结束
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        getLogger().info("FancyHelper 已禁用！");
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public WorkspaceIndexer getWorkspaceIndexer() {
        return workspaceIndexer;
    }

    public CLIManager getCliManager() {
        return cliManager;
    }

    public UpdateManager getUpdateManager() {
        return updateManager;
    }

    public EulaManager getEulaManager() {
        return eulaManager;
    }

    public CloudErrorReport getCloudErrorReport() {
        return cloudErrorReport;
    }

    public VerificationManager getVerificationManager() {
        return verificationManager;
    }

    public PacketCaptureManager getPacketCaptureManager() {
        return packetCaptureManager;
    }

    public TodoManager getTodoManager() {
        return todoManager;
    }

    public NoticeManager getNoticeManager() {
        return noticeManager;
    }

    public TavilyAPI getTavilyAPI() {
        return tavilyAPI;
    }

    public MetasoAPI getMetasoAPI() {
        return metasoAPI;
    }

    public ErrorHandler getErrorHandler() {
        return errorHandler;
    }

    public InstructionManager getInstructionManager() {
        return instructionManager;
    }

public PlanManager getPlanManager() {
        return planManager;
    }

    public org.YanPl.gui.GUIManager getGuiManager() {
        return guiManager;
    }

    /**
     * 从 CommandMap 中移除本插件注册的命令，避免重载后出现重复注册或旧引用残留。
     */
    @SuppressWarnings("unchecked")
    private void unregisterCommands() {
        try {
            Field commandMapField = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            commandMapField.setAccessible(true);
            SimpleCommandMap commandMap = (SimpleCommandMap) commandMapField.get(Bukkit.getServer());

            Field knownCommandsField = SimpleCommandMap.class.getDeclaredField("knownCommands");
            knownCommandsField.setAccessible(true);
            Map<String, Command> knownCommands = (Map<String, Command>) knownCommandsField.get(commandMap);

            // 使用 key set 遍历并收集需要删除的 key，避免迭代器 UnsupportedOperationException
            java.util.List<String> keysToRemove = new java.util.ArrayList<>();
            for (Map.Entry<String, Command> entry : knownCommands.entrySet()) {
                Command cmd = entry.getValue();
                if (cmd instanceof PluginCommand) {
                    PluginCommand pluginCommand = (PluginCommand) cmd;
                    if (pluginCommand.getPlugin().equals(this)) {
                        keysToRemove.add(entry.getKey());
                    }
                }
            }

            for (String key : keysToRemove) {
                knownCommands.remove(key);
                // getLogger().info("已注销命令: " + key);
            }
        } catch (Exception e) {
            getLogger().warning("注销命令失败: " + e.getClass().getName() + ": " + e.getMessage());
            if (cloudErrorReport != null) {
                cloudErrorReport.report(e);
            }
        }
    }

    /**
     * 强制同步服务器命令（刷新 Brigadier 命令树），修复热重载后命令失效的问题。
     * 这通常是 Paper 1.20+ 环境下的必需操作。
     */
    private void syncCommands() {
        try {
            // 延迟一 tick 执行，确保插件完全加载后再同步
            Bukkit.getScheduler().runTask(this, () -> {
                try {
                    Object server = Bukkit.getServer();
                    // 仅在 CraftServer 上尝试调用 syncCommands
                    if (server.getClass().getName().contains("CraftServer")) {
                        java.lang.reflect.Method method = server.getClass().getDeclaredMethod("syncCommands");
                        method.setAccessible(true);
                        method.invoke(server);
                        // getLogger().info("已尝试同步命令以刷新缓存。");
                    }
                } catch (Throwable t) {
                    getLogger().warning("同步命令失败 (syncCommands): " + t.getMessage());
                }
            });
        } catch (Throwable t) {
            // 忽略调度失败
        }
    }
}
