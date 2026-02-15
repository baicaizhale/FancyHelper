package org.YanPl.UpdateService;

import org.YanPl.UpdateService.manager.PluginReloadManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * FancyHelper 更新服务插件
 * 负责在插件加载时自动重载 FancyHelper 插件
 */
public class FancyHelperUpdateService extends JavaPlugin {

    private static final String FANCYHELPER_PLUGIN_NAME = "FancyHelper";

    @Override
    public void onEnable() {
        getLogger().info("FancyHelperUpdateService Enabled");

        // 延迟执行重载，确保所有插件都已加载
        Bukkit.getScheduler().runTaskLater(this, this::reloadFancyHelper, 20L);
    }

    @Override
    public void onDisable() {
        getLogger().info("FancyHelperUpdateService Disabled");
    }

    /**
     * 重载 FancyHelper 插件
     * 先禁用插件，然后重新加载
     */
    private void reloadFancyHelper() {
        var fancyHelper = Bukkit.getPluginManager().getPlugin(FANCYHELPER_PLUGIN_NAME);

        if (fancyHelper == null) {
            getLogger().warning("FancyHelper plugin not found! Skipping reload.");
            return;
        }

        if (fancyHelper == this) {
            getLogger().info("Detected that this is FancyHelperUpdateService itself. Skipping self-reload.");
            return;
        }

        getLogger().info("Found FancyHelper plugin: " + fancyHelper.getName() + " v" + fancyHelper.getDescription().getVersion());

        // 先禁用插件
        if (fancyHelper.isEnabled()) {
            getLogger().info("Disabling FancyHelper...");
            Bukkit.getPluginManager().disablePlugin(fancyHelper);
            getLogger().info("FancyHelper disabled successfully.");
        } else {
            getLogger().info("FancyHelper is already disabled.");
        }

        // 重新加载插件
        getLogger().info("Reloading FancyHelper...");
        if (PluginReloadManager.reloadPlugin(FANCYHELPER_PLUGIN_NAME)) {
            getLogger().info("FancyHelper reloaded successfully!");
        } else {
            getLogger().severe("Failed to reload FancyHelper!");
        }
    }
}