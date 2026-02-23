package org.YanPl.gui;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.YanPl.FancyHelper;
import org.YanPl.model.ExecutionPlan;
import org.YanPl.model.PlanStep;

import java.util.ArrayList;
import java.util.List;

/**
 * 计划编辑主界面（54槽Chest GUI）
 */
public class PlanEditGUI extends GUI {

    private final ExecutionPlan originalPlan;  // 原始计划（只读）
    private final ExecutionPlan temporaryPlan; // 临时副本（可编辑）
    private int currentPage = 0;
    private static final int ITEMS_PER_PAGE = 36; // 4行 x 9列 = 36个步骤（每页）

    // 槽位定义
    private static final int TITLE_SLOT = 0;
    private static final int CANCEL_SLOT = 45;
    private static final int SAVE_SLOT = 53;
    private static final int PREV_PAGE_SLOT = 47;
    private static final int NEXT_PAGE_SLOT = 51;

    // 步骤起始槽位（第1行，第0列）
    private static final int STEPS_START_ROW = 1;
    private static final int STEPS_END_ROW = 4;

    public PlanEditGUI(FancyHelper plugin, Player player, ExecutionPlan plan) {
        super(plugin, player);
        this.originalPlan = plan;
        // 创建临时副本
        this.temporaryPlan = new ExecutionPlan(plan.getTitle(), plan.getDescription());
        this.temporaryPlan.copyFrom(plan);
    }

    /**
     * 获取当前正在编辑的计划（临时副本）
     * 
     * @return 临时计划
     */
    public ExecutionPlan getEditingPlan() {
        return temporaryPlan;
    }

    @Override
    public void open() {
        // 每次打开都重新创建Inventory，确保holder正确
        inventory = Bukkit.createInventory(this, 54, ChatColor.DARK_PURPLE + "计划编辑");
        fillInventory();
        player.openInventory(inventory);
    }

    @Override
    public void refresh() {
        if (inventory != null) {
            inventory.clear();
            fillInventory();
        }
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getSlot();

        // 点击标题
        if (slot == TITLE_SLOT) {
            // 打开详情界面编辑标题
            plugin.getGuiManager().openGUI(player, new DetailGUI(plugin, player, temporaryPlan, this));
            return;
        }

        // 点击取消按钮
        if (slot == CANCEL_SLOT) {
            // 清理editGUIMap
            plugin.getPlanManager().clearEditGUI(player.getUniqueId());
            plugin.getGuiManager().closeGUI(player);
            player.sendMessage(ChatColor.GRAY + "» " + ChatColor.YELLOW + "已取消编辑，未保存更改");
            return;
        }

        // 点击保存按钮
        if (slot == SAVE_SLOT) {
            saveAndNotify();
            return;
        }

        // 点击上一页
        if (slot == PREV_PAGE_SLOT) {
            if (currentPage > 0) {
                currentPage--;
                refresh();
            }
            return;
        }

        // 点击下一页
        if (slot == NEXT_PAGE_SLOT) {
            if (currentPage < getTotalPages() - 1) {
                currentPage++;
                refresh();
            }
            return;
        }

        // 点击步骤
        if (isStepSlot(slot)) {
            int stepIndex = getStepIndexFromSlot(slot);
            List<PlanStep> currentSteps = getCurrentPageSteps();
            if (stepIndex >= 0 && stepIndex < currentSteps.size()) {
                PlanStep step = currentSteps.get(stepIndex);
                // 打开详情界面编辑步骤
                plugin.getGuiManager().openGUI(player, new DetailGUI(plugin, player, temporaryPlan, step, this));
            }
        }
    }

    @Override
    public String getTitle() {
        return "计划编辑";
    }

    @Override
    public void onClose() {
        // 清理资源
        // 注意：不要在这里清理editGUIMap，因为backToPrev也会调用onClose
    }

    /**
     * 填充Inventory
     */
    private void fillInventory() {
        // 第一行：标题
        setTitleItem();

        // 填充第一行其余位置为绿色玻璃板
        for (int col = 1; col < 9; col++) {
            inventory.setItem(col, createGlassPane(ChatColor.of("#AAFFAA"), " "));
        }

        // 第2-5行：步骤列表
        fillSteps();

        // 第6行：操作按钮
        fillActionButtons();
    }

