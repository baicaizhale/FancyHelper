package org.YanPl.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ColorUtil 测试")
class ColorUtilParameterizedTest {

    // ======================== translateCustomColors ========================

    @Test
    @DisplayName("null 输入应返回 null，空串应返回空串")
    void testNullAndEmptyInput() {
        assertNull(ColorUtil.translateCustomColors(null));
        assertEquals("", ColorUtil.translateCustomColors(""));
    }

    @Test
    @DisplayName("不含颜色代码的文本应原样返回")
    void testPlainText() {
        assertEquals("Hello World", ColorUtil.translateCustomColors("Hello World"));
    }

    @Test
    @DisplayName("标准 & 颜色代码应转为 § 码")
    void testAmpersandColorCode() {
        assertEquals("§a绿色", ColorUtil.translateCustomColors("&a绿色"));
        assertEquals("§c红色", ColorUtil.translateCustomColors("&c红色"));
    }

    @Test
    @DisplayName("格式代码 &l 应转为 §l")
    void testFormatCode() {
        assertEquals("§l粗体", ColorUtil.translateCustomColors("&l粗体"));
    }

    @Test
    @DisplayName("自定义 &z 应转为 hex 颜色序列")
    void testAmpersandZColor() {
        String expected = net.md_5.bungee.api.ChatColor.of("#30AEE5").toString();
        assertEquals(expected + "文本", ColorUtil.translateCustomColors("&z文本"));
    }

    @Test
    @DisplayName("自定义 &x 应转为 hex 颜色序列")
    void testAmpersandXColor() {
        String expected = net.md_5.bungee.api.ChatColor.of("#11A8CD").toString();
        assertEquals(expected + "文本", ColorUtil.translateCustomColors("&x文本"));
    }

    @Test
    @DisplayName("自定义 §z 应转为 hex 颜色序列")
    void testSectionZColor() {
        String expected = net.md_5.bungee.api.ChatColor.of("#30AEE5").toString();
        assertEquals(expected + "文本", ColorUtil.translateCustomColors("§z文本"));
    }

    @Test
    @DisplayName("非 hex 序列开头的 §x 应转为自定义颜色")
    void testSectionXNotHex() {
        String expected = net.md_5.bungee.api.ChatColor.of("#11A8CD").toString();
        assertEquals(expected + "Hello", ColorUtil.translateCustomColors("§xHello"));
    }

    @Test
    @DisplayName("已存在的 legacy hex 序列应保持原样不被二次替换")
    void testExistingHexPreserved() {
        String hex = "§x§3§0§a§e§e§5FancyHelper";
        assertEquals(hex, ColorUtil.translateCustomColors(hex));
    }

    @Test
    @DisplayName("双 && 是转义，不应产生颜色码")
    void testDoubleAmpersandEscapes() {
        String result = ColorUtil.translateCustomColors("&&");
        assertFalse(result.contains("§"));
    }

    @Test
    @DisplayName("单独的 & 后跟非颜色字符应保持不变")
    void testAmpersandNotColorCode() {
        assertEquals("&文本", ColorUtil.translateCustomColors("&文本"));
    }

    @Test
    @DisplayName("重复调用幂等：已转换的 § 文本不再被改写")
    void testIdempotent() {
        String input = "§zFancyHelper§b§r §7> §cAPI 请求失败 (500)";
        String once = ColorUtil.translateCustomColors(input);
        String twice = ColorUtil.translateCustomColors(once);
        assertEquals(once, twice);
    }

    @Test
    @DisplayName("Unicode 文本应原样保留")
    void testUnicodeText() {
        String result = ColorUtil.translateCustomColors("&a你好世界");
        assertTrue(result.contains("你好世界"));
    }

    // ======================== legacyToReadable ========================

    @Test
    @DisplayName("legacy 颜色码应转为可读标记")
    void testLegacyToReadable() {
        assertEquals("{green}Hello {red}World", ColorUtil.legacyToReadable("§aHello §cWorld"));
        assertEquals("{reset}结束", ColorUtil.legacyToReadable("§r结束"));
    }

    @Test
    @DisplayName("legacy hex 序列应转为 {#RRGGBB} 标记")
    void testLegacyToReadableHex() {
        String result = ColorUtil.legacyToReadable("§x§1§1§a§8§c§d hi");
        assertEquals("{#11a8cd} hi", result);
    }

    @Test
    @DisplayName("null 或空输入应原样返回")
    void testLegacyToReadableNullAndEmpty() {
        assertNull(ColorUtil.legacyToReadable(null));
        assertEquals("", ColorUtil.legacyToReadable(""));
    }

    @Test
    @DisplayName("无颜色码的文本应原样返回")
    void testLegacyToReadablePlain() {
        assertEquals("plain text", ColorUtil.legacyToReadable("plain text"));
    }

    // ======================== stripToPlainText ========================

    @Test
    @DisplayName("应移除 {marker} 标记")
    void testStripMarkers() {
        assertEquals("[World] <Player>", ColorUtil.stripToPlainText("{green}[World] {white}<Player>"));
    }

    @Test
    @DisplayName("应移除 § 颜色码")
    void testStripColorCodes() {
        assertEquals("Hello", ColorUtil.stripToPlainText("§aHello"));
    }

    @Test
    @DisplayName("null 或空输入应原样返回")
    void testStripNullAndEmpty() {
        assertNull(ColorUtil.stripToPlainText(null));
        assertEquals("", ColorUtil.stripToPlainText(""));
    }

    // ======================== getColorX / getColorZ ========================

    @Test
    @DisplayName("颜色常量应返回固定 hex 值")
    void testColorConstants() {
        assertEquals("#11A8CD", ColorUtil.getColorX());
        assertEquals("#30AEE5", ColorUtil.getColorZ());
        assertNotEquals(ColorUtil.getColorX(), ColorUtil.getColorZ());
    }
}
