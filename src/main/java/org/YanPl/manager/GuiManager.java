package org.YanPl.manager;

import org.YanPl.FancyHelper;
import org.YanPl.model.DialogueSession;
import org.YanPl.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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
    private final String SETTINGS_TITLE = ChatColor.DARK_GRAY + "FancyHelper Settings";

    public GuiManager(FancyHelper plugin) {
        this.plugin = plugin;
    }

    /**
     * 打开设置菜单
     */
    public void openSettingsMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, SETTINGS_TITLE);

        // 1. 模式切换 (Normal/YOLO)
        updateModeItem(inv, player);

        // 2. 状态显示位置 (ActionBar/Subtitle)
        updateDisplayPosItem(inv, player);

        // 3. 工具权限管理
        ItemStack toolsItem = createItem(Material.CHEST, ChatColor.GOLD + "工具权限管理", 
                ChatColor.GRAY + "点击管理文件操作权限",
                ChatColor.GRAY + "包括: ls, read, diff");
        inv.setItem(14, toolsItem); // Slot 14 (Row 2, Col 6)

        // 4. 记忆管理
        ItemStack memoryItem = createItem(Material.BOOK, ChatColor.AQUA + "记忆管理", 
                ChatColor.GRAY + "查看和管理 AI 的记忆",
                ChatColor.GRAY + "左键: 查看列表",
                ChatColor.GRAY + "右键: 清空记忆 (需确认)");
        inv.setItem(12, memoryItem); // Slot 12 (Row 2, Col 4)
        
        // 5. 关闭按钮
        ItemStack closeItem = createItem(Material.BARRIER, ChatColor.RED + "关闭菜单", 
                ChatColor.GRAY + "点击关闭");
        inv.setItem(26, closeItem);

        // 填充背景板 (可选)
        ItemStack bg = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, bg);
            }
        }

        player.openInventory(inv);
    }

    private void updateModeItem(Inventory inv, Player player) {
        UUID uuid = player.getUniqueId();
        DialogueSession session = plugin.getCliManager().getSession(uuid);
        DialogueSession.Mode mode = (session != null) ? session.getMode() : DialogueSession.Mode.NORMAL;

        ItemStack item;
        if (mode == DialogueSession.Mode.NORMAL) {
            item = createItem(Material.LIME_DYE, ChatColor.GREEN + "当前模式: Normal", 
                    ChatColor.GRAY + "点击切换至 YOLO 模式",
                    ChatColor.GRAY + "YOLO 模式下 AI 将自动执行大部分命令");
        } else {
            item = createItem(Material.RED_DYE, ChatColor.RED + "当前模式: YOLO", 
                    ChatColor.GRAY + "点击切换至 Normal 模式",
                    ChatColor.GRAY + "Normal 模式下 AI 执行命令需手动确认");
        }
        inv.setItem(10, item); // Slot 10 (Row 2, Col 2)
    }

    private void updateDisplayPosItem(Inventory inv, Player player) {
        String displayPos = plugin.getConfigManager().getPlayerDisplayPosition(player);
        ItemStack item;
        if ("actionbar".equalsIgnoreCase(displayPos)) {
            item = createItem(Material.COMPASS, ChatColor.YELLOW + "显示位置: ActionBar", 
                    ChatColor.GRAY + "点击切换至 Subtitle",
                    ChatColor.GRAY + "当前状态显示在快捷栏上方");
        } else {
            item = createItem(Material.PAPER, ChatColor.YELLOW + "显示位置: Subtitle", 
                    ChatColor.GRAY + "点击切换至 ActionBar",
                    ChatColor.GRAY + "当前状态显示在屏幕中央");
        }
        inv.setItem(16, item); // Slot 16 (Row 2, Col 8)
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
        if (!event.getView().getTitle().equals(SETTINGS_TITLE)) {
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
    }
}
