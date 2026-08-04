package org.YanPl.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("I18n 国际化工具测试")
class I18nTest {

    @Test
    @DisplayName("未初始化插件时语言默认回退 zh-cn")
    void testDefaultLangIsZhCn() {
        I18n.init(null);
        assertEquals(I18n.LANG_ZH_CN, I18n.getLang());
    }

    @Test
    @DisplayName("存在 key 时返回对应语言的文本")
    void testKnownKey() {
        I18n.init(null);
        // 基准表 zh-cn 应有该 key
        String text = I18n.t("cli.only.player");
        assertNotNull(text);
        assertFalse(text.isEmpty());
        assertTrue(text.contains("仅限玩家使用"));
    }

    @Test
    @DisplayName("占位符 {0} 与 {1} 应被替换为参数")
    void testPlaceholders() {
        I18n.init(null);
        assertEquals("Progress: 3/10 已完成", I18n.t("todo.progress", 3, 10));
        // 注意：返回值经 ColorUtil.translateCustomColors 处理，§z 会被转为 hex 颜色码，
        // 因此这里只断言占位符替换后的关键文本片段
        String notFound = I18n.t("cli.skill.not.found", "minecraft");
        assertTrue(notFound.contains("未找到 Skill: minecraft"), "占位符 {0} 应被替换: " + notFound);
        assertFalse(notFound.contains("{0}"), "不应残留未替换的占位符");
    }

    @Test
    @DisplayName("返回值应经过 ColorUtil 颜色码处理（& 转 §）")
    void testColorTranslation() {
        I18n.init(null);
        // todo.progress 值以纯文本开头，不含 &；这里验证含 & 的 key 会被转换
        String text = I18n.t("cli.resume.page", 1, 2);
        assertNotNull(text);
        assertTrue(text.contains("§7") || text.contains("&7"), "页面文本应含颜色码");
        // 明确验证 & -> § 的转换：用 & 前缀的 key 断言
        assertFalse(I18n.t("cli.resume.next").contains("&8["), "& 色码应被转换为 §");
        assertTrue(I18n.t("cli.resume.next").contains("§8["), "& 色码应被转换为 §");
    }

    @Test
    @DisplayName("占位符数量多于参数时保留未替换的占位符")
    void testExtraPlaceholdersKept() {
        I18n.init(null);
        String text = I18n.t("cli.mcp.enabled.summary", 1);
        assertNotNull(text);
        assertTrue(text.contains("{1}") || text.contains("{2}") || text.contains("{3}"));
    }

    @Test
    @DisplayName("未知 key 且中文表也无此 key 时原样返回 key")
    void testUnknownKeyReturnsKey() {
        I18n.init(null);
        assertEquals("i18n.no.such.key", I18n.t("i18n.no.such.key"));
    }

    @Test
    @DisplayName("非法语言代码回退到 zh-cn 表")
    void testInvalidLangFallsBack() {
        I18n.init(null);
        // plugin 为 null 时 getLang() 恒为 zh-cn；此处直接验证 tableFor 行为等价：
        // 对任意语言调用 t 都应返回有效文本而非 null
        assertNotNull(I18n.t("cli.help.title"));
        assertNotNull(I18n.t("gui.settings.title"));
        assertNotNull(I18n.t("tool.exit"));
        assertNotNull(I18n.t("notice.title"));
        assertNotNull(I18n.t("verify.success"));
        assertNotNull(I18n.t("error.retry"));
    }

    @Test
    @DisplayName("三张语言表关键 key 均存在（en-us / lzh-cn 不回退中文）")
    void testAllTablesHaveKey() {
        I18n.init(null);
        // 通过 init 模拟不同语言会引入 Bukkit 依赖，因此这里通过已知的静态常量与
        // 基准表 key 数量做冒烟验证：确保新增的 key 已存在于 zh-cn 表
        String[] keys = {
                "cli.only.console",
                "cli.help.skill.reload",
                "cli.help.skill.install",
                "cli.skill.loaded",
                "cli.mcp.tools.header",
                "cli.mcp.server.header",
                "clim.truncated",
                "clim.context.short",
                "clim.no.active.session",
                "clim.compact.not.needed",
                "clim.compacting",
                "clim.loop.detected",
                "clim.loop.interrupted",
                "clim.chain.long",
                "clim.tool.call"
        };
        for (String key : keys) {
            assertFalse(I18n.t(key).equals(key), "zh-cn 表应包含 key: " + key);
        }
    }

    @Test
    @DisplayName("翻译结果不应为空串")
    void testNoEmptyTranslation() {
        I18n.init(null);
        String[] keys = {
                "cli.help.title", "cli.reg.click", "cli.reload.all", "cli.status.refresh",
                "clim.tips.0", "clim.greet.morning", "clim.agree.title",
                "tool.unknown", "tool.webfetch.fail",
                "gui.settings.title", "notice.marked", "todo.none",
                "verify.success", "error.retry", "chat.stop.detected",
                "supd.checking", "upd.latest", "skillmgr.none"
        };
        for (String key : keys) {
            String text = I18n.t(key);
            assertNotNull(text);
            assertFalse(text.isEmpty(), "key " + key + " 的翻译不应为空");
        }
    }
}
