package org.YanPl.gui;

import de.rapha149.signgui.SignGUI;
import de.rapha149.signgui.exception.SignGUIVersionException;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.entity.Player;
import org.YanPl.FancyHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * 告示牌编辑器
 * 使用 SignGUI 库提供虚拟告示牌编辑功能
 * 多页编辑功能已移除，由 MultiLineEditorGUI 处理
 */
public class SignEditor {

    private final FancyHelper plugin;
    private final Player player;
    private final String defaultText;
    private final EditType editType;

    // 每行最大显示宽度（告示牌限制）
    public static final int MAX_LINE_WIDTH = 15;

    /**
     * 编辑类型枚举
     */
    public enum EditType {
        TITLE_DESC,      // 编辑计划标题和描述
        STEP_DESC_NOTES, // 编辑步骤描述和备注
        SINGLE_LINE      // 编辑单行文本
    }

    /**
     * 计算字符串的显示宽度
     * 汉字等全角字符宽度为2，英文等半角字符宽度为1
     */
    public static int getDisplayWidth(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }

        int width = 0;
        for (char c : text.toCharArray()) {
            // 判断是否为全角字符
            if (isFullWidth(c)) {
                width += 2;
            } else {
                width += 1;
            }
        }
        return width;
    }

    /**
     * 判断字符是否为全角字符（公开方法，供其他类使用）
     */
    public static boolean isFullWidthChar(char c) {
        return isFullWidth(c);
    }

    /**
     * 判断字符是否为全角字符
     */
    private static boolean isFullWidth(char c) {
        // CJK统一汉字范围
        if (c >= '\u4E00' && c <= '\u9FFF') return true;
        // CJK扩展A
        if (c >= '\u3400' && c <= '\u4DBF') return true;
        // 全角符号、日文假名等
        if (c >= '\u3000' && c <= '\u303F') return true;
        if (c >= '\u3040' && c <= '\u309F') return true; // 平假名
        if (c >= '\u30A0' && c <= '\u30FF') return true; // 片假名
        if (c >= '\uFF00' && c <= '\uFFEF') return true; // 全角ASCII、全角标点
        // 朝鲜文
        if (c >= '\uAC00' && c <= '\uD7AF') return true;

        return false;
    }

    /**
     * 按显示宽度分割字符串为多行
     * @param text 原文本
     * @param maxWidth 每行最大宽度
     * @return 分割后的行列表
     */
    private static List<String> splitByWidth(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();

        if (text == null || text.isEmpty()) {
            return lines;
        }

        StringBuilder currentLine = new StringBuilder();
        int currentWidth = 0;

        for (char c : text.toCharArray()) {
            int charWidth = isFullWidth(c) ? 2 : 1;

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
     * 构造函数
     *
     * @param plugin      插件实例
     * @param player      玩家
     * @param defaultText 默认文本
     * @param editType    编辑类型
     */
    public SignEditor(FancyHelper plugin, Player player, String defaultText, EditType editType) {
        this.plugin = plugin;
        this.player = player;
        this.defaultText = defaultText != null ? defaultText : "";
        this.editType = editType;
    }

    /**
     * 打开编辑器
     *
     * @param callback 编辑完成回调，返回完整文本
     */
    public void open(Consumer<String> callback) {
        // 直接使用单页编辑（多页编辑功能已移除，由MultiLineEditorGUI处理）
        openSinglePage(callback);
    }

    /**
     * 打开单页编辑器
     */
    private void openSinglePage(Consumer<String> callback) {
        String[] lines = parseTextToLines(defaultText);

        try {
            SignGUI gui = SignGUI.builder()
                .setLines(lines[0], lines[1], lines[2], lines[3])
                .setHandler((p, result) -> {
                    // 获取玩家编辑的内容
                    String[] resultLines = result.getLinesWithoutColor();
                    String fullText = mergeLines(resultLines);

                    if (((FancyHelper) plugin).getConfigManager().isDebug()) {
                        plugin.getLogger().info("告示牌编辑完成: " + fullText);
                    }

                    // 调用回调
                    if (callback != null) {
                        callback.accept(fullText);
                    }

                    return Collections.emptyList();
                })
                .build();

            gui.open(player);

            if (((FancyHelper) plugin).getConfigManager().isDebug()) {
                plugin.getLogger().info("已打开虚拟告示牌编辑器");
            }

        } catch (SignGUIVersionException e) {
            plugin.getLogger().warning("当前服务器版本不支持 SignGUI: " + e.getMessage());
            player.sendMessage(ChatColor.RED + "» " + ChatColor.WHITE + "当前服务器版本不支持告示牌编辑功能");
        }
    }

    /**
     * 解析文本为4行（告示牌限制）
     * 按显示宽度智能分行：优先按换行符分割，超长行自动截断
     */
    private String[] parseTextToLines(String text) {
        String[] result = new String[4];

        if (text == null || text.isEmpty()) {
            for (int i = 0; i < 4; i++) {
                result[i] = "";
            }
            return result;
        }

        // 按换行符分割
        String[] parts = text.split("\n", -1);

        int lineIndex = 0;
        for (int i = 0; i < parts.length && lineIndex < 4; i++) {
            String part = parts[i];
            
            // 按显示宽度分割超长行
            List<String> subLines = splitByWidth(part, MAX_LINE_WIDTH);
            
            for (String subLine : subLines) {
                if (lineIndex >= 4) break;
                result[lineIndex] = subLine;
                lineIndex++;
            }
        }

        // 填充空行
        while (lineIndex < 4) {
            result[lineIndex] = "";
            lineIndex++;
        }

        return result;
    }

    /**
     * 合并4行为完整文本
     */
    private String mergeLines(String[] lines) {
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            if (line != null && !line.isEmpty()) {
                if (sb.length() > 0) {
                    sb.append("\n");
                }
                sb.append(line);
            }
        }
        return sb.toString();
    }

    /**
     * 静态初始化 - SignGUI 库不需要额外初始化
     */
    public static void init(FancyHelper plugin) {
        // SignGUI 库不需要额外初始化
    }

    /**
     * 处理玩家输入（由ChatListener调用）
     * SignGUI 库自己处理输入，此方法保留以兼容旧的调用方式
     *
     * @param player 玩家
     * @param input  输入内容
     * @return 是否已处理
     */
    public static boolean handleInput(Player player, String input) {
        // SignGUI 库自己处理输入
        return false;
    }

    /**
     * 检查玩家是否在编辑文本
     * SignGUI 库内部管理状态，这里直接返回false
     *
     * @param player 玩家
     * @return 是否在编辑
     */
    public static boolean isEditing(Player player) {
        // SignGUI 库自己处理，不再使用会话管理
        return false;
    }

    /**
     * 取消玩家的文本编辑
     * SignGUI 库内部管理状态，这里只发送提示消息
     *
     * @param player 玩家
     */
    public static void cancelEdit(Player player) {
        player.sendMessage(ChatColor.GRAY + "» " + ChatColor.YELLOW + "已取消编辑");
    }

    /**
     * 清理所有编辑会话
     */
    public static void cleanup() {
        // 不再使用会话管理，无需清理
    }
}
