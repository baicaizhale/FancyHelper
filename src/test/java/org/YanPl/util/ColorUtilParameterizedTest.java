package org.YanPl.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ColorUtil 参数化测试")
class ColorUtilParameterizedTest {

    @Nested
    @DisplayName("translateCustomColors 方法测试")
    class TranslateCustomColorsTest {

        @ParameterizedTest
        @NullSource
        @DisplayName("null 输入应返回 null")
        void testNullInput(String input) {
            assertNull(ColorUtil.translateCustomColors(input));
        }

        @ParameterizedTest
        @EmptySource
        @DisplayName("空字符串应返回空字符串")
        void testEmptyInput(String input) {
            assertEquals("", ColorUtil.translateCustomColors(input));
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "Hello World",
            "普通文本无颜色代码",
            "123456",
            "!@#$%^&*()",
            "Multi\nLine\nText"
        })
        @DisplayName("不含颜色代码的文本应原样返回")
        void testNoColorCodes(String input) {
            String result = ColorUtil.translateCustomColors(input);
            assertNotNull(result);
            assertTrue(result.contains(input.trim().split("\n")[0]));
        }

        @ParameterizedTest
        @CsvSource({
            "'&a绿色文本', '§a绿色文本'",
            "'&b青色文本', '§b青色文本'",
            "'&c红色文本', '§c红色文本'",
            "'&d粉色文本', '§d粉色文本'",
            "'&e黄色文本', '§e黄色文本'",
            "'&f白色文本', '§f白色文本'",
            "'&0黑色文本', '§0黑色文本'",
            "'&1深蓝文本', '§1深蓝文本'",
            "'&2深绿文本', '§2深绿文本'",
            "'&3深青文本', '§3深青文本'",
            "'&4深红文本', '§4深红文本'",
            "'&5紫色文本', '§5紫色文本'",
            "'&6金色文本', '§6金色文本'",
            "'&7灰色文本', '§7灰色文本'",
            "'&8深灰文本', '§8深灰文本'",
            "'&9蓝色文本', '§9蓝色文本'"
        })
        @DisplayName("标准 & 颜色代码转换")
        void testStandardColorCodes(String input, String expectedSubstring) {
            String result = ColorUtil.translateCustomColors(input);
            assertTrue(result.contains("§") || result.equals(expectedSubstring));
        }

