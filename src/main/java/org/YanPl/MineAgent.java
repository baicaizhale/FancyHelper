package org.YanPl;

import org.YanPl.command.CLICommand;
import org.YanPl.listener.ChatListener;
import org.YanPl.manager.CLIManager;
import org.YanPl.manager.ConfigManager;
import org.YanPl.manager.WorkspaceIndexer;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class MineAgent extends JavaPlugin {
    private ConfigManager configManager;
    private WorkspaceIndexer workspaceIndexer;
    private CLIManager cliManager;

    @Override
    public void onEnable() {
        configManager = new ConfigManager(this);
        
        workspaceIndexer = new WorkspaceIndexer(this);
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> workspaceIndexer.indexAll());

        cliManager = new CLIManager(this);

        CLICommand cliCommand = new CLICommand(this);
        getCommand("mineagent").setExecutor(cliCommand);
        getCommand("mineagent").setTabCompleter(cliCommand);

        getServer().getPluginManager().registerEvents(new ChatListener(this), this);

        int pluginId = 28567;
        new Metrics(this, pluginId);
        
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
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("MineAgent 正在禁用...");
        
        if (cliManager != null) {
            cliManager.shutdown();
        }
        
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
