package org.YanPl;

import org.YanPl.command.CLICommand;
import org.YanPl.listener.ChatListener;
import org.YanPl.manager.CLIManager;
import org.YanPl.manager.ConfigManager;
import org.YanPl.manager.WorkspaceIndexer;
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

    @Override
    public void onEnable() {
        // 执行旧插件清理（清理带有 mineagent 关键词的文件）
        cleanOldPluginFiles();

        // 初始化配置管理器
        configManager = new ConfigManager(this);
        
        // 异步索引服务器命令与预设文件
        workspaceIndexer = new WorkspaceIndexer(this);
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> workspaceIndexer.indexAll());

        // 初始化 CLI 管理器（管理玩家的 AI 会话）
        cliManager = new CLIManager(this);

        CLICommand cliCommand = new CLICommand(this);
        getCommand("fancyhelper").setExecutor(cliCommand);
        getCommand("fancyhelper").setTabCompleter(cliCommand);

        // 注册事件监听器
        getServer().getPluginManager().registerEvents(new ChatListener(this), this);

        // bStats 统计
        int pluginId = 28567;
        new Metrics(this, pluginId);

        // 检查 server.properties 中的安全配置并提示
        checkSecureProfile();

        // 打印启动 ASCII 艺术
        getLogger().info("    ____                   __ __    __            ");
        getLogger().info("   / __/__ ____  ______ __/ // /__ / /__  ___ ____ ");
        getLogger().info("  / _// _ `/ _ \\/ __/ // / _  / -_) / _ \\/ -_) __/ ");
        getLogger().info(" /_/  \\_,_/_//_/\\__ /\\_, /_//_/\\__/_/ .__/\\__/_/   ");
        getLogger().info("                   /___/          /_/             ");

        getLogger().info("FancyHelper 已启用！");
    }

    /**
     * 清理旧插件文件：正则匹配 /plugins 下包含 mineagent 的文件或文件夹，并移至 /plugins/old
     */
    private void cleanOldPluginFiles() {
        java.io.File pluginsDir = getDataFolder().getParentFile();
        if (pluginsDir == null || !pluginsDir.exists() || !pluginsDir.isDirectory()) {
            return;
        }

        java.io.File oldDir = new java.io.File(pluginsDir, "old");
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
                getLogger().info("已将旧插件文件/文件夹 [" + target.getName() + "] 移动至 plugins/old/");
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
                getLogger().warning("这可能会导致 CLI 模式下的聊天拦截出现警告。");
                getLogger().warning("建议在 server.properties 中将其设置为 false。");
                getLogger().warning("====================================================");
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
}
