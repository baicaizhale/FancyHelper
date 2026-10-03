package org.YanPl.listener;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.YanPl.FancyHelper;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.YanPl.util.I18n;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ChatListener implements Listener {
    private final FancyHelper plugin;
    private static boolean paperChatEventExists = false;

    /**
     * Bukkit 与 Paper 两套聊天事件会为同一条消息各触发一次 handleChat。
     * 第一套事件处理完后，第二套事件拿到的是处理过的文本（如剥掉"！"后），
     * 会绕过前缀判断被当成普通聊天再次送进 AI（实测复现：CLI 中发"！xxx"广播后
     * AI 又把内容当成聊天处理了一遍）。同一玩家两次 handleChat 间隔必然远大于
     * 手动打字速度，因此按时间窗去重：窗口内的第二次调用跳过 handleChat，
     * 但保留该事件自己的"！"剥离逻辑。
     */
    private static final long DUPLICATE_EVENT_WINDOW_MS = 200;
    private final Map<UUID, Long> lastChatHandledAt = new ConcurrentHashMap<>();

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
        lastChatHandledAt.remove(event.getPlayer().getUniqueId());
        plugin.getInitWizardManager().cleanup(event.getPlayer().getUniqueId());
        plugin.getCliManager().exitCLI(event.getPlayer());
    }

    /**
     * 判断是否为同一条消息的第二套聊天事件（详见 DUPLICATE_EVENT_WINDOW_MS 注释）。
     * 无论是否重复都要刷新时间戳，避免三连事件时窗口失效。
     */
    private boolean isDuplicateChatEvent(Player player) {
        long now = System.currentTimeMillis();
        Long last = lastChatHandledAt.put(player.getUniqueId(), now);
        return last != null && now - last < DUPLICATE_EVENT_WINDOW_MS;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        // 已禁用：插件重载/重启后不再自动恢复会话，改为玩家手动 /cli resume。
        // Player player = event.getPlayer();
        // if (plugin.getCliManager().hasPreloadedSession(player.getUniqueId())) {
        //     if (plugin.getConfigManager().isDebug()) {
        //         plugin.getLogger().info("[ChatListener] 玩家 " + player.getName() + " 有预加载的会话，静默进入CLI模式");
        //     }
        //     Bukkit.getScheduler().runTaskLater(plugin, () -> {
        //         if (!plugin.isEnabled() || !player.isOnline()) return;
        //         plugin.getCliManager().enterCLI(player, true);
        //     }, 20L); // 1秒 = 20 ticks
        // }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        // 即使 paperChatEventExists 为 true（Paper 服务端），也要处理 Bukkit 事件，
        // 因为 TrChat 等聊天插件可能监听 Bukkit 的 AsyncPlayerChatEvent，
        // 而 Paper 为了兼容性会同时触发 Bukkit 事件。
        // 如果只处理 Paper 的 AsyncChatEvent，Bukkit 事件仍会被其他插件收到并广播。

        String message = event.getMessage();
        Player player = event.getPlayer();
        boolean duplicate = isDuplicateChatEvent(player);
        if (!duplicate && plugin.getCliManager().handleChat(player, message)) {
            event.getRecipients().clear();
            event.setCancelled(true);
            return;
        }
        if (plugin.getCliManager().isInCLI(player)) {
            if (message.startsWith("！")) {
                event.setMessage(message.substring(1));
            } else if (message.startsWith("!")) {
                event.setMessage(message.substring(1));
            }
        }
    }

    /**
     * 拦截 CLI 模式下玩家误输 /stop 和 /exit 等命令
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getCliManager().isInCLI(player)) {
            // 初始化向导玩家：新手常把"停"打成 /stop，这里拦下当作退出向导，避免误停服务器
            if (plugin.getInitWizardManager().isActive(player)) {
                String cmd = event.getMessage().toLowerCase().split(" ")[0];
                if (cmd.equals("/stop") || cmd.equals("/exit") || cmd.equals("/quit")) {
                    event.setCancelled(true);
                    plugin.getInitWizardManager().cleanup(player.getUniqueId());
                    player.sendMessage(I18n.t("wizard.exited"));
                    player.sendMessage(I18n.t("wizard.exited.hint"));
                }
            }
            return;
        }

        String cmd = event.getMessage().toLowerCase().split(" ")[0];

        if (cmd.equals("/stop")) {
            event.setCancelled(true);
            player.sendMessage(I18n.t("chat.stop.detected"));
            plugin.getCliManager().handleChat(player, "stop");
        } else if (cmd.equals("/exit")) {
            event.setCancelled(true);
            TextComponent msg = new TextComponent(TextComponent.fromLegacyText(I18n.t("chat.exit.ask")));
            TextComponent escapeBtn = new TextComponent(TextComponent.fromLegacyText(I18n.t("chat.escape")));
            escapeBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cli exit"));
            escapeBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(I18n.t("chat.exit.hover"))));
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

                        boolean duplicate = isDuplicateChatEvent(player);
                        if (!duplicate && plugin.getCliManager().handleChat(player, message)) {
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
                            return;
                        }
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
