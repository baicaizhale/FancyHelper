package org.YanPl.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.YanPl.FancyHelper;

import java.util.UUID;

/**
 * GUI抽象基类，定义所有GUI的通用接口
 */
public abstract class GUI implements InventoryHolder {

    protected final FancyHelper plugin;
    protected final Player player;
    protected final UUID playerUUID;
    protected Inventory inventory;

    /**
     * 构造函数
     *
     * @param plugin 插件实例
     * @param player 玩家
     */
    public GUI(FancyHelper plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.playerUUID = player.getUniqueId();
    }

    /**
     * 打开GUI
     */
    public abstract void open();

    /**
     * 刷新GUI显示
     */
    public abstract void refresh();

    /**
     * 处理点击事件
     *
     * @param event 点击事件
     */
    public abstract void handleClick(InventoryClickEvent event);

    /**
     * 获取GUI标题
     *
     * @return 标题
     */
    public abstract String getTitle();

    /**
     * 关闭GUI时的清理
     */
    public abstract void onClose();

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    /**
     * 获取玩家
     *
     * @return 玩家
     */
    public Player getPlayer() {
        return player;
    }

    /**
     * 获取玩家UUID
     *
     * @return 玩家UUID
     */
    public UUID getPlayerUUID() {
        return playerUUID;
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