package org.YanPl.gui;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.entity.Player;
import org.YanPl.FancyHelper;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 文本编辑器
 * 使用聊天输入方式提供文本编辑功能
 */
public class SignEditor {

    // 静态回调映射
    private static final Map<UUID, Consumer<String>> callbacks = new ConcurrentHashMap<>();

    private final FancyHelper plugin;
    private final Player player;
    private final String currentText;

    /**
     * 构造函数
     *
     * @param plugin     插件实例
     * @param player     玩家
     * @param currentText 当前文本
     * @param callback   编辑完成回调
     */
    public SignEditor(FancyHelper plugin, Player player, String currentText, Consumer<String> callback) {
        this.plugin = plugin;
        this.player = player;
        this.currentText = currentText;
    }

    /**
     * 打开编辑器
     */
    public void open(Consumer<String> callback) {
        UUID uuid = player.getUniqueId();

        // 保存回调到静态映射
        callbacks.put(uuid, callback);

        // 显示提示信息
        player.sendMessage(ChatColor.GRAY + "========== 文本编辑 ==========");
        player.sendMessage(ChatColor.GRAY + "当前内容:");
        player.sendMessage(ChatColor.WHITE + currentText.replace("\n", "\n" + ChatColor.WHITE));
        player.sendMessage(ChatColor.GRAY + "=============================");
        player.sendMessage(ChatColor.YELLOW + "请在聊天框输入新的文本内容，输入 'cancel' 取消编辑");
        player.sendMessage(ChatColor.GRAY + "支持使用 \\n 表示换行");
    }

    /**
     * 处理玩家输入（由ChatListener调用）
     *
     * @param player 玩家
     * @param input  输入内容
     * @return 是否已处理
     */
    public static boolean handleInput(Player player, String input) {
        UUID uuid = player.getUniqueId();
        Consumer<String> callback = callbacks.get(uuid);

        if (callback == null) {
            return false;
        }

        // 移除回调
        callbacks.remove(uuid);

        // 检查是否取消
        if (input.equalsIgnoreCase("cancel")) {
            player.sendMessage(ChatColor.GRAY + "» " + ChatColor.YELLOW + "已取消编辑");
            return true;
        }

        // 处理换行符
        String processedInput = input.replace("\\n", "\n");

        // 调用回调
        callback.accept(processedInput);

        return true;
    }

    /**
     * 检查玩家是否在编辑文本
     *
     * @param player 玩家
     * @return 是否在编辑
     */
    public static boolean isEditing(Player player) {
        return callbacks.containsKey(player.getUniqueId());
    }

    /**
     * 取消玩家的文本编辑
     *
     * @param player 玩家
     */
    public static void cancelEdit(Player player) {
        callbacks.remove(player.getUniqueId());
    }
}