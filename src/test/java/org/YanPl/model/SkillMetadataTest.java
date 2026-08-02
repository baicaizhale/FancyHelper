package org.YanPl.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SkillMetadata 测试")
class SkillMetadataTest {

    // ======================== fromYaml 解析 ========================

    @Test
    @DisplayName("null 或空内容应返回默认元数据")
    void testNullAndEmptyContent() {
        SkillMetadata nullMeta = SkillMetadata.fromYaml(null);
        assertEquals("", nullMeta.getName());
        assertEquals("Unknown", nullMeta.getAuthor());
        assertEquals("1.0.0", nullMeta.getVersion());
        assertTrue(nullMeta.getTriggers().isEmpty());
        assertTrue(nullMeta.isAutoTrigger());
        assertEquals(50, nullMeta.getPriority());

        SkillMetadata emptyMeta = SkillMetadata.fromYaml("   \n  ");
        assertEquals("", emptyMeta.getName());
        assertEquals(50, emptyMeta.getPriority());
    }

    @Test
    @DisplayName("应解析 name/description/author/version/priority")
    void testParseBasicFields() {
        SkillMetadata meta = SkillMetadata.fromYaml("""
            name: 测试技能
            description: 描述文本
            author: YanPl
            version: 2.3.0
            priority: 80
            """);

        assertEquals("测试技能", meta.getName());
        assertEquals("描述文本", meta.getDescription());
        assertEquals("YanPl", meta.getAuthor());
        assertEquals("2.3.0", meta.getVersion());
        assertEquals(80, meta.getPriority());
    }

    @Test
    @DisplayName("应解析 triggers 列表")
    void testParseTriggersList() {
        SkillMetadata meta = SkillMetadata.fromYaml("""
            name: 测试
            triggers:
              - 你好
              - hello
              - 早上好
            """);

        assertEquals(List.of("你好", "hello", "早上好"), meta.getTriggers());
    }

    @Test
    @DisplayName("triggers 为逗号分隔字符串时应拆分")
    void testParseTriggersString() {
        SkillMetadata meta = SkillMetadata.fromYaml("""
            name: 测试
            triggers: "你好, hello, 早上好"
            """);

        assertEquals(List.of("你好", "hello", "早上好"), meta.getTriggers());
    }

    @Test
    @DisplayName("应解析 trigger_weights")
    void testParseTriggerWeights() {
        SkillMetadata meta = SkillMetadata.fromYaml("""
            name: 测试
            trigger_weights:
              hello: 5
              hi: 3
            """);

        assertEquals(5, meta.getTriggerWeights().get("hello"));
        assertEquals(3, meta.getTriggerWeights().get("hi"));
        assertEquals(2, meta.getTriggerWeights().size());
    }

    @Test
    @DisplayName("应解析 variables")
    void testParseVariables() {
        SkillMetadata meta = SkillMetadata.fromYaml("""
            name: 测试
            variables:
              world: 主世界
              prefix: "[测试] "
            """);

        assertEquals("主世界", meta.getVariables().get("world"));
        assertEquals("[测试] ", meta.getVariables().get("prefix"));
    }

    @Test
    @DisplayName("variables 中非字符串键值应被忽略")
    void testParseVariablesSkipsNonString() {
        SkillMetadata meta = SkillMetadata.fromYaml("""
            name: 测试
            variables:
              world: 主世界
              count: 3
            """);

        assertEquals("主世界", meta.getVariables().get("world"));
        assertFalse(meta.getVariables().containsKey("count"));
    }

    @Test
    @DisplayName("auto_trigger 默认 true，显式 false 时生效")
    void testParseAutoTrigger() {
        SkillMetadata defaultMeta = SkillMetadata.fromYaml("name: 测试");
        assertTrue(defaultMeta.isAutoTrigger());

        SkillMetadata disabled = SkillMetadata.fromYaml("name: 测试\nauto_trigger: false");
        assertFalse(disabled.isAutoTrigger());
    }