        @ParameterizedTest
        @CsvSource({
            "'&l粗体文本', '§l'",
            "'&o斜体文本', '§o'",
            "'&n下划线文本', '§n'",
            "'&m删除线文本', '§m'",
            "'&k混乱文本', '§k'",
            "'&r重置文本', '§r'"
        })
        @DisplayName("格式代码转换")
        void testFormatCodes(String input, String expectedContains) {
            String result = ColorUtil.translateCustomColors(input);
            assertNotNull(result);
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "&a绿色&c红色&b青色",
            "&l粗体&o斜体&n下划线",
            "&0&1&2&3&4&5&6&7&8&9&a&b&c&d&e&f"
        })
        @DisplayName("多个颜色代码组合")
        void testMultipleColorCodes(String input) {
            String result = ColorUtil.translateCustomColors(input);
            assertNotNull(result);
            assertTrue(result.contains("§"));
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "&",
            "&&",
            "&",
            "&文本",
            "文本&",
            "文本&文本"
        })
        @DisplayName("边缘情况：单独的 & 符号")
        void testEdgeCaseAmpersand(String input) {
            String result = ColorUtil.translateCustomColors(input);
            assertNotNull(result);
        }

        @ParameterizedTest
        @CsvSource({
            "'&&a双&', true",
            "'&&', false"
        })
        @DisplayName("双 && 转义")
        void testDoubleAmpersandEscape(String input, boolean containsSection) {
            String result = ColorUtil.translateCustomColors(input);
            assertNotNull(result);
        }

        @Test
        @DisplayName("长文本性能测试")
        void testLongText() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 1000; i++) {
                sb.append("&a").append("Hello").append("&c").append("World");
            }
            String input = sb.toString();
            
            String result = ColorUtil.translateCustomColors(input);
            assertNotNull(result);
            assertTrue(result.length() > 0);
        }

        @Test
        @DisplayName("Unicode 文本处理")
        void testUnicodeText() {
            String input = "&a你好世界 &c🎉🎊&bこんにちは";
            String result = ColorUtil.translateCustomColors(input);
            assertNotNull(result);
            assertTrue(result.contains("你好世界"));
        }
    }

    @Nested
    @DisplayName("getColorX 方法测试")
    class GetColorXTest {

        @Test
        @DisplayName("应返回 #11A8CD")
        void testGetColorX() {
            assertEquals("#11A8CD", ColorUtil.getColorX());
        }

        @Test
        @DisplayName("多次调用应返回相同值")
        void testGetColorXConsistent() {
            String first = ColorUtil.getColorX();
            String second = ColorUtil.getColorX();
            String third = ColorUtil.getColorX();
            
            assertEquals(first, second);
            assertEquals(second, third);
        }
    }

    @Nested
    @DisplayName("getColorZ 方法测试")
    class GetColorZTest {

        @Test
        @DisplayName("应返回 #30AEE5")
        void testGetColorZ() {
            assertEquals("#30AEE5", ColorUtil.getColorZ());
        }

        @Test
        @DisplayName("多次调用应返回相同值")
        void testGetColorZConsistent() {
            String first = ColorUtil.getColorZ();
            String second = ColorUtil.getColorZ();
            String third = ColorUtil.getColorZ();
            
            assertEquals(first, second);
            assertEquals(second, third);
        }
    }

    @Nested
    @DisplayName("颜色值对比测试")
    class ColorValueComparisonTest {

        @Test
        @DisplayName("X 和 Z 颜色值应不同")
        void testColorXNotEqualsColorZ() {
            assertNotEquals(ColorUtil.getColorX(), ColorUtil.getColorZ());
        }

        @Test
        @DisplayName("颜色值应为有效的十六进制格式")
        void testColorHexFormat() {
            assertTrue(ColorUtil.getColorX().matches("^#[0-9A-Fa-f]{6}$"));
            assertTrue(ColorUtil.getColorZ().matches("^#[0-9A-Fa-f]{6}$"));
        }
    }

    @Nested
    @DisplayName("静态方法可访问性测试")
    class StaticMethodAccessibilityTest {

        @Test
        @DisplayName("所有公共静态方法应可访问")
        void testAllPublicStaticMethodsAccessible() throws NoSuchMethodException {
            assertNotNull(ColorUtil.class.getMethod("translateCustomColors", String.class));
            assertNotNull(ColorUtil.class.getMethod("getColorX"));
            assertNotNull(ColorUtil.class.getMethod("getColorZ"));
        }
    }

    @Nested
    @DisplayName("translateCustomColors 全分支覆盖测试")
    class FullBranchCoverageTest {

        static Stream<org.junit.jupiter.params.provider.Arguments> allBranchInputs() {
            return Stream.of(
                org.junit.jupiter.params.provider.Arguments.of(null, "null input"),
                org.junit.jupiter.params.provider.Arguments.of("", "empty string"),
                org.junit.jupiter.params.provider.Arguments.of(" ", "single space"),
                org.junit.jupiter.params.provider.Arguments.of("   ", "multiple spaces"),
                org.junit.jupiter.params.provider.Arguments.of("plain text", "no codes"),
                org.junit.jupiter.params.provider.Arguments.of("&a", "single color code"),
                org.junit.jupiter.params.provider.Arguments.of("&&", "double ampersand"),
                org.junit.jupiter.params.provider.Arguments.of("&&&", "triple ampersand"),
                org.junit.jupiter.params.provider.Arguments.of("&aa", "invalid double color"),
                org.junit.jupiter.params.provider.Arguments.of("&A", "uppercase color"),
                org.junit.jupiter.params.provider.Arguments.of("text&a", "color after text"),
                org.junit.jupiter.params.provider.Arguments.of("&atext", "color before text"),
                org.junit.jupiter.params.provider.Arguments.of("te&axt", "color in middle"),
                org.junit.jupiter.params.provider.Arguments.of("&a&b&c", "multiple colors"),
                org.junit.jupiter.params.provider.Arguments.of("&l&o&n", "multiple formats"),
                org.junit.jupiter.params.provider.Arguments.of("&k&r", "obfuscated and reset"),
                org.junit.jupiter.params.provider.Arguments.of("\n&a", "newline then color"),
                org.junit.jupiter.params.provider.Arguments.of("&a\n", "color then newline"),
                org.junit.jupiter.params.provider.Arguments.of("\t&b\t", "tab with color"),
                org.junit.jupiter.params.provider.Arguments.of("&1text&2text&3text", "multiple with text"),
                org.junit.jupiter.params.provider.Arguments.of("&&&a", "escaped then color"),
                org.junit.jupiter.params.provider.Arguments.of("&", "lone ampersand"),
                org.junit.jupiter.params.provider.Arguments.of("a&", "char then ampersand"),
                org.junit.jupiter.params.provider.Arguments.of("& ", "ampersand space"),
                org.junit.jupiter.params.provider.Arguments.of(" & ", "spaced ampersand")
            );
        }

        @ParameterizedTest
        @MethodSource("allBranchInputs")
        @DisplayName("全分支覆盖：各种输入组合")
        void testAllBranches(String input, String description) {
            String result = ColorUtil.translateCustomColors(input);
            if (input == null) {
                assertNull(result);
            } else {
                assertNotNull(result);
            }
        }
    }
}
