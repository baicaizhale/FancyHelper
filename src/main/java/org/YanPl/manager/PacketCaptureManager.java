package org.YanPl.manager;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.chat.ComponentSerializer;
import org.YanPl.FancyHelper;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 数据包捕获管理器，使用 ProtocolLib 拦截发送给玩家的系统消息
 */
public class PacketCaptureManager {
    private final FancyHelper plugin;
    private final Map<UUID, StringBuilder> captureBuffers = new ConcurrentHashMap<>();
    private final Set<String> recentBroadcastMessages = ConcurrentHashMap.newKeySet();
    private boolean enabled = false;

    /**
     * 初始化数据包捕获管理器
     * 
     * @param plugin 插件实例
     */
    public PacketCaptureManager(FancyHelper plugin) {
        this.plugin = plugin;
        try {
            registerListener();
            enabled = true;
            plugin.getLogger().info("[PacketCapture] 已检测到 ProtocolLib，启用数据包级命令输出捕获。");
        } catch (Exception e) {
            plugin.getLogger().warning("[PacketCapture] 注册 ProtocolLib 监听器失败: " + e.getMessage());
        }
    }

    /**
     * 注册数据包监听器
     */
    private void registerListener() {
        ProtocolLibrary.getProtocolManager().addPacketListener(
            new PacketAdapter(plugin,
                    PacketType.Play.Server.SYSTEM_CHAT,
                    PacketType.Play.Server.CHAT,
                    PacketType.Play.Server.SET_ACTION_BAR_TEXT,
                    PacketType.Play.Server.SET_TITLE_TEXT,
                    PacketType.Play.Server.SET_SUBTITLE_TEXT) {
                @Override
                public void onPacketSending(PacketEvent event) {
                    Player player = event.getPlayer();
                    if (player == null) return;
                    
                    UUID uuid = player.getUniqueId();
                    if (captureBuffers.containsKey(uuid)) {
                        String message = extractMessage(event.getPacket());
                        if (message != null && !message.isEmpty()) {
                            // 过滤掉插件自身的提示消息，避免循环反馈或干扰 AI
                            String stripped = ChatColor.stripColor(message).trim();
                            if (stripped.isEmpty() || 
                                stripped.startsWith("⇒") || 
                                stripped.startsWith("◇") || 
                                stripped.startsWith("◆") || 
                                stripped.contains("FancyHelper") ||
                                stripped.equals("....") ||
                                stripped.equals("...") ||
                                stripped.equals("- ✓ -") ||
                                stripped.equals("- ✕ -") ||
                                stripped.equals("- ERROR -") ||
                                stripped.equals("正在征求您的许可...") ||
                                stripped.equals("正在征求您的意见...") ||
                                stripped.matches("^- 思考中 \\d+s -$")) {
                                return;
                            }

                            // 关键修复：过滤掉明显的玩家聊天消息，防止多玩家环境下上下文混淆
                            // 匹配标准聊天格式 <PlayerName> Message 或 [World] <PlayerName> Message
                            if (stripped.matches("^<[^>]+>.*") || 
                                stripped.matches("^\\[.*\\]\\s*<[^>]+>.*") ||
                                stripped.matches("^\\* [^ ]+ .*")) { // /me 命令格式
                                return;
                            }

                            // 过滤常见的广播消息格式，防止多管理员环境下上下文互串
                            boolean isBracketFormat = stripped.matches("^\\[[^\\]]+\\].*") || stripped.matches("^【[^】]+】.*");
                            boolean isLogPrefix = stripped.matches("^\\[(?i)(INFO|ERROR|WARN|DEBUG|WARNING|信息|错误|警告|调试)\\].*")
                                    || stripped.matches("^【(信息|错误|警告|调试)】.*");
                            if (isBracketFormat && !stripped.contains("<") && !isLogPrefix) {
                                return;
                            }

                            // 重复广播检测：如果消息最近已被其他玩家捕获，则跳过（防止多管理员上下文互串）
                            // 但日志前缀消息除外，因为它们可能是命令输出的一部分
                            if (!isLogPrefix && recentBroadcastMessages.contains(stripped)) {
                                return;
                            }
                            recentBroadcastMessages.add(stripped);
                            // 3秒后自动清除，避免内存泄漏
                            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                                recentBroadcastMessages.remove(stripped);
                            }, 60L); // 3秒 = 60 ticks

                            StringBuilder buffer = captureBuffers.get(uuid);
                            synchronized (buffer) {
                                if (buffer.length() > 0) buffer.append("\n");
                                buffer.append(stripped);
                            }
                        }
                    }
                }
            }
        );
    }

    /**
     * 从数据包中提取文本消息
     * 
     * @param packet 数据包容器
     * @return 提取出的文本内容，如果无法提取则返回 null
     */
    private String extractMessage(PacketContainer packet) {
        try {
            if (packet.getType() == PacketType.Play.Server.SYSTEM_CHAT) {
                // 1.19+ 系统聊天消息
                // 字段 0 可能是 JSON 字符串或 BaseComponent
                Object content = packet.getModifier().read(0);
                if (content instanceof String) {
                    try {
                        return TextComponent.toPlainText(net.md_5.bungee.chat.ComponentSerializer.parse((String) content));
                    } catch (Exception e) {
                        return (String) content;
                    }
                } else if (content instanceof BaseComponent) {
                    return TextComponent.toPlainText((BaseComponent) content);
                } else if (content instanceof BaseComponent[]) {
                    return TextComponent.toPlainText((BaseComponent[]) content);
                } else if (content instanceof WrappedChatComponent) {
                    String json = ((WrappedChatComponent) content).getJson();
                    return TextComponent.toPlainText(ComponentSerializer.parse(json));
                }
                Object fallback = packet.getChatComponents().read(0);
                if (fallback instanceof WrappedChatComponent) {
                    String json = ((WrappedChatComponent) fallback).getJson();
                    return TextComponent.toPlainText(ComponentSerializer.parse(json));
                }
            } else if (packet.getType() == PacketType.Play.Server.CHAT) {
                // 旧版本或 1.18 聊天消息
                Object content = packet.getChatComponents().read(0);
                if (content != null) {
                    if (content instanceof BaseComponent[]) {
                        return TextComponent.toPlainText((BaseComponent[]) content);
                    } else if (content instanceof BaseComponent) {
                        return TextComponent.toPlainText((BaseComponent) content);
                    } else if (content instanceof WrappedChatComponent) {
                        String json = ((WrappedChatComponent) content).getJson();
                        return TextComponent.toPlainText(ComponentSerializer.parse(json));
                    }
                }
            } else if (packet.getType() == PacketType.Play.Server.SET_ACTION_BAR_TEXT
                    || packet.getType() == PacketType.Play.Server.SET_TITLE_TEXT
                    || packet.getType() == PacketType.Play.Server.SET_SUBTITLE_TEXT) {
                Object content = packet.getChatComponents().read(0);
                if (content instanceof WrappedChatComponent) {
                    String json = ((WrappedChatComponent) content).getJson();
                    return TextComponent.toPlainText(ComponentSerializer.parse(json));
                } else if (content instanceof BaseComponent[]) {
                    return TextComponent.toPlainText((BaseComponent[]) content);
                } else if (content instanceof BaseComponent) {
                    return TextComponent.toPlainText((BaseComponent) content);
                }
            }
        } catch (Exception e) {
            // 忽略提取错误，可能是由于版本差异导致字段索引不同
        }
        return null;
    }

    /**
     * 开始为指定玩家捕获数据包
     * 
     * @param player 目标玩家
     */
    public void startCapture(Player player) {
        if (!enabled) return;
        captureBuffers.put(player.getUniqueId(), new StringBuilder());
    }

    /**
     * 停止为指定玩家捕获数据包并返回捕获的内容
     * 
     * @param player 目标玩家
     * @return 捕获到的所有消息合并后的字符串
     */
    public String stopCapture(Player player) {
        if (!enabled) return "";
        StringBuilder sb = captureBuffers.remove(player.getUniqueId());
        return sb != null ? sb.toString() : "";
    }

    /**
     * 检查是否正在为该玩家捕获数据包
     * 
     * @param player 目标玩家
     * @return 如果正在捕获则返回 true
     */
    public boolean isCapturing(Player player) {
        return captureBuffers.containsKey(player.getUniqueId());
    }

    /**
     * 获取当前捕获的内容（不清除）
     * 
     * @param player 目标玩家
     * @return 当前捕获的内容，如果未开始捕获则返回空字符串
     */
    public String peekCapture(Player player) {
        if (!enabled) return "";
        StringBuilder sb = captureBuffers.get(player.getUniqueId());
        return sb != null ? sb.toString() : "";
    }
}
