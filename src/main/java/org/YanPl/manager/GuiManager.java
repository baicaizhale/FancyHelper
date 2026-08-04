package org.YanPl.manager;

import org.YanPl.FancyHelper;
import org.YanPl.model.DialogueSession;
import org.YanPl.util.ColorUtil;
import org.YanPl.util.I18n;
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
    private final String SETTINGS_TITLE = I18n.t("gui.settings.title");
    private final String MODE_SELECTION_TITLE = I18n.t("gui.mode.title");

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
        ItemStack normalItem = createItem(Material.LIME_DYE, I18n.t("gui.mode.normal.name"),
                ColorUtil.translateCustomColors("&8&m------------------------"),
                I18n.t("gui.mode.normal.desc"),
                I18n.t("gui.mode.normal.detail"),
                "",
                I18n.t("gui.mode.click"),
                ColorUtil.translateCustomColors("&8&m------------------------"));
        inv.setItem(1, normalItem);

        // 2. SMART 模式
        ItemStack smartItem = createItem(Material.BLUE_DYE, I18n.t("gui.mode.smart.name"),
                ColorUtil.translateCustomColors("&8&m------------------------"),
                I18n.t("gui.mode.smart.desc"),
                I18n.t("gui.mode.smart.detail"),
                "",
                I18n.t("gui.mode.click"),
                ColorUtil.translateCustomColors("&8&m------------------------"));
        inv.setItem(3, smartItem);

        // 3. Plan 模式
        ItemStack planItem = createItem(Material.CYAN_DYE, I18n.t("gui.mode.plan.name"),
                ColorUtil.translateCustomColors("&8&m------------------------"),
                I18n.t("gui.mode.plan.desc"),
                I18n.t("gui.mode.plan.detail"),
                "",
                I18n.t("gui.mode.click"),
                ColorUtil.translateCustomColors("&8&m------------------------"));
        inv.setItem(5, planItem);

        // 4. YOLO 模式
        ItemStack yoloItem = createItem(Material.RED_DYE, I18n.t("gui.mode.yolo.name"),
                ColorUtil.translateCustomColors("&8&m------------------------"),
                I18n.t("gui.mode.yolo.desc"),
                I18n.t("gui.mode.yolo.detail"),
                "",
                I18n.t("gui.mode.click"),
                ColorUtil.translateCustomColors("&8&m------------------------"));
        inv.setItem(7, yoloItem);

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
        ItemStack memoryItem = createItem(Material.KNOWLEDGE_BOOK, I18n.t("gui.settings.memory.name"), 
                ColorUtil.translateCustomColors("&8&m------------------------"),
                I18n.t("gui.settings.memory.desc"),
                "",
                I18n.t("gui.settings.memory.lclick"),
                I18n.t("gui.settings.memory.rclick"),
                ColorUtil.translateCustomColors("&8&m------------------------"));
        inv.setItem(12, memoryItem); 

        // 3. 工具权限管理
        ItemStack toolsItem = createItem(Material.CHEST, I18n.t("gui.settings.tools.name"), 
                ColorUtil.translateCustomColors("&8&m------------------------"),
                I18n.t("gui.settings.tools.desc"),
                I18n.t("gui.settings.tools.include"),
                "",
                I18n.t("gui.settings.tools.click"),
                ColorUtil.translateCustomColors("&8&m------------------------"));
        inv.setItem(14, toolsItem); 

        // 4. 状态显示位置 (ActionBar/Subtitle)
        updateDisplayPosItem(inv, player);

        // 5. 声音开关
        updateSoundItem(inv, player);

        // 6. 关闭按钮
        ItemStack closeItem = createItem(Material.BARRIER, I18n.t("gui.settings.close.name"), 
                I18n.t("gui.settings.close.desc"));
        inv.setItem(26, closeItem);

        player.openInventory(inv);
    }

    private void updateModeItem(Inventory inv, Player player) {
        UUID uuid = player.getUniqueId();
        DialogueSession session = plugin.getCliManager().getSession(uuid);
        DialogueSession.Mode mode = (session != null) ? session.getMode() : DialogueSession.Mode.NORMAL;

        ItemStack item;
        if (mode == DialogueSession.Mode.NORMAL) {
            item = createItem(Material.LIME_DYE, I18n.t("gui.settings.mode.current.normal"),
                    ColorUtil.translateCustomColors("&8&m------------------------"),
                    I18n.t("gui.settings.mode.desc.normal"),
                    I18n.t("gui.mode.normal.detail"),
                    "",
                    I18n.t("gui.settings.mode.to.smart"),
                    ColorUtil.translateCustomColors("&8&m------------------------"));
        } else if (mode == DialogueSession.Mode.SMART) {
            item = createItem(Material.BLUE_DYE, I18n.t("gui.settings.mode.current.smart"),
                    ColorUtil.translateCustomColors("&8&m------------------------"),
                    I18n.t("gui.settings.mode.desc.smart"),
                    I18n.t("gui.mode.smart.detail"),
                    "",
                    I18n.t("gui.settings.mode.to.plan"),
                    ColorUtil.translateCustomColors("&8&m------------------------"));
        } else if (mode == DialogueSession.Mode.PLAN) {
            item = createItem(Material.CYAN_DYE, I18n.t("gui.settings.mode.current.plan"),
                    ColorUtil.translateCustomColors("&8&m------------------------"),
                    I18n.t("gui.settings.mode.desc.plan"),
                    I18n.t("gui.mode.plan.detail"),
                    "",
                    I18n.t("gui.settings.mode.to.yolo"),
                    ColorUtil.translateCustomColors("&8&m------------------------"));
        } else {
            item = createItem(Material.RED_DYE, I18n.t("gui.settings.mode.current.yolo"),
                    ColorUtil.translateCustomColors("&8&m------------------------"),
                    I18n.t("gui.settings.mode.desc.yolo"),
                    I18n.t("gui.mode.yolo.detail"),
                    "",
                    I18n.t("gui.settings.mode.to.normal"),
                    ColorUtil.translateCustomColors("&8&m------------------------"));
        }
        inv.setItem(10, item); 
    }

    private void updateDisplayPosItem(Inventory inv, Player player) {
        String displayPos = plugin.getConfigManager().getPlayerDisplayPosition(player);
        ItemStack item;
        boolean isActionBar = "actionbar".equalsIgnoreCase(displayPos);
        
        if (isActionBar) {
            item = createItem(Material.NAME_TAG, I18n.t("gui.settings.display.actionbar.name"), 
                    ColorUtil.translateCustomColors("&8&m------------------------"),
                    I18n.t("gui.settings.display.actionbar.desc"),
                    "",
                    I18n.t("gui.settings.display.to.subtitle"),
                    ColorUtil.translateCustomColors("&8&m------------------------"));
        } else {
            item = createItem(Material.PAPER, I18n.t("gui.settings.display.subtitle.name"), 
                    ColorUtil.translateCustomColors("&8&m------------------------"),
                    I18n.t("gui.settings.display.subtitle.desc"),
                    "",
                    I18n.t("gui.settings.display.to.actionbar"),
                    ColorUtil.translateCustomColors("&8&m------------------------"));
        }
        inv.setItem(16, item);
    }

    private void updateSoundItem(Inventory inv, Player player) {
        boolean disabled = plugin.getConfigManager().isPlayerSoundDisabled(player.getUniqueId());
        ItemStack item;
        if (disabled) {
            item = createItem(Material.GRAY_DYE, I18n.t("gui.settings.sound.off.name"),
                    ColorUtil.translateCustomColors("&8&m------------------------"),
                    I18n.t("gui.settings.sound.off.desc"),
                    "",
                    I18n.t("gui.settings.sound.to.on"),
                    ColorUtil.translateCustomColors("&8&m------------------------"));
        } else {
            item = createItem(Material.LIME_DYE, I18n.t("gui.settings.sound.on.name"),
                    ColorUtil.translateCustomColors("&8&m------------------------"),
                    I18n.t("gui.settings.sound.on.desc"),
                    "",
                    I18n.t("gui.settings.sound.to.off"),
                    ColorUtil.translateCustomColors("&8&m------------------------"));
        }
        inv.setItem(18, item);
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
                plugin.getCliManager().switchMode(player, DialogueSession.Mode.PLAN);
            } else if (currentMode == DialogueSession.Mode.PLAN) {
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
        // 声音开关
        else if (slot == 18) {
            player.performCommand("cli sound");
            updateSoundItem(event.getClickedInventory(), player);
            player.playSound(player.getLocation(), "ui.button.click", 1, 1);
        }
        // 关闭
        else if (slot == 26) {
            player.closeInventory();
            player.playSound(player.getLocation(), "ui.button.click", 1, 1);
        }
        
        // 模式选择菜单处理
        if (event.getView().getTitle().equals(MODE_SELECTION_TITLE)) {
            if (slot == 1) {
                // Normal 模式
                plugin.getCliManager().switchMode(player, DialogueSession.Mode.NORMAL);
                player.closeInventory();
                player.playSound(player.getLocation(), "ui.button.click", 1, 1);
            } else if (slot == 3) {
                // SMART 模式
                plugin.getCliManager().switchMode(player, DialogueSession.Mode.SMART);
                player.closeInventory();
                player.playSound(player.getLocation(), "ui.button.click", 1, 1);
            } else if (slot == 5) {
                // Plan 模式
                plugin.getCliManager().switchMode(player, DialogueSession.Mode.PLAN);
                player.closeInventory();
                player.playSound(player.getLocation(), "ui.button.click", 1, 1);
            } else if (slot == 7) {
                // YOLO 模式
                plugin.getCliManager().switchMode(player, DialogueSession.Mode.YOLO);
                player.closeInventory();
                player.playSound(player.getLocation(), "ui.button.click", 1, 1);
            }
        }
    }
}
