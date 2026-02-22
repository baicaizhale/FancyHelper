package org.YanPl.gui;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.YanPl.FancyHelper;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GUI管理器，负责管理GUI栈
 */
public class GUIManager {

    private final FancyHelper plugin;
    private final Map<UUID, Deque<GUI>> guiStack;

    public GUIManager(FancyHelper plugin) {
        this.plugin = plugin;
        this.guiStack = new ConcurrentHashMap<>();
    }

    /**
     * 打开新GUI（压栈）
     *
     * @param player 玩家
     * @param gui    要打开的GUI
     */
    public void openGUI(Player player, GUI gui) {
        UUID uuid = player.getUniqueId();

        // 初始化栈
        guiStack.putIfAbsent(uuid, new ArrayDeque<>());

        // 将当前GUI压入栈
        Deque<GUI> stack = guiStack.get(uuid);

        // 检查栈顶是否就是当前GUI
        if (!stack.isEmpty() && stack.peek() == gui) {
            // 已经在栈顶，直接打开
            gui.open();
            return;
        }

        // 否则压入栈
        stack.push(gui);

        // 打开新GUI
        gui.open();
    }

    /**
     * 关闭当前GUI（弹栈并关闭所有）
     *
     * @param player 玩家
     */
    public void closeGUI(Player player) {
        UUID uuid = player.getUniqueId();
        Deque<GUI> stack = guiStack.get(uuid);

        if (stack != null && !stack.isEmpty()) {
            // 关闭所有GUI
            while (!stack.isEmpty()) {
                GUI g = stack.pop();
                g.onClose();
            }
            guiStack.remove(uuid);
        }

        player.closeInventory();
    }

    /**
     * 返回上一级（弹出当前，显示上一个）
     *
     * @param player 玩家
     * @return 是否成功返回
     */
    public boolean backToPrev(Player player) {
        UUID uuid = player.getUniqueId();
        Deque<GUI> stack = guiStack.get(uuid);

        if (stack == null || stack.isEmpty()) {
            // 栈为空，直接关闭
            player.closeInventory();
            return false;
        }

        // 弹出当前GUI
        GUI currentGUI = stack.pop();
        currentGUI.onClose();

        // 如果栈不为空，打开上一个GUI
        if (!stack.isEmpty()) {
            GUI prevGUI = stack.peek();
            // 直接打开，不使用runTask
            prevGUI.open();
            return true;
        } else {
            // 栈已空，关闭Inventory
            return false;
        }
    }

    /**
     * 获取当前GUI
     *
     * @param player 玩家
     * @return 当前GUI，如果没有则返回null
     */
    public GUI getCurrentGUI(Player player) {
        UUID uuid = player.getUniqueId();
        Deque<GUI> stack = guiStack.get(uuid);

        if (stack != null && !stack.isEmpty()) {
            return stack.peek();
        }
        return null;
    }

    /**
     * 清空玩家GUI栈
     *
     * @param player 玩家
     */
    public void clearStack(Player player) {
        UUID uuid = player.getUniqueId();
        Deque<GUI> stack = guiStack.get(uuid);

        if (stack != null) {
            while (!stack.isEmpty()) {
                GUI gui = stack.pop();
                gui.onClose();
            }
            guiStack.remove(uuid);
        }
    }

    /**
     * 处理玩家退出事件
     *
     * @param player 玩家
     */
    public void onPlayerQuit(Player player) {
        clearStack(player);
    }

    /**
     * 获取插件实例
     *
     * @return 插件实例
     */
    public FancyHelper getPlugin() {
        return plugin;
    }
}