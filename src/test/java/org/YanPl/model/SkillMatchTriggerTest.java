package org.YanPl.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Skill 触发词匹配测试")
class SkillMatchTriggerTest {

    private Skill newSkill(String name, String... triggers) {
        SkillMetadata metadata = new SkillMetadata();
        metadata.setName(name);
        metadata.setTriggers(triggers.length == 0
                ? Collections.emptyList()
                : Arrays.asList(triggers));
        return new Skill("test", metadata, "content", "full", null, false, false);
    }

    @Test
    @DisplayName("ASCII 触发词整词命中，子串不命中（forgive 不触发 give）")
    void testAsciiTriggerWordBoundary() {
        Skill skill = newSkill("give", "give");
        assertEquals(0, skill.matchTrigger("forgive me"), "forgive 中的 give 子串不应命中");
        assertTrue(skill.matchTrigger("give me tnt") >= 70, "独立单词 give 应命中");
        assertEquals(100, skill.matchTrigger("give"), "完整等于触发词为最高分");
    }

    @Test
    @DisplayName("ASCII 名称要求整词命中，子串不命中")
    void testAsciiNameWordBoundary() {
        Skill skill = newSkill("give");
        assertEquals(0, skill.matchTrigger("forgive me"), "名称 give 在 forgive 中不应命中");
        assertEquals(40, skill.matchTrigger("give me a diamond"), "独立单词 give 名称命中 40 分");
    }

    @Test
    @DisplayName("空名称不参与匹配")
    void testEmptyNameNeverMatches() {
        Skill skill = newSkill("");
        assertEquals(0, skill.matchTrigger("任意输入"), "空名称不应给任何输入加分");
    }

    @Test
    @DisplayName("中文触发词保持包含匹配")
    void testChineseTriggerContains() {
        Skill skill = newSkill("领地", "领地");
        assertTrue(skill.matchTrigger("帮我圈个领地") >= 50, "中文触发词包含即命中");
        assertEquals(0, skill.matchTrigger("帮我圈个地皮"), "不含触发词不命中");
    }

    @Test
    @DisplayName("无触发词无名称时不匹配")
    void testNoSignalsNoMatch() {
        Skill skill = newSkill("");
        assertEquals(0, skill.matchTrigger("anything"));
        // Skill 构造器 metadata 为 null 时兜底默认 metadata（名称为空）
        Skill skill3 = new Skill("test", null, "c", "f", (File) null, false, false);
        assertEquals(0, skill3.matchTrigger("anything"));
    }
}
