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
public final class MineAgent extends JavaPlugin {
    private ConfigManager configManager;
    private WorkspaceIndexer workspaceIndexer;
    private CLIManager cliManager;

    @Override
    public void onEnable() {
        // 初始化配置管理器
        configManager = new ConfigManager(this);
        
        // 异步索引服务器命令与预设文件
        workspaceIndexer = new WorkspaceIndexer(this);
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> workspaceIndexer.indexAll());

        // 初始化 CLI 管理器（管理玩家的 AI 会话）
        cliManager = new CLIManager(this);

        CLICommand cliCommand = new CLICommand(this);
        getCommand("mineagent").setExecutor(cliCommand);
        getCommand("mineagent").setTabCompleter(cliCommand);

        getServer().getPluginManager().registerEvents(new ChatListener(this), this);

        // bStats 统计
        int pluginId = 28567;
        new Metrics(this, pluginId);

        // 检查 server.properties 中的安全配置并提示
        checkSecureProfile();

        getLogger().info("MineAgent 已启用！");
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
        getLogger().info("MineAgent 正在禁用...");

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

        getLogger().info("MineAgent 已禁用！");
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
