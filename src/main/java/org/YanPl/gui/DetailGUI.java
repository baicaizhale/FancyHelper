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
 * 详情界面（9槽Dropper GUI）
 * 用于编辑计划标题或单个步骤的详情
 */
public class DetailGUI extends GUI {

    private final ExecutionPlan plan;
    private final PlanStep step; // null表示编辑标题
    private final GUI prevGUI;

    // 槽位定义
    private static final int EDIT_SLOT = 0;
    private static final int ADD_BEFORE_SLOT = 1;
    private static final int ADD_AFTER_SLOT = 2;
    private static final int DELETE_SLOT = 3;
    private static final int CANCEL_SLOT = 6;
    private static final int SAVE_SLOT = 8;

    /**
     * 构造函数 - 编辑标题
     */
    public DetailGUI(FancyHelper plugin, Player player, ExecutionPlan plan, GUI prevGUI) {
        super(plugin, player);
        this.plan = plan;
        this.step = null;
        this.prevGUI = prevGUI;
    }

    /**
     * 构造函数 - 编辑步骤
     */
    public DetailGUI(FancyHelper plugin, Player player, ExecutionPlan plan, PlanStep step, GUI prevGUI) {
        super(plugin, player);
        this.plan = plan;
        this.step = step;
        this.prevGUI = prevGUI;
    }

    @Override
    public void open() {
        // 每次打开都重新创建Inventory，确保holder正确
        inventory = Bukkit.createInventory(this, 9, ChatColor.DARK_PURPLE + "编辑详情");
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

        // 只在debug模式下输出日志
        if (plugin.getConfigManager().isDebug()) {
            plugin.getLogger().info("DetailGUI handleClick: slot=" + slot + ", step=" + (step != null ? step.getOrder() : "null"));
        }

        // 点击编辑项
        if (slot == EDIT_SLOT) {
            if (plugin.getConfigManager().isDebug()) {
                plugin.getLogger().info("Clicked EDIT_SLOT");
            }
            openSignEditor();
            return;
        }

        // 在前面添加
        if (slot == ADD_BEFORE_SLOT && step != null) {
            if (plugin.getConfigManager().isDebug()) {
                plugin.getLogger().info("Clicked ADD_BEFORE_SLOT");
            }
            addStepBefore();
            return;
        }

        // 在后面添加
        if (slot == ADD_AFTER_SLOT && step != null) {
            if (plugin.getConfigManager().isDebug()) {
                plugin.getLogger().info("Clicked ADD_AFTER_SLOT");
            }
            addStepAfter();
            return;
        }

        // 删除
        if (slot == DELETE_SLOT && step != null) {
            if (plugin.getConfigManager().isDebug()) {
                plugin.getLogger().info("Clicked DELETE_SLOT");
            }
            deleteStep();
            return;
        }

        // 取消
        if (slot == CANCEL_SLOT) {
            if (plugin.getConfigManager().isDebug()) {
                plugin.getLogger().info("Clicked CANCEL_SLOT");
            }
            plugin.getGuiManager().backToPrev(player);
            return;
        }

        // 保存
        if (slot == SAVE_SLOT) {
            if (plugin.getConfigManager().isDebug()) {
                plugin.getLogger().info("Clicked SAVE_SLOT");
            }
            save();
            return;
        }

        if (plugin.getConfigManager().isDebug()) {
            plugin.getLogger().info("Unhandled slot: " + slot);
        }
    }

    @Override
    public String getTitle() {
        return "编辑详情";
    }

    @Override
    public void onClose() {
        // 清理资源（GUIListener会自动返回上一级）
    }

    /**
     * 填充Inventory
     */
    private void fillInventory() {
        // 编辑项
        inventory.setItem(EDIT_SLOT, createEditItem());

        // 添加和删除按钮（仅编辑步骤时显示）
        if (step != null) {
            inventory.setItem(ADD_BEFORE_SLOT, createActionButton("在前面添加", Material.EMERALD_BLOCK));
            inventory.setItem(ADD_AFTER_SLOT, createActionButton("在后面添加", Material.EMERALD));
            inventory.setItem(DELETE_SLOT, createActionButton("删除", Material.REDSTONE_BLOCK));
        } else {
            // 编辑标题时，这些位置显示绿色玻璃板
            inventory.setItem(ADD_BEFORE_SLOT, createGlassPane(" "));
            inventory.setItem(ADD_AFTER_SLOT, createGlassPane(" "));
            inventory.setItem(DELETE_SLOT, createGlassPane(" "));
        }

        // 空白区域
        inventory.setItem(4, createGlassPane(" "));
        inventory.setItem(5, createGlassPane(" "));
        inventory.setItem(7, createGlassPane(" "));

        // 取消按钮
        inventory.setItem(CANCEL_SLOT, createActionButton("取消", Material.BARRIER));

        // 保存按钮
        inventory.setItem(SAVE_SLOT, createActionButton("保存", Material.NETHER_STAR));
    }

