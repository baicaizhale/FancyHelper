package org.YanPl.util;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;

import java.util.HashMap;
import java.util.Map;

/**
 * 颜色工具类：处理自定义颜色代码转换
 * <p>
 * 支持的颜色代码：
 * - {@code §x} → #11A8CD (青色偏蓝)
 * - {@code §z} → #30AEE5 (明亮的天蓝色)
 */
public class ColorUtil {

    // 自定义颜色代码映射
    private static final ChatColor COLOR_X = net.md_5.bungee.api.ChatColor.of("#11A8CD");  // §x 颜色
    private static final ChatColor COLOR_Z = net.md_5.bungee.api.ChatColor.of("#30AEE5");  // §z 颜色

    // Minecraft § 格式码 → 可读名称映射
    private static final Map<Character, String> FORMAT_CODE_TO_NAME = new HashMap<>();

    static {
        FORMAT_CODE_TO_NAME.put('0', "black");
        FORMAT_CODE_TO_NAME.put('1', "dark_blue");
        FORMAT_CODE_TO_NAME.put('2', "dark_green");
        FORMAT_CODE_TO_NAME.put('3', "dark_aqua");
        FORMAT_CODE_TO_NAME.put('4', "dark_red");
        FORMAT_CODE_TO_NAME.put('5', "dark_purple");
        FORMAT_CODE_TO_NAME.put('6', "gold");
        FORMAT_CODE_TO_NAME.put('7', "gray");
        FORMAT_CODE_TO_NAME.put('8', "dark_gray");
        FORMAT_CODE_TO_NAME.put('9', "blue");
        FORMAT_CODE_TO_NAME.put('a', "green");
        FORMAT_CODE_TO_NAME.put('b', "aqua");
        FORMAT_CODE_TO_NAME.put('c', "red");
        FORMAT_CODE_TO_NAME.put('d', "light_purple");
        FORMAT_CODE_TO_NAME.put('e', "yellow");
        FORMAT_CODE_TO_NAME.put('f', "white");
        FORMAT_CODE_TO_NAME.put('k', "obfuscated");
        FORMAT_CODE_TO_NAME.put('l', "bold");
        FORMAT_CODE_TO_NAME.put('m', "strikethrough");
        FORMAT_CODE_TO_NAME.put('n', "underline");
        FORMAT_CODE_TO_NAME.put('o', "italic");
        FORMAT_CODE_TO_NAME.put('r', "reset");
    }

    // ========================
    //  自定义颜色转换（原有）
    // ========================

    /**
     * 转换自定义颜色代码 §x 和 §z 为实际颜色
     * 同时处理标准的 & 和 § 颜色代码
     */
    public static String translateCustomColors(String message) {
        if (message == null || message.isEmpty()) {
            return message;
        }

        String colorX = COLOR_X.toString();
        String colorZ = COLOR_Z.toString();

        message = message.replace("§x", colorX);
        message = message.replace("§z", colorZ);
        message = message.replace("&x", colorX);
        message = message.replace("&z", colorZ);

        message = ChatColor.translateAlternateColorCodes('&', message);

        return message;
    }

    public static String getColorX() {
        return "#11A8CD";
    }

    public static String getColorZ() {
        return "#30AEE5";
    }

    // ==========================================
    //  包级别颜色信息 → AI 可读格式（不含 §）
    // ==========================================

    /**
     * 将 BaseComponent 数组转换为 AI 可读格式。
     * <p>
     * Minecraft 包里的 ChatComponent 用 JSON 表示颜色（如 {@code "color":"green"}），
     * 但 {@link BaseComponent#toLegacyText()} 会降级成 {@code §a} / {@code §x§F§F§5§5§0§0} 这种 legacy 码。
     * 此方法先将组件转为 legacy 文本，再将 legacy 颜色码映射为清晰的可读标记。
     * <p>
     * 格式标记使用 {@code {name}} 而非 {@code [name]}，避免与内容中的方括号冲突。
     * <p>
     * 示例：{@code {green}Hello {gold}World{reset}}<br>
     * hex 颜色：{@code {#FF5500}Special message}
     */
    public static String componentsToReadable(BaseComponent... components) {
        if (components == null || components.length == 0) return "";
        String legacy = BaseComponent.toLegacyText(components);
        return legacyToReadable(legacy);
    }

    /**
     * 将单个 BaseComponent 转换为 AI 可读格式。
     */
    public static String componentToReadable(BaseComponent component) {
        if (component == null) return "";
        String legacy = component.toLegacyText();
        return legacyToReadable(legacy);
    }

    /**
     * 将 legacy {@code §} 格式文本转为可读标记格式。
     * <p>
     * {@code "§aHello §cWorld"} → {@code "{green}Hello {red}World"}
     */
    public static String legacyToReadable(String legacy) {
        if (legacy == null || legacy.isEmpty()) return legacy;
        StringBuilder result = new StringBuilder();
        int i = 0;
        while (i < legacy.length()) {
            char c = legacy.charAt(i);
            if (c == '§' && i + 1 < legacy.length()) {
                char code = legacy.charAt(i + 1);
                i += 2;

                if (code == 'x' && i + 12 <= legacy.length()) {
                    // §x§R§R§G§G§B§B → {#RRGGBB}
                    StringBuilder hex = new StringBuilder("#");
                    for (int j = 0; j < 6; j++) {
                        hex.append(legacy.charAt(i + 1));
                        i += 2;
                    }
                    result.append('{').append(hex).append('}');
                } else {
                    String name = FORMAT_CODE_TO_NAME.get(code);
                    if (name != null) {
                        result.append('{').append(name).append('}');
                    }
                }
            } else {
                result.append(c);
                i++;
            }
        }
        return result.toString();
    }

    /**
     * 从可读标记格式或 legacy § 格式中提取纯文本。
     * <p>
     * 移除所有 {@code {marker}} 标记和 {@code §} 码。
     * <p>
     * 示例：{@code "{green}[World] {white}<Player>"} → {@code "[World] <Player>"}
     */
    public static String stripToPlainText(String text) {
        if (text == null || text.isEmpty()) return text;
        // 移除 {marker} 格式标记，不碰内容里的 [...]
        String result = text.replaceAll("\\{[^}]*\\}", "");
        result = ChatColor.stripColor(result);
        return result;
    }
}
