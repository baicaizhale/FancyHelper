package org.YanPl.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.YanPl.FancyHelper;
import org.YanPl.gui.GUI;
import org.YanPl.gui.GUIManager;

/**
 * GUI事件监听器，处理Inventory点击和关闭事件
 */
public class GUIListener implements Listener {

    private final FancyHelper plugin;
    private final GUIManager guiManager;

    public GUIListener(FancyHelper plugin, GUIManager guiManager) {
        this.plugin = plugin;
        this.guiManager = guiManager;
    }

    /**
     * 处理Inventory点击事件
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        Inventory inventory = event.getClickedInventory();

        // 检查是否为GUI Inventory
        if (inventory == null || !(inventory.getHolder() instanceof GUI)) {
            return;
        }

        // 取消事件，防止玩家操作物品
        event.setCancelled(true);

        // 直接从Inventory的Holder获取GUI
        GUI gui = (GUI) inventory.getHolder();
        gui.handleClick(event);
    }

    /**
     * 处理Inventory关闭事件
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getPlayer();
        Inventory inventory = event.getInventory();

        // 检查是否为GUI Inventory
        if (inventory == null || !(inventory.getHolder() instanceof GUI)) {
            return;
        }

        GUI closedGUI = (GUI) inventory.getHolder();

        // 只处理栈顶GUI的关闭事件
        // 如果关闭的不是栈顶GUI，说明是在打开新GUI时触发的关闭事件，忽略它
        GUI currentGUI = guiManager.getCurrentGUI(player);
        if (currentGUI == null || !currentGUI.equals(closedGUI)) {
            return;
        }

        // 返回上一级GUI（如果有），否则完全关闭
        guiManager.backToPrev(player);
    }
}