    /**
     * 创建编辑项物品
     */
    private ItemStack createEditItem() {
        ItemStack item = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            if (step == null) {
                // 编辑标题
                meta.setDisplayName(ChatColor.GOLD + "编辑标题和描述");
                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.GRAY + "标题: " + ChatColor.WHITE + plan.getTitle());
                lore.add(ChatColor.GRAY + "描述: " + ChatColor.WHITE + plan.getDescription());
                lore.add("");
                lore.add(ChatColor.GREEN + "点击编辑");
                meta.setLore(lore);
            } else {
                // 编辑步骤
                meta.setDisplayName(ChatColor.GOLD + "编辑步骤 " + step.getOrder());
                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.GRAY + "描述: " + ChatColor.WHITE + step.getDescription());
                if (step.getNotes() != null && !step.getNotes().isEmpty()) {
                    lore.add(ChatColor.GRAY + "备注: " + ChatColor.WHITE + step.getNotes());
                }
                lore.add("");
                lore.add(ChatColor.GREEN + "点击编辑");
                meta.setLore(lore);
            }
            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * 创建操作按钮
     */
    private ItemStack createActionButton(String name, Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.AQUA + name);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * 创建玻璃板
     */
    private ItemStack createGlassPane(String name) {
        ItemStack pane = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            pane.setItemMeta(meta);
        }
        return pane;
    }

    /**
     * 打开告示牌编辑器
     */
    private void openSignEditor() {
        String currentText;

        if (step == null) {
            // 编辑标题和描述
            currentText = plan.getTitle() + "\n" + plan.getDescription();
        } else {
            // 编辑步骤描述和备注
            currentText = step.getDescription() + "\n" + (step.getNotes() != null ? step.getNotes() : "");
        }

        new SignEditor(plugin, player, currentText, (newText) -> {
            // 编辑完成回调
            String[] lines = newText.split("\n", 2);

            if (step == null) {
                // 更新标题和描述
                plan.setTitle(lines[0]);
                if (lines.length > 1) {
                    plan.setDescription(lines[1]);
                } else {
                    plan.setDescription("");
                }
            } else {
                // 更新步骤描述和备注
                step.setDescription(lines[0]);
                if (lines.length > 1) {
                    step.setNotes(lines[1]);
                } else {
                    step.setNotes("");
                }
            }

            // 刷新界面
            refresh();

            player.sendMessage(ChatColor.GREEN + "» " + ChatColor.WHITE + "已更新");
        }).open((newText) -> {
            // 编辑完成回调
            String[] lines = newText.split("\n", 2);

            if (step == null) {
                // 更新标题和描述
                plan.setTitle(lines[0]);
                if (lines.length > 1) {
                    plan.setDescription(lines[1]);
                } else {
                    plan.setDescription("");
                }
            } else {
                // 更新步骤描述和备注
                step.setDescription(lines[0]);
                if (lines.length > 1) {
                    step.setNotes(lines[1]);
                } else {
                    step.setNotes("");
                }
            }

            // 刷新界面
            refresh();

            player.sendMessage(ChatColor.GREEN + "» " + ChatColor.WHITE + "已更新");
        });
    }

    /**
     * 在前面添加步骤
     */
    private void addStepBefore() {
        int currentIndex = plan.getSteps().indexOf(step);
        if (currentIndex == -1) {
            return;
        }

        PlanStep newStep = new PlanStep(0, "新步骤");
        plan.getSteps().add(currentIndex, newStep);

        // 重新编号所有步骤
        renumberSteps();

        player.sendMessage(ChatColor.GREEN + "» " + ChatColor.WHITE + "已在前面添加新步骤");

        // 返回主界面
        plugin.getGuiManager().backToPrev(player);
    }

    /**
     * 在后面添加步骤
     */
    private void addStepAfter() {
        int currentIndex = plan.getSteps().indexOf(step);
        if (currentIndex == -1) {
            return;
        }

        PlanStep newStep = new PlanStep(0, "新步骤");
        plan.getSteps().add(currentIndex + 1, newStep);

        // 重新编号所有步骤
        renumberSteps();

        player.sendMessage(ChatColor.GREEN + "» " + ChatColor.WHITE + "已在后面添加新步骤");

        // 返回主界面
        plugin.getGuiManager().backToPrev(player);
    }

    /**
     * 删除步骤
     */
    private void deleteStep() {
        if (plan.getSteps().size() <= 1) {
            player.sendMessage(ChatColor.RED + "» " + ChatColor.WHITE + "至少保留一个步骤");
            return;
        }

        plan.getSteps().remove(step);

        // 重新编号所有步骤
        renumberSteps();

        player.sendMessage(ChatColor.GREEN + "» " + ChatColor.WHITE + "已删除步骤");

        // 返回主界面
        plugin.getGuiManager().backToPrev(player);
    }

    /**
     * 重新编号所有步骤
     */
    private void renumberSteps() {
        for (int i = 0; i < plan.getSteps().size(); i++) {
            plan.getSteps().get(i).setOrder(i + 1);
        }
    }

    /**
     * 保存修改
     */
    private void save() {
        // 返回上一级（会刷新主界面）
        plugin.getGuiManager().backToPrev(player);
        player.sendMessage(ChatColor.GREEN + "» " + ChatColor.WHITE + "已保存");
    }
}