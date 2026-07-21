package org.YanPl.listener;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.YanPl.FancyHelper;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
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
        Player player = event.getPlayer();

        // 检查玩家是否有预加载的会话，如果有则静默进入CLI模式
        if (plugin.getCliManager().hasPreloadedSession(player.getUniqueId())) {
            if (plugin.getConfigManager().isDebug()) {
                plugin.getLogger().info("[ChatListener] 玩家 " + player.getName() + " 有预加载的会话，静默进入CLI模式");
            }
            // 延迟1秒后自动进入CLI模式，确保玩家完全加载
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!plugin.isEnabled() || !player.isOnline()) return;
                plugin.getCliManager().enterCLI(player, true);
            }, 20L); // 1秒 = 20 ticks
        }

    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        // 即使 paperChatEventExists 为 true（Paper 服务端），也要处理 Bukkit 事件，
        // 因为 TrChat 等聊天插件可能监听 Bukkit 的 AsyncPlayerChatEvent，
        // 而 Paper 为了兼容性会同时触发 Bukkit 事件。
        // 如果只处理 Paper 的 AsyncChatEvent，Bukkit 事件仍会被其他插件收到并广播。

        String message = event.getMessage();
        Player player = event.getPlayer();
        if (!plugin.getCliManager().handleChat(player, message)) {
            if (plugin.getCliManager().isInCLI(player)) {
                if (message.startsWith("！")) {
                    event.setMessage(message.substring(1));
                } else if (message.startsWith("!")) {
                    event.setMessage(message.substring(1));
                }
            }
            return;
        }
        event.getRecipients().clear();
        event.setCancelled(true);
    }

    /**
     * 拦截 CLI 模式下玩家误输 /stop 和 /exit 等命令
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getCliManager().isInCLI(player)) return;

        String cmd = event.getMessage().toLowerCase().split(" ")[0];

        if (cmd.equals("/stop")) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.YELLOW + "⚡ 检测到 /stop，是否想输入 stop 打断 AI？已为你执行打断。");
            plugin.getCliManager().handleChat(player, "stop");
        } else if (cmd.equals("/exit")) {
            event.setCancelled(true);
            TextComponent msg = new TextComponent(ChatColor.GRAY + "你是否想退出 CLI 模式？ ");
            TextComponent escapeBtn = new TextComponent(ChatColor.GOLD + "" + ChatColor.BOLD + "[ Escape ]");
            escapeBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cli exit"));
            escapeBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("点击退出 CLI 模式")));
            msg.addExtra(escapeBtn);
            player.spigot().sendMessage(msg);
        }
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

                        if (!plugin.getCliManager().handleChat(player, message)) {
                            if (plugin.getCliManager().isInCLI(player)) {
                                if (message.startsWith("！") || message.startsWith("!")) {
                                    String newMessage = message.substring(1);
                                    Class<?> componentClass = Class.forName("net.kyori.adventure.text.Component");
                                    Method textMethod = componentClass.getMethod("text", String.class);
                                    Object newComponent = textMethod.invoke(null, newMessage);
                                    Method setMethod = event.getClass().getMethod("message", componentClass);
                                    setMethod.invoke(event, newComponent);
                                }
                            }
                            return;
                        }
                        // 清空消息内容，防止 TrChat 等插件在 HIGHEST 优先级（ignoreCancelled=true）
                        // 仍然读取并广播原始消息
                        Class<?> componentClass = Class.forName("net.kyori.adventure.text.Component");
                        Method emptyMethod = componentClass.getMethod("empty");
                        Object emptyComponent = emptyMethod.invoke(null);
                        Method setMessageMethod = event.getClass().getMethod("message", componentClass);
                        setMessageMethod.invoke(event, emptyComponent);
                        try {
                            Method setCancelledMethod = event.getClass().getMethod("setCancelled", boolean.class);
                            setCancelledMethod.invoke(event, true);
                        } catch (Exception ignored) {}
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
}
