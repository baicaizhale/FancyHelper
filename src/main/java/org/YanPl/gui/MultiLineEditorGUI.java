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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * 多行文本编辑器（27槽Chest GUI）
 * 用于编辑超过4行的长文本
 * 文本按每4行分组显示，点击可编辑对应段落
 */
public class MultiLineEditorGUI extends GUI {

    // 原始文本（只读）
    private final String originalText;
    // 临时编辑文本（可编辑）
    private String editingText;
    // 编辑完成回调
    private final Consumer<String> callback;
    // 上一级GUI
    private final GUI prevGUI;

    // 槽位定义
    private static final int CANCEL_SLOT = 18;  // 第2行，第0列
    private static final int SAVE_SLOT = 26;    // 第2行，第8列
    private static final int CONTENT_START_ROW = 1;  // 内容从第1行开始
    private static final int CONTENT_END_ROW = 2;    // 内容到第2行结束（中间18个槽位）

    /**
     * 构造函数
     *
     * @param plugin 插件实例
     * @param player 玩家
     * @param text 要编辑的文本
     * @param callback 编辑完成回调
     * @param prevGUI 上一级GUI
     */
    public MultiLineEditorGUI(FancyHelper plugin, Player player, String text, Consumer<String> callback, GUI prevGUI) {
        super(plugin, player);
        this.originalText = text != null ? text : "";
        this.editingText = this.originalText;
        this.callback = callback;
        this.prevGUI = prevGUI;
    }

    @Override
    public void open() {
        // 每次打开都重新创建Inventory，确保holder正确
        inventory = Bukkit.createInventory(this, 27, ChatColor.DARK_PURPLE + "多行文本编辑");
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

        // 点击取消按钮
        if (slot == CANCEL_SLOT) {
            if (plugin.getConfigManager().isDebug()) {
                plugin.getLogger().info("MultiLineEditorGUI: 点击取消");
            }
            cancel();
            return;
        }

        // 点击保存按钮
        if (slot == SAVE_SLOT) {
            if (plugin.getConfigManager().isDebug()) {
                plugin.getLogger().info("MultiLineEditorGUI: 点击保存");
            }
            save();
            return;
        }

        // 点击内容槽位
        if (isContentSlot(slot)) {
            int segmentIndex = getSegmentIndexFromSlot(slot);
            editSegment(segmentIndex);
            return;
        }
    }

    @Override
    public String getTitle() {
        return "多行文本编辑";
    }

    @Override
    public void onClose() {
        // 关闭时不保存，让 GUIManager 处理返回逻辑
        // 不主动打开上一级，避免与 GUIManager 的 backToPrev 冲突
    }

    /**
     * 填充Inventory
     */
    private void fillInventory() {
        // 第0行：装饰（绿色玻璃板）
        for (int col = 0; col < 9; col++) {
            inventory.setItem(col, createGlassPane(ChatColor.of("#AAFFAA"), " "));
        }

        // 第1-2行：内容槽位（18个槽位，每个代表一段4行文本）
        fillContentSlots();

        // 第3行：操作按钮
        fillActionButtons();
    }

    /**
     * 填充内容槽位
     */
    private void fillContentSlots() {
        List<String[]> segments = splitIntoSegments(editingText);

        for (int i = 0; i < 18; i++) {
            int slot = getContentSlot(i);

            if (i < segments.size()) {
                // 显示内容段落
                inventory.setItem(slot, createSegmentItem(segments.get(i), i + 1));
            } else {
                // 空槽位
                inventory.setItem(slot, createGlassPane(ChatColor.of("#DDDDDD"), " "));
            }
        }
    }

    /**
     * 填充操作按钮行（第2行）
     */
    private void fillActionButtons() {
        // 第2行（槽位18-26）
        // 中间槽位填充绿色玻璃板
        for (int col = 1; col < 8; col++) {
            inventory.setItem(18 + col, createGlassPane(ChatColor.of("#AAFFAA"), " "));
        }

        // 取消按钮
        ItemStack cancelItem = new ItemStack(Material.BARRIER);
        ItemMeta cancelMeta = cancelItem.getItemMeta();
        if (cancelMeta != null) {
            cancelMeta.setDisplayName(ChatColor.RED + "取消");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "不保存修改");
            lore.add(ChatColor.GRAY + "返回上一级");
            cancelMeta.setLore(lore);
            cancelItem.setItemMeta(cancelMeta);
        }
        inventory.setItem(CANCEL_SLOT, cancelItem);