    @Test
    @DisplayName("未知字段应存入 extra")
    void testParseExtraFields() {
        SkillMetadata meta = SkillMetadata.fromYaml("""
            name: 测试
            icon: diamond
            enabled: true
            """);

        assertEquals("diamond", meta.getExtra().get("icon"));
        assertEquals(true, meta.getExtra().get("enabled"));
        assertFalse(meta.getExtra().containsKey("name"));
    }

    @Test
    @DisplayName("非法 YAML 应回退到默认元数据而不抛异常")
    void testInvalidYamlReturnsDefaults() {
        SkillMetadata meta = SkillMetadata.fromYaml("name: [未闭合");

        assertEquals("", meta.getName());
        assertEquals("Unknown", meta.getAuthor());
        assertTrue(meta.getTriggers().isEmpty());
    }

    @Test
    @DisplayName("非 Map 的 YAML 内容应回退到默认元数据")
    void testNonMapYamlReturnsDefaults() {
        SkillMetadata meta = SkillMetadata.fromYaml("just a plain string");

        assertEquals("", meta.getName());
        assertEquals(50, meta.getPriority());
    }

    // ======================== toYaml / round-trip ========================

    @Test
    @DisplayName("toYaml 后 fromYaml 应还原字段（round-trip）")
    void testRoundTrip() {
        SkillMetadata original = SkillMetadata.fromYaml("""
            name: 测试技能
            description: 描述
            author: YanPl
            version: 2.0.0
            priority: 90
            triggers:
              - 你好
              - hello
            trigger_weights:
              hello: 5
            variables:
              world: 主世界
            categories:
              - 管理
            source: https://example.com
            icon: diamond
            """);

        SkillMetadata reloaded = SkillMetadata.fromYaml(original.toYaml());

        assertEquals(original.getName(), reloaded.getName());
        assertEquals(original.getDescription(), reloaded.getDescription());
        assertEquals(original.getAuthor(), reloaded.getAuthor());
        assertEquals(original.getVersion(), reloaded.getVersion());
        assertEquals(original.getPriority(), reloaded.getPriority());
        assertEquals(original.getTriggers(), reloaded.getTriggers());
        assertEquals(original.getTriggerWeights(), reloaded.getTriggerWeights());
        assertEquals(original.getVariables(), reloaded.getVariables());
        assertEquals(original.getCategories(), reloaded.getCategories());
        assertEquals(original.getSource(), reloaded.getSource());
        assertEquals(original.getExtra(), reloaded.getExtra());
    }

    @Test
    @DisplayName("空 triggers/空 extra 不应出现在 toYaml 输出中")
    void testToYamlOmitsEmptyFields() {
        SkillMetadata meta = new SkillMetadata();
        meta.setName("简单技能");

        String yaml = meta.toYaml();

        assertFalse(yaml.contains("triggers"));
        assertFalse(yaml.contains("priority"));
        assertTrue(yaml.contains("name: 简单技能"));
    }

    @Test
    @DisplayName("priority 为默认值 50 时不输出，非默认时输出")
    void testToYamlPriorityDefaultOmitted() {
        SkillMetadata defaultMeta = new SkillMetadata();
        assertFalse(defaultMeta.toYaml().contains("priority"));

        SkillMetadata custom = new SkillMetadata();
        custom.setPriority(10);
        assertTrue(custom.toYaml().contains("priority: 10"));
    }

    @Test
    @DisplayName("getFullName 应拼接 name 与 version")
    void testGetFullName() {
        SkillMetadata meta = new SkillMetadata();
        meta.setName("测试");
        meta.setVersion("1.2.3");

        assertEquals("测试 v1.2.3", meta.getFullName());
    }

    @Test
    @DisplayName("setter 应防御性拷贝，外部修改不影响内部状态")
    void testSettersDefensiveCopy() {
        SkillMetadata meta = new SkillMetadata();

        List<String> triggers = new java.util.ArrayList<>(Arrays.asList("a", "b"));
        meta.setTriggers(triggers);
        triggers.add("c");
        assertEquals(List.of("a", "b"), meta.getTriggers());

        Map<String, Integer> weights = new java.util.HashMap<>();
        weights.put("a", 1);
        meta.setTriggerWeights(weights);
        weights.put("b", 2);
        assertEquals(1, meta.getTriggerWeights().size());
    }
}
