package org.YanPl.manager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ToolExecutor.normalizeJsonArgs 测试")
class ToolExecutorJsonArgsTest {

    // —— 应翻译的 7 组工具 ——

    @Test
    @DisplayName("#search JSON → 裸 query")
    void testSearch() {
        assertEquals("boats are fast",
                ToolExecutor.normalizeJsonArgs("#search", "{\"query\":\"boats are fast\"}"));
    }

    @Test
    @DisplayName("#run JSON → 裸 command（命令内含冒号不受首冒号切分影响）")
    void testRun() {
        assertEquals("say hi: there",
                ToolExecutor.normalizeJsonArgs("#run", "{\"command\":\"say hi: there\"}"));
    }

    @Test
    @DisplayName("#list / #read JSON → 路径 与 路径+范围")
    void testFileTools() {
        assertEquals("plugins/FancyHelper",
                ToolExecutor.normalizeJsonArgs("#list", "{\"path\":\"plugins/FancyHelper\"}"));
        assertEquals("config.yml 1-50",
                ToolExecutor.normalizeJsonArgs("#read", "{\"path\":\"config.yml\",\"range\":\"1-50\"}"));
        assertEquals("config.yml",
                ToolExecutor.normalizeJsonArgs("#read", "{\"path\":\"config.yml\"}"));
    }

    @Test
    @DisplayName("#skill / #unloadskill JSON → 裸 id（含 list/read 子命令）")
    void testSkillTools() {
        assertEquals("redstone", ToolExecutor.normalizeJsonArgs("#skill", "{\"id\":\"redstone\"}"));
        assertEquals("redstone list",
                ToolExecutor.normalizeJsonArgs("#skill", "{\"id\":\"redstone\",\"action\":\"list\"}"));
        assertEquals("redstone read notes.md",
                ToolExecutor.normalizeJsonArgs("#skill", "{\"id\":\"redstone\",\"action\":\"read\",\"file\":\"notes.md\"}"));
        assertEquals("redstone", ToolExecutor.normalizeJsonArgs("#unloadskill", "{\"id\":\"redstone\"}"));
    }

    @Test
    @DisplayName("#forget / #forget_global JSON → 序号（数字键也取串）")
    void testForget() {
        assertEquals("all", ToolExecutor.normalizeJsonArgs("#forget", "{\"index\":\"all\"}"));
        assertEquals("3", ToolExecutor.normalizeJsonArgs("#forget", "{\"index\":3}"));
        assertEquals("2", ToolExecutor.normalizeJsonArgs("#forget_global", "{\"index\":2}"));
    }

    @Test
    @DisplayName("#remember 系列按 category 有无输出 管道格式")
    void testRememberFamily() {
        assertEquals("likes boats",
                ToolExecutor.normalizeJsonArgs("#remember", "{\"content\":\"likes boats\"}"));
        assertEquals("style|concise",
                ToolExecutor.normalizeJsonArgs("#remember", "{\"category\":\"style\",\"content\":\"concise\"}"));
        assertEquals("rule|周五禁放 TNT",
                ToolExecutor.normalizeJsonArgs("#remember_global", "{\"category\":\"rule\",\"content\":\"周五禁放 TNT\"}"));
        assertEquals("2|style|new text",
                ToolExecutor.normalizeJsonArgs("#edit_memory", "{\"index\":2,\"category\":\"style\",\"content\":\"new text\"}"));
        assertEquals("1|rule|new rule",
                ToolExecutor.normalizeJsonArgs("#edit_global", "{\"index\":1,\"category\":\"rule\",\"content\":\"new rule\"}"));
    }

    @Test
    @DisplayName("#mcp JSON → server.tool|json 管道格式")
    void testMcp() {
        assertEquals("weather.query|{\"city\":\"Beijing\"}",
                ToolExecutor.normalizeJsonArgs("#mcp",
                        "{\"server\":\"weather\",\"tool\":\"query\",\"arguments\":{\"city\":\"Beijing\"}}"));
    }

    @Test
    @DisplayName("#webfetch JSON 也在归一化清单内（与专属解析等价）")
    void testWebfetchCovered() {
        assertEquals("https://example.com",
                ToolExecutor.normalizeJsonArgs("#webfetch", "{\"url\":\"https://example.com\"}"));
    }

    // —— 不翻译/透传 ——

    @Test
    @DisplayName("非法 JSON 原样透传")
    void testInvalidJsonPassthrough() {
        String bad = "{oops: not json";
        assertEquals(bad, ToolExecutor.normalizeJsonArgs("#search", bad));
    }

    @Test
    @DisplayName("键名对不上时原样透传（走 handler 旧行为兜底）")
    void testWrongKeyPassthrough() {
        String wrong = "{\"q\":\"boats\"}";
        assertEquals(wrong, ToolExecutor.normalizeJsonArgs("#search", wrong));
    }

    @Test
    @DisplayName("JSON 协议工具（ask/edit/write/todo）不归一化")
    void testJsonProtocolToolsExcluded() {
        String ask = "{\"question\":\"Which?\"}";
        assertEquals(ask, ToolExecutor.normalizeJsonArgs("#ask", ask));
        String edit = "{\"path\":\"a.txt\",\"original\":\"x\",\"replacement\":\"y\"}";
        assertEquals(edit, ToolExecutor.normalizeJsonArgs("#edit", edit));
    }

    @Test
    @DisplayName("裸参数与 null 原样返回")
    void testBareAndNull() {
        assertEquals("config.yml", ToolExecutor.normalizeJsonArgs("#read", "config.yml"));
        assertNull(ToolExecutor.normalizeJsonArgs("#read", null));
    }
}