        // 保存按钮
        ItemStack saveItem = new ItemStack(Material.NETHER_STAR);
        ItemMeta saveMeta = saveItem.getItemMeta();
        if (saveMeta != null) {
            saveMeta.setDisplayName(ChatColor.GREEN + "保存");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "保存修改");
            lore.add(ChatColor.GRAY + "返回上一级");
            saveMeta.setLore(lore);
            saveItem.setItemMeta(saveMeta);
        }
        inventory.setItem(SAVE_SLOT, saveItem);
    }

    /**
     * 创建段落物品
     */
    private ItemStack createSegmentItem(String[] segmentLines, int segmentIndex) {
        ItemStack item = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + "段落 " + segmentIndex);

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "内容:");

            // 显示4行内容
            for (int i = 0; i < 4; i++) {
                if (i < segmentLines.length) {
                    String line = segmentLines[i];
                    if (line != null && !line.isEmpty()) {
                        lore.add(ChatColor.WHITE + "  " + line);
                    } else {
                        lore.add(ChatColor.GRAY + "  (空行)");
                    }
                } else {
                    lore.add(ChatColor.GRAY + "  (空行)");
                }
            }

            lore.add("");
            lore.add(ChatColor.GREEN + "点击编辑此段落");
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
     * 将文本按4行分割成多个段落
     * 首先按换行符分割，然后对超宽行按显示宽度进一步拆分
     */
    private List<String[]> splitIntoSegments(String text) {
        List<String[]> segments = new ArrayList<>();

        if (text == null || text.isEmpty()) {
            return segments;
        }

        // 按换行符分割
        String[] lines = text.split("\n", -1);

        // 对每一行，如果超过最大宽度（15），则拆分成多行
        List<String> allLines = new ArrayList<>();
        for (String line : lines) {
            List<String> splitLines = splitByWidth(line, 15);
            allLines.addAll(splitLines);
        }

        // 按每4行一组分割成段落
        for (int i = 0; i < allLines.size(); i += 4) {
            String[] segment = new String[4];
            for (int j = 0; j < 4 && (i + j) < allLines.size(); j++) {
                segment[j] = allLines.get(i + j);
            }
            segments.add(segment);
        }

        return segments;
    }

    /**
     * 按显示宽度分割字符串为多行
     * @param text 原文本
     * @param maxWidth 每行最大宽度
     * @return 分割后的行列表
     */
    private List<String> splitByWidth(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();

        if (text == null || text.isEmpty()) {
            return lines;
        }

        StringBuilder currentLine = new StringBuilder();
        int currentWidth = 0;

        for (char c : text.toCharArray()) {
            int charWidth = SignEditor.isFullWidthChar(c) ? 2 : 1;

            if (currentWidth + charWidth > maxWidth) {
                // 当前行已满，添加到列表
                lines.add(currentLine.toString());
                currentLine = new StringBuilder();
                currentWidth = 0;
            }

            currentLine.append(c);
            currentWidth += charWidth;
        }

        // 添加最后一行
        if (currentLine.length() > 0) {
            lines.add(currentLine.toString());
        }

        return lines;
    }

    /**
     * 检查槽位是否为内容槽位
     */
    private boolean isContentSlot(int slot) {
        int row = slot / 9;
        int col = slot % 9;
        return row >= CONTENT_START_ROW && row <= CONTENT_END_ROW;
    }

    /**
     * 从槽位获取段落索引
     */
    private int getSegmentIndexFromSlot(int slot) {
        int row = slot / 9;
        int col = slot % 9;
        int rowIndex = row - CONTENT_START_ROW;
        return rowIndex * 9 + col;
    }

    /**
     * 从段落索引获取槽位
     */
    private int getContentSlot(int segmentIndex) {
        int rowIndex = segmentIndex / 9;
        int colIndex = segmentIndex % 9;
        int row = CONTENT_START_ROW + rowIndex;
        return row * 9 + colIndex;
    }

    /**
     * 编辑指定段落
     */
    private void editSegment(int segmentIndex) {
        List<String[]> segments = splitIntoSegments(editingText);

        if (segmentIndex < 0 || segmentIndex >= segments.size()) {
            return;
        }

        String[] segment = segments.get(segmentIndex);

        // 合并4行为一个字符串（保留空行）
        StringBuilder segmentText = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            if (i > 0) {
                segmentText.append("\n");
            }
            if (i < segment.length && segment[i] != null) {
                segmentText.append(segment[i]);
            }
        }

        String currentSegmentText = segmentText.toString();

        if (plugin.getConfigManager().isDebug()) {
            plugin.getLogger().info("编辑段落 " + (segmentIndex + 1) + ": " + currentSegmentText);
        }

        // 设置忽略关闭标志，防止SignGUI关闭时触发返回上一级
        ignoreNextClose = true;

        // 打开告示牌编辑器编辑该段落
        new SignEditor(plugin, player, currentSegmentText, SignEditor.EditType.SINGLE_LINE).open((newText) -> {
            // 更新编辑文本中的对应段落
            updateSegment(segmentIndex, newText);

            if (plugin.getConfigManager().isDebug()) {
                plugin.getLogger().info("段落 " + (segmentIndex + 1) + " 更新为: " + newText);
            }

            player.sendMessage(ChatColor.GREEN + "» " + ChatColor.WHITE + "段落 " + (segmentIndex + 1) + " 已更新");

            // 刷新界面
            refresh();

            // 延迟一tick后重新打开编辑器
            new org.bukkit.scheduler.BukkitRunnable() {
                @Override
                public void run() {
                    open();
                }
            }.runTaskLater(plugin, 1L);
        });
    }

    /**
     * 更新指定段落的内容
     */
    private void updateSegment(int segmentIndex, String newText) {
        List<String[]> segments = splitIntoSegments(editingText);

        if (segmentIndex < 0 || segmentIndex >= segments.size()) {
            return;
        }

        // 将新文本分割为最多4行
        String[] newLines = newText.split("\n", -1);
        String[] segment = new String[4];

        for (int i = 0; i < 4; i++) {
            if (i < newLines.length) {
                segment[i] = newLines[i];
            } else {
                segment[i] = "";
            }
        }

        // 更新segments
        segments.set(segmentIndex, segment);

        // 重新组合所有段落为完整文本
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < segments.size(); i++) {
            String[] seg = segments.get(i);
            for (int j = 0; j < seg.length; j++) {
                if (j > 0) {
                    result.append("\n");
                }
                if (seg[j] != null && !seg[j].isEmpty()) {
                    result.append(seg[j]);
                }
            }

            // 如果不是最后一段，且当前段落的第4行不为空，需要确保段落之间有4行的分隔
            if (i < segments.size() - 1) {
                // 计算当前段落有多少行非空内容
                int nonEmptyLines = 0;
                for (String line : seg) {
                    if (line != null && !line.isEmpty()) {
                        nonEmptyLines++;
                    }
                }

                // 如果不足4行，添加空行补足
                for (int j = nonEmptyLines; j < 4; j++) {
                    result.append("\n");
                }
            }
        }

        editingText = result.toString();
    }

    /**
     * 保存编辑
     */
    private void save() {
        // 设置忽略关闭标志，防止 backToPrev 中的 onClose 触发异常
        ignoreNextClose = true;

        // 调用回调保存数据
        if (callback != null) {
            callback.accept(editingText);
        }

        player.sendMessage(ChatColor.GREEN + "» " + ChatColor.WHITE + "编辑已保存");

        // 刷新上一级GUI（DetailGUI）
        if (prevGUI != null) {
            prevGUI.refresh();
        }

        // 手动调用 backToPrev，返回上一级
        plugin.getGuiManager().backToPrev(player);
    }

    /**
     * 取消编辑
     */
    private void cancel() {
        player.sendMessage(ChatColor.GRAY + "» " + ChatColor.YELLOW + "已取消编辑");

        // 手动调用 backToPrev，返回上一级
        plugin.getGuiManager().backToPrev(player);
    }

    /**
     * 获取当前编辑的文本
     */
    public String getEditingText() {
        return editingText;
    }
}
