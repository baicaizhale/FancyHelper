package org.YanPl.manager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ToolExecutor 命令存在性校验测试")
class ToolExecutorCommandCheckTest {

    @Test
    @DisplayName("首词完全命中命令表 → 放行")
    void testExactMatchPasses() {
        List<String> table = Arrays.asList("give", "say", "/pos1", "pos1", "menu");
        assertFalse(ToolExecutor.checkCommand("give baicaizhale tnt", table).blocked());
        assertFalse(ToolExecutor.checkCommand("say hello", table).blocked());
        // 命令表里带斜杠的 /pos1 也应命中放行
        assertFalse(ToolExecutor.checkCommand("/pos1", table).blocked());
    }

    @Test
    @DisplayName("多斜杠命中（模型写 /menu，表里注册 menu）→ 拦截并建议 menu")
    void testExtraSlashBlocked() {
        List<String> table = Arrays.asList("give", "menu");
        ToolExecutor.CommandCheckResult r = ToolExecutor.checkCommand("/menu", table);
        assertTrue(r.blocked());
        assertEquals("menu", r.suggestion());
    }

    @Test
    @DisplayName("少斜杠命中（模型写 pos1，表里注册 /pos1）→ 拦截并建议 /pos1")
    void testMissingSlashBlocked() {
        List<String> table = Arrays.asList("give", "/pos1");
        ToolExecutor.CommandCheckResult r = ToolExecutor.checkCommand("pos1", table);
        assertTrue(r.blocked());
        assertEquals("/pos1", r.suggestion());
    }

    @Test
    @DisplayName("双斜杠命中（模型写 //pos1，表里注册 /pos1）→ 拦截并建议 /pos1")
    void testDoubleSlashBlocked() {
        List<String> table = Arrays.asList("give", "/pos1");
        ToolExecutor.CommandCheckResult r = ToolExecutor.checkCommand("//pos1", table);
        assertTrue(r.blocked());
        assertEquals("/pos1", r.suggestion());
    }

    @Test
    @DisplayName("懒注册命令（命令表完全没有）→ 放行")
    void testLazyRegisteredCommandPasses() {
        List<String> table = Arrays.asList("give", "say");
        // caidan 未注册，任何斜杠变体都不命中 → 放行
        assertFalse(ToolExecutor.checkCommand("caidan x", table).blocked());
        assertFalse(ToolExecutor.checkCommand("/caidan x", table).blocked());
    }

    @Test
    @DisplayName("命令表为空（未索引）→ 放行避免误杀")
    void testEmptyTablePasses() {
        assertFalse(ToolExecutor.checkCommand("/give a", List.of()).blocked());
        assertFalse(ToolExecutor.checkCommand("anything", List.of()).blocked());
    }

    @Test
    @DisplayName("命令表为 null → 放行避免误杀")
    void testNullTablePasses() {
        assertFalse(ToolExecutor.checkCommand("/give a", null).blocked());
    }

    @Test
    @DisplayName("空命令 → 放行")
    void testEmptyCommandPasses() {
        List<String> table = Arrays.asList("give");
        assertFalse(ToolExecutor.checkCommand("", table).blocked());
        assertFalse(ToolExecutor.checkCommand("   ", table).blocked());
    }

    @Test
    @DisplayName("仅斜杠无命令名 → 放行")
    void testBareSlashPasses() {
        List<String> table = Arrays.asList("give");
        assertFalse(ToolExecutor.checkCommand("/", table).blocked());
    }

    @Test
    @DisplayName("带参数的命令只校验首词")
    void testOnlyFirstTokenChecked() {
        List<String> table = Arrays.asList("give");
        // /give 不在表里，但 give 在 → 拦截建议 give
        ToolExecutor.CommandCheckResult r = ToolExecutor.checkCommand("/give baicaizhale tnt", table);
        assertTrue(r.blocked());
        assertEquals("give", r.suggestion());
    }

    @Test
    @DisplayName("空命令但表为 null → 放行")
    void testEmptyCommandNullTablePasses() {
        assertFalse(ToolExecutor.checkCommand("", null).blocked());
    }
}
