package org.YanPl.UpdateService;

import org.bukkit.plugin.java.JavaPlugin;

public class FancyHelperUpdateService extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("FancyHelperUpdateService Enabled");
    }

    @Override
    public void onDisable() {
        getLogger().info("FancyHelperUpdateService Disabled");
    }
}