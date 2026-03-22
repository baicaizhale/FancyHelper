package org.YanPl.manager;

import org.YanPl.FancyHelper;
import org.YanPl.model.DialogueSession;
import org.YanPl.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class GuiManager implements Listener {

    private final FancyHelper plugin;
    private final String SETTINGS_TITLE = ColorUtil.translateCustomColors("&zFancyHelper &8| &7Settings");
    private final String MODE_SELECTION_TITLE = ColorUtil.translateCustomColors("&zFancyHelper &8| &7模式选择");

    public GuiManager(FancyHelper plugin) {
        this.plugin = plugin;
    }

    /**
     * 打开模式选择菜单
     */
    public void openModeSelectionMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 9, MODE_SELECTION_TITLE);

        // 填充背景板
        ItemStack bg = createItem(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, bg);
        }

        // 1. Normal 模式
        ItemStack normalItem = createItem(Material.LIME_DYE, ColorUtil.translateCustomColors("&a&lNormal 模式"), 
                ColorUtil.translateCustomColors("&8&m------------------------"),
                ColorUtil.translateCustomColors("&7普通模式"),
                ColorUtil.translateCustomColors("&7AI 执行敏感操作需手动确认"),
                "",
                ColorUtil.translateCustomColors("&e▸ 点击选择此模式"),
                ColorUtil.translateCustomColors("&8&m------------------------"));
        inv.setItem(2, normalItem);

        // 2. SMART 模式
        ItemStack smartItem = createItem(Material.BLUE_DYE, ColorUtil.translateCustomColors("&9&lSMART 模式"), 
                ColorUtil.translateCustomColors("&8&m------------------------"),
                ColorUtil.translateCustomColors("&7智能模式"),
                ColorUtil.translateCustomColors("&7AI 会评估操作风险，高风险需确认"),
                "",
                ColorUtil.translateCustomColors("&e▸ 点击选择此模式"),
                ColorUtil.translateCustomColors("&8&m------------------------"));
        inv.setItem(4, smartItem);

        // 3. YOLO 模式
        ItemStack yoloItem = createItem(Material.RED_DYE, ColorUtil.translateCustomColors("&c&lYOLO 模式"), 
                ColorUtil.translateCustomColors("&8&m------------------------"),
                ColorUtil.translateCustomColors("&7激进模式"),
                ColorUtil.translateCustomColors("&7AI 将自动执行大部分命令"),
                "",
                ColorUtil.translateCustomColors("&e▸ 点击选择此模式"),
                ColorUtil.translateCustomColors("&8&m------------------------"));
        inv.setItem(6, yoloItem);

        player.openInventory(inv);
    }

    /**
     * 打开设置菜单
     */
    public void openSettingsMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, SETTINGS_TITLE);

        // 填充背景板
        ItemStack bg = createItem(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, bg);
        }

        // 1. 模式切换 (Normal/YOLO)
        updateModeItem(inv, player);

        // 2. 记忆管理
        ItemStack memoryItem = createItem(Material.KNOWLEDGE_BOOK, ColorUtil.translateCustomColors("&b&l记忆管理"), 
                ColorUtil.translateCustomColors("&8&m------------------------"),
                ColorUtil.translateCustomColors("&7查看和管理 AI 的长期记忆"),
                "",
                ColorUtil.translateCustomColors("&e▸ 左键点击: &f查看列表"),
                ColorUtil.translateCustomColors("&c▸ 右键点击: &f清空记忆"),
                ColorUtil.translateCustomColors("&8&m------------------------"));
        inv.setItem(12, memoryItem); 

        // 3. 工具权限管理
        ItemStack toolsItem = createItem(Material.CHEST, ColorUtil.translateCustomColors("&6&l工具权限"), 
                ColorUtil.translateCustomColors("&8&m------------------------"),
                ColorUtil.translateCustomColors("&7管理文件操作相关权限"),
                ColorUtil.translateCustomColors("&7包括: &fls, read, diff"),
                "",
                ColorUtil.translateCustomColors("&e▸ 点击管理"),
                ColorUtil.translateCustomColors("&8&m------------------------"));
        inv.setItem(14, toolsItem); 

        // 4. 状态显示位置 (ActionBar/Subtitle)
        updateDisplayPosItem(inv, player);
        
        // 5. 关闭按钮
        ItemStack closeItem = createItem(Material.BARRIER, ColorUtil.translateCustomColors("&c&l关闭菜单"), 
                ColorUtil.translateCustomColors("&7点击关闭此菜单"));
        inv.setItem(26, closeItem);

        player.openInventory(inv);
    }

    private void updateModeItem(Inventory inv, Player player) {
        UUID uuid = player.getUniqueId();
        DialogueSession session = plugin.getCliManager().getSession(uuid);
        DialogueSession.Mode mode = (session != null) ? session.getMode() : DialogueSession.Mode.NORMAL;

        ItemStack item;
        if (mode == DialogueSession.Mode.NORMAL) {
            item = createItem(Material.LIME_DYE, ColorUtil.translateCustomColors("&a&l当前模式: Normal"), 
                    ColorUtil.translateCustomColors("&8&m------------------------"),
                    ColorUtil.translateCustomColors("&7当前为 &a普通模式"),
                    ColorUtil.translateCustomColors("&7AI 执行敏感操作需手动确认"),
                    "",
                    ColorUtil.translateCustomColors("&e▸ 点击切换至 SMART 模式"),
                    ColorUtil.translateCustomColors("&8&m------------------------"));
        } else if (mode == DialogueSession.Mode.SMART) {
            item = createItem(Material.BLUE_DYE, ColorUtil.translateCustomColors("&9&l当前模式: SMART"), 
                    ColorUtil.translateCustomColors("&8&m------------------------"),
                    ColorUtil.translateCustomColors("&7当前为 &9智能模式"),
                    ColorUtil.translateCustomColors("&7AI 会评估操作风险，高风险需确认"),
                    "",
                    ColorUtil.translateCustomColors("&e▸ 点击切换至 YOLO 模式"),
                    ColorUtil.translateCustomColors("&8&m------------------------"));
        } else {
            item = createItem(Material.RED_DYE, ColorUtil.translateCustomColors("&c&l当前模式: YOLO"), 
                    ColorUtil.translateCustomColors("&8&m------------------------"),
                    ColorUtil.translateCustomColors("&7当前为 &c激进模式"),
                    ColorUtil.translateCustomColors("&7AI 将自动执行大部分命令"),
                    "",
                    ColorUtil.translateCustomColors("&e▸ 点击切换至 Normal 模式"),
                    ColorUtil.translateCustomColors("&8&m------------------------"));
        }
        inv.setItem(10, item); 
    }

    private void updateDisplayPosItem(Inventory inv, Player player) {
        String displayPos = plugin.getConfigManager().getPlayerDisplayPosition(player);
        ItemStack item;
        boolean isActionBar = "actionbar".equalsIgnoreCase(displayPos);
        
        if (isActionBar) {
            item = createItem(Material.NAME_TAG, ColorUtil.translateCustomColors("&e&l显示位置: ActionBar"), 
                    ColorUtil.translateCustomColors("&8&m------------------------"),
                    ColorUtil.translateCustomColors("&7当前显示在: &f快捷栏上方"),
                    "",
                    ColorUtil.translateCustomColors("&e▸ 点击切换至 Subtitle"),
                    ColorUtil.translateCustomColors("&8&m------------------------"));
        } else {
            item = createItem(Material.PAPER, ColorUtil.translateCustomColors("&e&l显示位置: Subtitle"), 
                    ColorUtil.translateCustomColors("&8&m------------------------"),
                    ColorUtil.translateCustomColors("&7当前显示在: &f屏幕中央"),
                    "",
                    ColorUtil.translateCustomColors("&e▸ 点击切换至 ActionBar"),
                    ColorUtil.translateCustomColors("&8&m------------------------"));
        }
        inv.setItem(16, item);
    }

    private ItemStack createItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            List<String> loreList = new ArrayList<>();
            for (String line : lore) {
                loreList.add(line);
            }
            meta.setLore(loreList);
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        if (!title.equals(SETTINGS_TITLE) && !title.equals(MODE_SELECTION_TITLE)) {
            return;
        }

        event.setCancelled(true);
        
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();

        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        int slot = event.getSlot();

        // 模式切换
        if (slot == 10) {
            UUID uuid = player.getUniqueId();
            DialogueSession session = plugin.getCliManager().getSession(uuid);
            DialogueSession.Mode currentMode = (session != null) ? session.getMode() : DialogueSession.Mode.NORMAL;
            
            if (currentMode == DialogueSession.Mode.NORMAL) {
                plugin.getCliManager().switchMode(player, DialogueSession.Mode.SMART);
            } else if (currentMode == DialogueSession.Mode.SMART) {
                plugin.getCliManager().switchMode(player, DialogueSession.Mode.YOLO);
            } else {
                plugin.getCliManager().switchMode(player, DialogueSession.Mode.NORMAL);
            }
            // 刷新图标
            updateModeItem(event.getClickedInventory(), player);
            player.playSound(player.getLocation(), "ui.button.click", 1, 1);
        }
        // 记忆管理
        else if (slot == 12) {
            player.closeInventory();
            player.performCommand("cli memory");
            player.playSound(player.getLocation(), "ui.button.click", 1, 1);
        }
        // 工具权限
        else if (slot == 14) {
            player.closeInventory();
            player.performCommand("cli tools");
            player.playSound(player.getLocation(), "ui.button.click", 1, 1);
        }
        // 显示位置切换
        else if (slot == 16) {
            player.performCommand("cli display");
            // 刷新图标
            updateDisplayPosItem(event.getClickedInventory(), player);
            player.playSound(player.getLocation(), "ui.button.click", 1, 1);
        }
        // 关闭
        else if (slot == 26) {
            player.closeInventory();
            player.playSound(player.getLocation(), "ui.button.click", 1, 1);
        }
        
        // 模式选择菜单处理
        if (event.getView().getTitle().equals(MODE_SELECTION_TITLE)) {
            if (slot == 2) {
                // Normal 模式
                plugin.getCliManager().switchMode(player, DialogueSession.Mode.NORMAL);
                player.closeInventory();
                player.playSound(player.getLocation(), "ui.button.click", 1, 1);
            } else if (slot == 4) {
                // SMART 模式
                plugin.getCliManager().switchMode(player, DialogueSession.Mode.SMART);
                player.closeInventory();
                player.playSound(player.getLocation(), "ui.button.click", 1, 1);
            } else if (slot == 6) {
                // YOLO 模式
                plugin.getCliManager().switchMode(player, DialogueSession.Mode.YOLO);
                player.closeInventory();
                player.playSound(player.getLocation(), "ui.button.click", 1, 1);
            }
        }
    }
}
