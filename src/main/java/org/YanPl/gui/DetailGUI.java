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
    private static final int EDIT_TITLE_SLOT = 0;
    private static final int EDIT_DESC_SLOT = 1;
    private static final int ADD_BEFORE_SLOT = 2;
    private static final int ADD_AFTER_SLOT = 3;
    private static final int DELETE_SLOT = 4;
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

        // 点击编辑按钮
        if (slot == EDIT_TITLE_SLOT) {
            if (plugin.getConfigManager().isDebug()) {
                plugin.getLogger().info("Clicked EDIT_TITLE_SLOT");
            }
            openSignEditor("title");
            return;
        }

        if (slot == EDIT_DESC_SLOT) {
            if (plugin.getConfigManager().isDebug()) {
                plugin.getLogger().info("Clicked EDIT_DESC_SLOT");
            }
            openSignEditor("desc");
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
        // 关闭详情页不保存，清除临时编辑内容
        clearTempEdits();
    }

    /**
     * 填充Inventory
     */
    private void fillInventory() {
        // 两个编辑按钮
        if (step == null) {
            // 编辑计划：标题和描述
            inventory.setItem(EDIT_TITLE_SLOT, createTitleEditItem());
            inventory.setItem(EDIT_DESC_SLOT, createDescEditItem());
        } else {
            // 编辑步骤：描述和备注
            inventory.setItem(EDIT_TITLE_SLOT, createStepDescEditItem());
            inventory.setItem(EDIT_DESC_SLOT, createStepNotesEditItem());
        }

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
        inventory.setItem(5, createGlassPane(" "));
        inventory.setItem(7, createGlassPane(" "));

        // 取消按钮
        inventory.setItem(CANCEL_SLOT, createActionButton("取消", Material.BARRIER));

        // 保存按钮
        inventory.setItem(SAVE_SLOT, createActionButton("保存", Material.NETHER_STAR));
    }

    /**
     * 创建标题编辑物品（编辑计划标题或步骤描述）
     */
    private ItemStack createTitleEditItem() {
        ItemStack item = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            if (step == null) {
                // 编辑计划标题
                meta.setDisplayName(ChatColor.GOLD + "编辑标题");
                List<String> lore = new ArrayList<>();
                // 优先显示临时编辑的内容
                String displayText = tempTitle != null ? tempTitle : plan.getTitle();
                if (tempTitle != null) {
                    addMultiLineLore(lore, ChatColor.YELLOW + "(未保存) " + ChatColor.WHITE, displayText);
                } else {
                    addMultiLineLore(lore, ChatColor.GRAY + "当前: " + ChatColor.WHITE, displayText);
                }
                lore.add("");
                lore.add(ChatColor.GREEN + "点击编辑");
                meta.setLore(lore);
            } else {
                // 编辑步骤描述
                meta.setDisplayName(ChatColor.GOLD + "编辑描述");
                List<String> lore = new ArrayList<>();
                String displayText = tempStepDesc != null ? tempStepDesc : step.getDescription();
                if (tempStepDesc != null) {
                    addMultiLineLore(lore, ChatColor.YELLOW + "(未保存) " + ChatColor.WHITE, displayText);
                } else {
                    addMultiLineLore(lore, ChatColor.GRAY + "当前: " + ChatColor.WHITE, displayText);
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
     * 创建描述编辑物品（编辑计划描述或步骤备注）
     */
    private ItemStack createDescEditItem() {
        ItemStack item = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            if (step == null) {
                // 编辑计划描述
                meta.setDisplayName(ChatColor.GOLD + "编辑描述");
                List<String> lore = new ArrayList<>();
                // 优先显示临时编辑的内容
                String displayText = tempDesc != null ? tempDesc : plan.getDescription();
                if (tempDesc != null) {
                    addMultiLineLore(lore, ChatColor.YELLOW + "(未保存) " + ChatColor.WHITE, displayText, true);
                } else {
                    addMultiLineLore(lore, ChatColor.GRAY + "当前: " + ChatColor.WHITE, displayText, true);
                }
                lore.add("");
                lore.add(ChatColor.GREEN + "点击编辑");
                meta.setLore(lore);
            } else {
                // 编辑步骤备注
                meta.setDisplayName(ChatColor.GOLD + "编辑备注");
                List<String> lore = new ArrayList<>();
                String displayText = tempStepNotes != null ? tempStepNotes : step.getNotes();
                if (tempStepNotes != null) {
                    addMultiLineLore(lore, ChatColor.YELLOW + "(未保存) " + ChatColor.WHITE, displayText, true);
                } else {
                    addMultiLineLore(lore, ChatColor.GRAY + "当前: " + ChatColor.WHITE, displayText, true);
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
     * 创建步骤描述编辑物品
     */
    private ItemStack createStepDescEditItem() {
        ItemStack item = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + "编辑描述");
            List<String> lore = new ArrayList<>();
            // 优先显示临时编辑的内容
            String displayText = tempStepDesc != null ? tempStepDesc : step.getDescription();
            if (tempStepDesc != null) {
                addMultiLineLore(lore, ChatColor.YELLOW + "(未保存) " + ChatColor.WHITE, displayText);
            } else {
                addMultiLineLore(lore, ChatColor.GRAY + "当前: " + ChatColor.WHITE, displayText);
            }
            lore.add("");
            lore.add(ChatColor.GREEN + "点击编辑");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * 创建步骤备注编辑物品
     */
    private ItemStack createStepNotesEditItem() {
        ItemStack item = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + "编辑备注");
            List<String> lore = new ArrayList<>();
            // 优先显示临时编辑的内容
            String displayText = tempStepNotes != null ? tempStepNotes : step.getNotes();
            if (tempStepNotes != null) {
                addMultiLineLore(lore, ChatColor.YELLOW + "(未保存) " + ChatColor.WHITE, displayText, true);
            } else {
                addMultiLineLore(lore, ChatColor.GRAY + "当前: " + ChatColor.WHITE, displayText, true);
            }
            lore.add("");
            lore.add(ChatColor.GREEN + "点击编辑");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * 添加多行内容到lore
     * @param lore lore列表
     * @param prefix 第一行的前缀
     * @param text 文本内容（可能包含换行符）
     */
    private void addMultiLineLore(List<String> lore, String prefix, String text) {
        addMultiLineLore(lore, prefix, text, false);
    }
    
    /**
     * 添加多行内容到lore
     * @param lore lore列表
     * @param prefix 第一行的前缀
     * @param text 文本内容（可能包含换行符）
     * @param showEmpty 是否在内容为空时显示"(无)"
     */
    private void addMultiLineLore(List<String> lore, String prefix, String text, boolean showEmpty) {
        if (text == null || text.isEmpty()) {
            if (showEmpty) {
                lore.add(prefix + "(无)");
            } else {
                lore.add(prefix + "");
            }
            return;
        }
        
        String[] lines = text.split("\n");
        for (int i = 0; i < lines.length; i++) {
            if (i == 0) {
                lore.add(prefix + lines[i]);
            } else {
                lore.add(ChatColor.WHITE + lines[i]);
            }
        }
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
         * @param field 要编辑的字段：title（标题/描述）或desc（描述/备注）
         */
        private void openSignEditor(String field) {
            String currentText;
            SignEditor.EditType editType;
    
            // 获取当前显示的文本（优先显示临时编辑的内容）
            if (step == null) {
                if ("title".equals(field)) {
                    currentText = tempTitle != null ? tempTitle : plan.getTitle();
                    editType = SignEditor.EditType.SINGLE_LINE;
                } else {
                    currentText = tempDesc != null ? tempDesc : plan.getDescription();
                    editType = SignEditor.EditType.SINGLE_LINE;
                }
            } else {
                if ("title".equals(field)) {
                    currentText = tempStepDesc != null ? tempStepDesc : step.getDescription();
                    editType = SignEditor.EditType.SINGLE_LINE;
                } else {
                    currentText = tempStepNotes != null ? tempStepNotes : (step.getNotes() != null ? step.getNotes() : "");
                    editType = SignEditor.EditType.SINGLE_LINE;
                }
            }
    
            final String editField = field;
    
            // 检查是否需要使用多行编辑器
            // 条件：1. 文本超过4行  2. 文本太宽拆分后超过4行（显示宽度 > 60）
            boolean needsMultiLineEditor = needsMultiLineEditor(currentText);
    
            if (plugin.getConfigManager().isDebug()) {
                plugin.getLogger().info("=== DetailGUI.openSignEditor ===");
                plugin.getLogger().info("field: " + field);
                plugin.getLogger().info("currentText: " + currentText);
                plugin.getLogger().info("使用MultiLineEditorGUI: " + needsMultiLineEditor);
            }
    
            if (needsMultiLineEditor) {
                // 设置忽略关闭标志，防止打开编辑器时触发返回上一级
                ignoreNextClose = true;

                // 打开多行编辑器
                plugin.getGuiManager().openGUI(player, new MultiLineEditorGUI(
                    plugin, 
                    player, 
                    currentText, 
                    (newText) -> {
                        // 编辑完成回调 - 只保存到临时变量，不处理界面跳转
                        saveTempEdit(editField, newText);
                    },
                    this
                ));
            } else {
                // 设置忽略关闭标志，防止SignGUI关闭时触发返回上一级
                ignoreNextClose = true;
    
                new SignEditor(plugin, player, currentText, editType).open((newText) -> {
                    // 编辑完成回调 - 保存到临时变量，等待用户点击保存按钮
                    saveTempEdit(editField, newText);
                    
                    player.sendMessage(ChatColor.GREEN + "» " + ChatColor.WHITE + "编辑完成，点击保存确认修改");
                    
                    // 刷新界面显示编辑后的内容
                    refresh();
                    
                    // 延迟一tick后重新打开详情页
                    new org.bukkit.scheduler.BukkitRunnable() {
                        @Override
                        public void run() {
                            open();
                        }
                    }.runTaskLater(plugin, 1L);
                });
            }
        }
    
        /**
         * 检查文本是否需要多行编辑器
         * 条件：1. 文本超过4行（基于换行符）  2. 文本太宽拆分后超过4行（显示宽度 > 60）
         */
        private boolean needsMultiLineEditor(String text) {
            if (text == null || text.isEmpty()) {
                return false;
            }
    
            // 条件1：文本超过4行（基于换行符）
            int lineCount = getLineCount(text);
            if (lineCount > 4) {
                if (plugin.getConfigManager().isDebug()) {
                    plugin.getLogger().info("needsMultiLineEditor: lineCount = " + lineCount + " > 4");
                }
                return true;
            }
    
            // 条件2：文本太宽拆分后超过4行（显示宽度 > 60）
            int displayWidth = SignEditor.getDisplayWidth(text);
            if (displayWidth > 60) {
                if (plugin.getConfigManager().isDebug()) {
                    plugin.getLogger().info("needsMultiLineEditor: displayWidth = " + displayWidth + " > 60");
                }
                return true;
            }
    
            if (plugin.getConfigManager().isDebug()) {
                plugin.getLogger().info("needsMultiLineEditor: lineCount = " + lineCount + ", displayWidth = " + displayWidth + " (无需多行编辑)");
            }
            return false;
        }
    
        /**
         * 获取文本的行数（基于换行符）
         */
        private int getLineCount(String text) {
            if (text == null || text.isEmpty()) {
                return 0;
            }
            String[] lines = text.split("\n", -1);
            int count = lines.length;
            if (plugin.getConfigManager().isDebug()) {
                plugin.getLogger().info("getLineCount: split result length = " + count);
                for (int i = 0; i < Math.min(10, lines.length); i++) {
                    plugin.getLogger().info("  line[" + i + "]: '" + lines[i] + "'");
                }
            }
            return count;
        }    
    /**
     * 临时编辑内容（未保存到plan）
     */
    private String tempTitle = null;
    private String tempDesc = null;
    private String tempStepDesc = null;
    private String tempStepNotes = null;
    
    /**
     * 保存临时编辑内容
     */
    private void saveTempEdit(String field, String text) {
        if (step == null) {
            if ("title".equals(field)) {
                tempTitle = text;
            } else {
                tempDesc = text;
            }
        } else {
            if ("title".equals(field)) {
                tempStepDesc = text;
            } else {
                tempStepNotes = text;
            }
        }
    }
    
    /**
     * 应用临时编辑到plan
     */
    private void applyTempEdits() {
        if (step == null) {
            if (tempTitle != null) {
                plan.setTitle(tempTitle);
                tempTitle = null;
            }
            if (tempDesc != null) {
                plan.setDescription(tempDesc);
                tempDesc = null;
            }
        } else {
            if (tempStepDesc != null) {
                step.setDescription(tempStepDesc);
                tempStepDesc = null;
            }
            if (tempStepNotes != null) {
                step.setNotes(tempStepNotes);
                tempStepNotes = null;
            }
        }
    }
    
    /**
     * 清除临时编辑内容
     */
    private void clearTempEdits() {
        tempTitle = null;
        tempDesc = null;
        tempStepDesc = null;
        tempStepNotes = null;
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
     * 保存修改（返回主界面，不设置待发送消息）
     */
    private void save() {
        // 应用临时编辑到plan
        applyTempEdits();
        
        // 标记计划已修改
        plan.setModified(true);
        
        // 返回上一级（会刷新主界面）
        plugin.getGuiManager().backToPrev(player);
        
        player.sendMessage(ChatColor.GREEN + "» " + ChatColor.WHITE + "修改已保存到临时计划");
    }
    
    /**
     * 构建计划摘要，用于发送给AI
     */
    private String buildPlanSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("#saved_plan: 用户已修改计划，内容如下：\n\n");
        sb.append("计划标题: ").append(plan.getTitle()).append("\n");
        sb.append("计划描述: ").append(plan.getDescription()).append("\n\n");
        sb.append("执行步骤:\n");
        
        for (int i = 0; i < plan.getSteps().size(); i++) {
            PlanStep step = plan.getSteps().get(i);
            sb.append("  ").append(i + 1).append(". ").append(step.getDescription());
            if (step.getNotes() != null && !step.getNotes().isEmpty()) {
                sb.append(" (备注: ").append(step.getNotes()).append(")");
            }
            sb.append("\n");
        }
        
        sb.append("\n用户可以继续调整计划，或直接让我执行。");
        
        return sb.toString();
    }
}