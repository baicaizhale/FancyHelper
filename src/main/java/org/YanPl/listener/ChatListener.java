package org.YanPl.listener;

import org.YanPl.MineAgent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public class ChatListener implements Listener {
    private final MineAgent plugin;

    public ChatListener(MineAgent plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage();

        // 如果 CLIManager 处理了该聊天（即处于 CLI 模式），则拦截该消息
        if (plugin.getCliManager().handleChat(player, message)) {
            event.getRecipients().clear();
            event.setCancelled(true);
        }
    }
}
