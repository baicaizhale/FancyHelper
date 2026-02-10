package org.YanPl.manager;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import org.YanPl.FancyHelper;
import org.bukkit.ChatColor;
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
            new PacketAdapter(plugin, PacketType.Play.Server.SYSTEM_CHAT, PacketType.Play.Server.CHAT) {
                @Override
                public void onPacketSending(PacketEvent event) {
                    Player player = event.getPlayer();
                    if (player == null) return;
                    
                    UUID uuid = player.getUniqueId();
                    if (captureBuffers.containsKey(uuid)) {
                        String message = extractMessage(event.getPacket());
                        if (message != null && !message.isEmpty()) {
                            // 过滤掉插件自身的提示消息，避免循环反馈或干扰 AI
                            String stripped = ChatColor.stripColor(message);
                            if (stripped.startsWith("⇒") || stripped.startsWith("◇") || stripped.startsWith("◆") || stripped.contains("FancyHelper")) {
                                return;
                            }

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
                }
            } else if (packet.getType() == PacketType.Play.Server.CHAT) {
                // 旧版本或 1.18 聊天消息
                Object content = packet.getChatComponents().read(0);
                if (content != null) {
                    if (content instanceof BaseComponent[]) {
                        return TextComponent.toPlainText((BaseComponent[]) content);
                    } else if (content instanceof BaseComponent) {
                        return TextComponent.toPlainText((BaseComponent) content);
                    }
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
}
