package org.YanPl.manager;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.chat.ComponentSerializer;
import org.YanPl.FancyHelper;
import org.YanPl.util.ColorUtil;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 数据包捕获管理器，使用 ProtocolLib 拦截发送给玩家的系统消息
 */
public class PacketCaptureManager {
    private final FancyHelper plugin;
    private final Map<UUID, StringBuilder> captureBuffers = new ConcurrentHashMap<>();
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
                            // 用 stripToPlainText 去除 [marker] 和 § 码后再过滤
                            String stripped = ColorUtil.stripToPlainText(message).trim();
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

                            // 过滤掉明显的玩家聊天消息
                            if (stripped.matches("^<[^>]+>.*") ||
                                stripped.matches("^\\[.*\\]\\s*<[^>]+>.*") ||
                                stripped.matches("^\\* [^ ]+ .*")) {
                                return;
                            }

                            StringBuilder buffer = captureBuffers.get(uuid);
                            synchronized (buffer) {
                                if (buffer.length() > 0) buffer.append("\n");
                                buffer.append(message);
                            }
                        }
                    }
                }
            }
        );
    }

    /**
     * 从数据包中提取文本消息，颜色信息转为 AI 可读格式。
     * <p>
     * 输出的颜色标记格式：{@code [green]}、{@code [#FF5500]}、{@code [bold]}。
     * 不含任何 {@code §} legacy 码，AI 可以直接理解。
     */
    private String extractMessage(PacketContainer packet) {
        try {
            if (packet.getType() == PacketType.Play.Server.SYSTEM_CHAT) {
                Object content = packet.getModifier().read(0);
                if (content instanceof String) {
                    try {
                        return ColorUtil.componentsToReadable(ComponentSerializer.parse((String) content));
                    } catch (Exception e) {
                        return (String) content;
                    }
                } else if (content instanceof BaseComponent) {
                    return ColorUtil.componentToReadable((BaseComponent) content);
                } else if (content instanceof BaseComponent[]) {
                    return ColorUtil.componentsToReadable((BaseComponent[]) content);
                } else if (content instanceof WrappedChatComponent) {
                    String json = ((WrappedChatComponent) content).getJson();
                    return ColorUtil.componentsToReadable(ComponentSerializer.parse(json));
                }
                Object fallback = packet.getChatComponents().read(0);
                if (fallback instanceof WrappedChatComponent) {
                    String json = ((WrappedChatComponent) fallback).getJson();
                    return ColorUtil.componentsToReadable(ComponentSerializer.parse(json));
                }
            } else if (packet.getType() == PacketType.Play.Server.CHAT) {
                Object content = packet.getChatComponents().read(0);
                if (content != null) {
                    if (content instanceof BaseComponent[]) {
                        return ColorUtil.componentsToReadable((BaseComponent[]) content);
                    } else if (content instanceof BaseComponent) {
                        return ColorUtil.componentToReadable((BaseComponent) content);
                    } else if (content instanceof WrappedChatComponent) {
                        String json = ((WrappedChatComponent) content).getJson();
                        return ColorUtil.componentsToReadable(ComponentSerializer.parse(json));
                    }
                }
            } else if (packet.getType() == PacketType.Play.Server.SET_ACTION_BAR_TEXT
                    || packet.getType() == PacketType.Play.Server.SET_TITLE_TEXT
                    || packet.getType() == PacketType.Play.Server.SET_SUBTITLE_TEXT) {
                Object content = packet.getChatComponents().read(0);
                if (content instanceof WrappedChatComponent) {
                    String json = ((WrappedChatComponent) content).getJson();
                    return ColorUtil.componentsToReadable(ComponentSerializer.parse(json));
                } else if (content instanceof BaseComponent[]) {
                    return ColorUtil.componentsToReadable((BaseComponent[]) content);
                } else if (content instanceof BaseComponent) {
                    return ColorUtil.componentToReadable((BaseComponent) content);
                }
            }
        } catch (Exception e) {
            // 忽略提取错误
        }
        return null;
    }

    /**
     * 开始为指定玩家捕获数据包
     */
    public void startCapture(Player player) {
        if (!enabled) return;
        captureBuffers.put(player.getUniqueId(), new StringBuilder());
    }

    /**
     * 停止为指定玩家捕获数据包并返回捕获的内容
     */
    public String stopCapture(Player player) {
        if (!enabled) return "";
        StringBuilder sb = captureBuffers.remove(player.getUniqueId());
        return sb != null ? sb.toString() : "";
    }

    /**
     * 检查是否正在为该玩家捕获数据包
     */
    public boolean isCapturing(Player player) {
        return captureBuffers.containsKey(player.getUniqueId());
    }

    /**
     * 获取当前捕获的内容（不清除）
     */
    public String peekCapture(Player player) {
        if (!enabled) return "";
        StringBuilder sb = captureBuffers.get(player.getUniqueId());
        return sb != null ? sb.toString() : "";
    }
}