    /**
     * 设置标题物品
     */
    private void setTitleItem() {
        ItemStack titleItem = new ItemStack(Material.BOOK);
        ItemMeta meta = titleItem.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + "计划标题: " + ChatColor.WHITE + temporaryPlan.getTitle());

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "描述: " + ChatColor.WHITE + temporaryPlan.getDescription());
            lore.add("");
            lore.add(ChatColor.GRAY + "步骤数量: " + ChatColor.YELLOW + temporaryPlan.getSteps().size());
            lore.add("");
            lore.add(ChatColor.GREEN + "点击编辑标题和描述");
            meta.setLore(lore);
            titleItem.setItemMeta(meta);
        }

        inventory.setItem(TITLE_SLOT, titleItem);
    }

    /**
     * 填充步骤列表
     */
    private void fillSteps() {
        List<PlanStep> currentSteps = getCurrentPageSteps();
        int totalSteps = temporaryPlan.getSteps().size();
        int startIndex = currentPage * ITEMS_PER_PAGE;

        // 填充步骤物品（第1-4行，每行9个）
        for (int i = 0; i < ITEMS_PER_PAGE; i++) {
            int slot = STEPS_START_ROW * 9 + i;

            if (i < currentSteps.size()) {
                PlanStep step = currentSteps.get(i);
                // 显示全局步骤序号
                inventory.setItem(slot, createStepItem(step, startIndex + i + 1));
            } else {
                // 空槽位填充灰色玻璃板
                inventory.setItem(slot, createGlassPane(ChatColor.of("#AAAAAA"), " "));
            }
        }
    }

    /**
     * 填充操作按钮行（第5行）
     * 布局：[取消][页信息][上一页][页码][分隔][页码][下一页][页信息][保存]
     * 槽位：  45     46      47    48    49    50    51      52     53
     */
    private void fillActionButtons() {
        int totalPages = getTotalPages();

        // 取消按钮
        ItemStack cancelItem = new ItemStack(Material.BARRIER);
        ItemMeta cancelMeta = cancelItem.getItemMeta();
        if (cancelMeta != null) {
            cancelMeta.setDisplayName(ChatColor.RED + "取消（不保存）");
            cancelItem.setItemMeta(cancelMeta);
        }
        inventory.setItem(CANCEL_SLOT, cancelItem);

        // 保存按钮
        ItemStack saveItem = new ItemStack(Material.NETHER_STAR);
        ItemMeta saveMeta = saveItem.getItemMeta();
        if (saveMeta != null) {
            saveMeta.setDisplayName(ChatColor.GREEN + "保存并提交");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "保存修改并通知AI");
            lore.add(ChatColor.GRAY + "重新生成执行方案");
            saveMeta.setLore(lore);
            saveItem.setItemMeta(saveMeta);
        }
        inventory.setItem(SAVE_SLOT, saveItem);

        // 分页相关按钮（仅当有多页时显示）
        if (totalPages > 1) {
            // 上一页按钮
            if (currentPage > 0) {
                inventory.setItem(PREV_PAGE_SLOT, createPageButton("上一页", Material.SPECTRAL_ARROW));
            } else {
                inventory.setItem(PREV_PAGE_SLOT, createGlassPane(ChatColor.of("#666666"), " "));
            }

            // 下一页按钮
            if (currentPage < totalPages - 1) {
                inventory.setItem(NEXT_PAGE_SLOT, createPageButton("下一页", Material.SPECTRAL_ARROW));
            } else {
                inventory.setItem(NEXT_PAGE_SLOT, createGlassPane(ChatColor.of("#666666"), " "));
            }

            // 页码显示（槽位48和50）
            ItemStack pageInfo = createPageInfo();
            inventory.setItem(48, pageInfo);
            inventory.setItem(50, pageInfo);

            // 中间分隔（槽位49）
            inventory.setItem(49, createGlassPane(ChatColor.of("#AAFFAA"), " "));

            // 页码信息指示器（槽位46和52）
            inventory.setItem(46, createGlassPane(ChatColor.of("#AAFFAA"), " "));
            inventory.setItem(52, createGlassPane(ChatColor.of("#AAFFAA"), " "));
        } else {
            // 单页模式，填充绿色玻璃板
            for (int slot = 46; slot < 53; slot++) {
                inventory.setItem(slot, createGlassPane(ChatColor.of("#AAFFAA"), " "));
            }
        }
    }

    /**
     * 创建页码信息物品
     */
    private ItemStack createPageInfo() {
        ItemStack item = new ItemStack(Material.KNOWLEDGE_BOOK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            int totalPages = getTotalPages();
            int totalSteps = temporaryPlan.getSteps().size();
            meta.setDisplayName(ChatColor.AQUA + "第 " + ChatColor.YELLOW + (currentPage + 1) + 
                              ChatColor.AQUA + " / " + ChatColor.YELLOW + totalPages + ChatColor.AQUA + " 页");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "共 " + ChatColor.WHITE + totalSteps + ChatColor.GRAY + " 个步骤");
            lore.add("");
            lore.add(ChatColor.GRAY + "当前显示: " + ChatColor.WHITE + 
                    (currentPage * ITEMS_PER_PAGE + 1) + " - " + 
                    Math.min((currentPage + 1) * ITEMS_PER_PAGE, totalSteps));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * 创建步骤物品
     */
    private ItemStack createStepItem(PlanStep step, int displayIndex) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(ChatColor.YELLOW + "步骤 " + step.getOrder());

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "描述: " + ChatColor.WHITE + step.getDescription());
            if (step.getNotes() != null && !step.getNotes().isEmpty()) {
                lore.add(ChatColor.GRAY + "备注: " + ChatColor.WHITE + step.getNotes());
            }
            lore.add("");
            lore.add(ChatColor.GREEN + "点击编辑此步骤");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * 创建玻璃板
     */
    private ItemStack createGlassPane(ChatColor color, String name) {
        ItemStack pane = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            pane.setItemMeta(meta);
        }
        return pane;
    }

    /**
     * 创建分页按钮
     */
    private ItemStack createPageButton(String name, Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.AQUA + name);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * 获取当前页的步骤
     */
    private List<PlanStep> getCurrentPageSteps() {
        int start = currentPage * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, temporaryPlan.getSteps().size());

        if (start >= temporaryPlan.getSteps().size()) {
            return new ArrayList<>();
        }

        return new ArrayList<>(temporaryPlan.getSteps().subList(start, end));
    }

    /**
     * 获取总页数
     */
    private int getTotalPages() {
        return (int) Math.ceil((double) temporaryPlan.getSteps().size() / ITEMS_PER_PAGE);
    }

    /**
     * 检查槽位是否为步骤槽位
     */
    private boolean isStepSlot(int slot) {
        int row = slot / 9;
        // 检查是否在步骤显示区域（第1-4行）
        return row >= STEPS_START_ROW && row <= STEPS_END_ROW;
    }

    /**
     * 从槽位获取步骤索引
     */
    private int getStepIndexFromSlot(int slot) {
        int row = slot / 9;
        int col = slot % 9;

        int rowIndex = row - STEPS_START_ROW;
        return rowIndex * 9 + col;
    }

    /**
     * 从步骤索引获取槽位
     */
    private int getStepSlot(int index) {
        int rowIndex = index / 9;
        int colIndex = index % 9;

        int row = STEPS_START_ROW + rowIndex;
        return row * 9 + colIndex;
    }

    /**
     * 保存并同步到原始计划
     */
    private void saveAndNotify() {
        // 将临时副本同步到原始计划
        originalPlan.copyFrom(temporaryPlan);
        originalPlan.setModified(true);

        // 清理editGUIMap
        plugin.getPlanManager().clearEditGUI(player.getUniqueId());

        // 关闭GUI
        plugin.getGuiManager().closeGUI(player);

        player.sendMessage(ChatColor.GREEN + "» " + ChatColor.WHITE + "计划已保存");
    }
}