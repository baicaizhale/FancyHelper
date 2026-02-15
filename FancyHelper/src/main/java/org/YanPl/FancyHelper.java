package org.YanPl;

import org.YanPl.api.MetasoAPI;
import org.YanPl.api.TavilyAPI;
import org.YanPl.command.CLICommand;
import org.YanPl.listener.ChatListener;
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
import org.YanPl.util.CloudErrorReport;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

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

    @Override
    public void onEnable() {
        // 初始化云端错误上报
        cloudErrorReport = new CloudErrorReport(this);

        // 执行旧插件清理（清理带有 mineagent 关键词的文件）
        cleanOldPluginFiles();

        // 卸载 FancyHelperUpdateService（避免重复重载）
        unloadUpdateService();

        // 初始化 EULA 管理器（优先于配置，以便更新时强制替换 EULA）
        eulaManager = new EulaManager(this);

        // 初始化配置管理器
        configManager = new ConfigManager(this);
        
        // 初始化验证管理器
        verificationManager = new VerificationManager(this);
        
        // 检查 ProtocolLib 依赖并初始化数据包捕获管理器
        if (getServer().getPluginManager().isPluginEnabled("ProtocolLib")) {
            // 初始化数据包捕获管理器
            packetCaptureManager = new PacketCaptureManager(this);
            getLogger().info("已检测到 ProtocolLib，启用高级功能。");
        } else {
            getLogger().warning("==================");
            getLogger().warning("未检测到 ProtocolLib！");
            getLogger().warning("FancyHelper 的部分高级功能（如命令输出捕获）将无法使用。");
            getLogger().warning("建议前往 https://www.spigotmc.org/resources/protocollib.1997/ 下载并安装以获得最佳体验。");
            getLogger().warning("==================");
            packetCaptureManager = null;
        }
        
        // 异步索引服务器命令与预设文件
        workspaceIndexer = new WorkspaceIndexer(this);
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> workspaceIndexer.indexAll());

        // 初始化待办管理器
        todoManager = new TodoManager(this);

        // 初始化 CLI 管理器（管理玩家的 AI 会话）
        cliManager = new CLIManager(this);

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
        getCommand("fancyhelper").setExecutor(cliCommand);
        getCommand("fancyhelper").setTabCompleter(cliCommand);

        // 注册事件监听器
        getServer().getPluginManager().registerEvents(new ChatListener(this), this);

        // bStats 统计
        int pluginId = 29036;
        new Metrics(this, pluginId);

        // 检查 server.properties 中的安全配置并提示
        checkSecureProfile();

        // 打印启动 ASCII 艺术
        getLogger().info("  _____ _   _ ");
        getLogger().info(" |  ___| | | |");
        getLogger().info(" | |_  | |_| |");
        getLogger().info(" |  _| |  _  |");
        getLogger().info(" |_|   |_| |_|");

        getLogger().info("FancyHelper 已启用！");
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

    @Override
    public void onDisable() {
        getLogger().info("FancyHelper 正在禁用...");

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

        // 等待短暂时间以确保后台任务结束
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        getLogger().info("FancyHelper 已禁用！");
    }

    /**
     * 卸载 FancyHelperUpdateService 插件
     * 使用正则匹配查找并卸载 FancyHelperUpdateService
     */
    private void unloadUpdateService() {
        var pluginManager = getServer().getPluginManager();
        var updateServicePlugin = pluginManager.getPlugin("FancyHelperUpdateService");

        if (updateServicePlugin == null) {
            getLogger().info("FancyHelperUpdateService 未找到，跳过卸载。");
            return;
        }

        if (updateServicePlugin == this) {
            getLogger().info("检测到这是 FancyHelperUpdateService 本身，跳过卸载。");
            return;
        }

        if (!updateServicePlugin.isEnabled()) {
            getLogger().info("FancyHelperUpdateService 已禁用，跳过卸载。");
            return;
        }

        getLogger().info("正在卸载 FancyHelperUpdateService...");
        pluginManager.disablePlugin(updateServicePlugin);
        getLogger().info("FancyHelperUpdateService 已卸载。");
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
}
