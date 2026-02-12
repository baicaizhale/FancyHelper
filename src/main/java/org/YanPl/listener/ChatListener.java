package org.YanPl.listener;

import org.YanPl.FancyHelper;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.lang.reflect.Method;

public class ChatListener implements Listener {
    private final FancyHelper plugin;
    private static boolean paperChatEventExists = false;

    static {
        try {
            Class.forName("io.papermc.paper.event.player.AsyncChatEvent");
            paperChatEventExists = true;
        } catch (ClassNotFoundException ignored) {}
    }

    public ChatListener(FancyHelper plugin) {
        this.plugin = plugin;
        if (paperChatEventExists) {
            registerPaperChatListener();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.getCliManager().exitCLI(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        // 检查配置是否启用公告显示
        if (!plugin.getConfigManager().isNoticeShowOnJoin()) {
            return;
        }

        Player player = event.getPlayer();
        plugin.getNoticeManager().fetchNoticeAsync().thenAccept(noticeData -> {
            if (noticeData != null) {
                plugin.getNoticeManager().showNoticeToPlayer(player, noticeData);
            }
        });
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        // 如果是 Paper 环境且我们已经注册了现代监听器，则跳过此事件以避免重复处理
        if (paperChatEventExists) return;
        
        handleChatInternal(event.getPlayer(), event.getMessage(), () -> {
            event.getRecipients().clear();
            event.setCancelled(true);
        });
    }

    /**
     * 使用反射手动注册 Paper 的 AsyncChatEvent，以避免直接引用类导致的编译或加载问题
     * 同时也避开了 @EventHandler 无法标记基类 Event 的限制
     */
    private void registerPaperChatListener() {
        try {
            @SuppressWarnings("unchecked")
            Class<? extends org.bukkit.event.Event> asyncChatEventClass = 
                (Class<? extends org.bukkit.event.Event>) Class.forName("io.papermc.paper.event.player.AsyncChatEvent");
            
            plugin.getServer().getPluginManager().registerEvent(
                asyncChatEventClass,
                this,
                EventPriority.LOWEST,
                (listener, event) -> {
                    if (!asyncChatEventClass.isInstance(event)) return;
                    try {
                        Method getPlayerMethod = event.getClass().getMethod("getPlayer");
                        Method messageMethod = event.getClass().getMethod("message");
                        Player player = (Player) getPlayerMethod.invoke(event);
                        
                        // Paper 使用 Adventure Component, 需要提取纯文本
                        Object component = messageMethod.invoke(event);
                        Method plainTextMethod = Class.forName("net.kyori.adventure.text.serializer.plain.PlainComponentSerializer").getMethod("plain");
                        Object serializer = plainTextMethod.invoke(null);
                        Method serializeMethod = serializer.getClass().getMethod("serialize", Class.forName("net.kyori.adventure.text.Component"));
                        String message = (String) serializeMethod.invoke(serializer, component);

                        handleChatInternal(player, message, () -> {
                            try {
                                Method setCancelledMethod = event.getClass().getMethod("setCancelled", boolean.class);
                                setCancelledMethod.invoke(event, true);
                            } catch (Exception ignored) {}
                        });
                    } catch (Exception e) {
                        plugin.getLogger().warning("处理 Paper 聊天事件时出错: " + e.getMessage());
                    }
                },
                plugin,
                true
            );
            plugin.getLogger().info("已注册 Paper 现代聊天事件监听。");
        } catch (Exception e) {
            plugin.getLogger().warning("无法注册 Paper 聊天监听器，将回退到标准监听器: " + e.getMessage());
            paperChatEventExists = false;
        }
    }

    private void handleChatInternal(Player player, String message, Runnable cancelAction) {
        if (plugin.getCliManager().handleChat(player, message)) {
            cancelAction.run();
        }
    }
}
