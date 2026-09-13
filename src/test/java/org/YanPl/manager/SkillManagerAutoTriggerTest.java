package org.YanPl.manager;

import org.YanPl.FancyHelper;
import org.YanPl.model.Skill;
import org.YanPl.model.SkillMetadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("SkillManager 自动注入测试")
class SkillManagerAutoTriggerTest {

    private SkillManager newManager() {
        FancyHelper plugin = Mockito.mock(FancyHelper.class);
        ConfigManager configManager = Mockito.mock(ConfigManager.class);
        Mockito.when(configManager.isDebug()).thenReturn(false);
        Mockito.when(plugin.getConfigManager()).thenReturn(configManager);
        return new SkillManager(plugin);
    }

    private Skill newSkill(String id, List<String> triggers, boolean autoTrigger) {
        SkillMetadata metadata = new SkillMetadata();
        metadata.setName(id);
        metadata.setTriggers(triggers);
        metadata.setAutoTrigger(autoTrigger);
        return new Skill(id, metadata, "content", "full content", null, false, false);
    }

    @Test
    @DisplayName("auto_trigger: false 的 Skill 不参与自动注入")
    void testAutoTriggerFalseNotInjected() {
        SkillManager manager = newManager();
        manager.getRegistry().register(newSkill("give", Arrays.asList("获取"), false));

        List<Skill> matches = manager.findMatchingSkills("帮我获取帮助", 3, 30);
        assertTrue(matches.isEmpty(), "auto_trigger: false 的 Skill 不应被自动注入");
    }

    @Test
    @DisplayName("auto_trigger: true（默认）的 Skill 正常注入")
    void testAutoTriggerTrueInjected() {
        SkillManager manager = newManager();
        manager.getRegistry().register(newSkill("give", Arrays.asList("获取"), true));

        List<Skill> matches = manager.findMatchingSkills("帮我获取帮助", 3, 30);
        assertEquals(1, matches.size());
        assertEquals("give", matches.get(0).getId());
    }

    @Test
    @DisplayName("auto_trigger: false 的 Skill 仍可手动加载")
    void testManualLoadStillWorks() {
        SkillManager manager = newManager();
        manager.getRegistry().register(newSkill("give", Arrays.asList("获取"), false));

        assertTrue(manager.getRegistry().findBestMatch("帮我获取帮助") != null);
        assertEquals(1, manager.getRegistry().getAllSkills().size());
    }
}
