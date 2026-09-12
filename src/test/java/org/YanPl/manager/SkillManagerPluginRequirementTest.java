package org.YanPl.manager;

import org.YanPl.FancyHelper;
import org.YanPl.model.Skill;
import org.YanPl.model.SkillMetadata;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("SkillManager 插件依赖过滤测试")
class SkillManagerPluginRequirementTest {

    private MockedStatic<Bukkit> bukkitMock;
    private SkillManager manager;

    @BeforeEach
    void setUp() {
        FancyHelper plugin = Mockito.mock(FancyHelper.class);
        ConfigManager configManager = Mockito.mock(ConfigManager.class);
        Mockito.when(configManager.isDebug()).thenReturn(false);
        Mockito.when(plugin.getConfigManager()).thenReturn(configManager);
        manager = new SkillManager(plugin);
    }

    @AfterEach
    void tearDown() {
        if (bukkitMock != null) {
            bukkitMock.close();
            bukkitMock = null;
        }
    }

    private void mockInstalledPlugins(String... names) {
        if (bukkitMock != null) {
            bukkitMock.close();
        }
        PluginManager pm = Mockito.mock(PluginManager.class);
        Plugin[] plugins = Arrays.stream(names).map(n -> {
            Plugin p = Mockito.mock(Plugin.class);
            Mockito.when(p.getName()).thenReturn(n);
            return p;
        }).toArray(Plugin[]::new);
        Mockito.when(pm.getPlugins()).thenReturn(plugins);
        bukkitMock = Mockito.mockStatic(Bukkit.class);
        bukkitMock.when(Bukkit::getPluginManager).thenReturn(pm);
    }

    private Skill newSkill(String id, String... requiresPlugin) {
        SkillMetadata metadata = new SkillMetadata();
        metadata.setName(id);
        metadata.setTriggers(Arrays.asList(id));
        metadata.setRequiresPlugin(Arrays.asList(requiresPlugin));
        return new Skill(id, metadata, "content", "full", null, false, false);
    }

    @Test
    @DisplayName("依赖插件未安装时自动注入被过滤")
    void testFilteredWhenPluginMissing() {
        manager.getRegistry().register(newSkill("luckperms", "LuckPerms"));
        mockInstalledPlugins("EssentialsX", "Vault");

        List<Skill> matches = manager.findMatchingSkills("luckperms", 3, 30);
        assertTrue(matches.isEmpty(), "未安装 LuckPerms 时不应注入");
        assertFalse(manager.isPluginRequirementSatisfied(newSkill("luckperms", "LuckPerms")));
    }

    @Test
    @DisplayName("依赖插件已安装时正常注入（大小写不敏感）")
    void testKeptWhenPluginInstalled() {
        manager.getRegistry().register(newSkill("luckperms", "LuckPerms"));
        mockInstalledPlugins("luckperms");

        List<Skill> matches = manager.findMatchingSkills("luckperms", 3, 30);
        assertEquals(1, matches.size());
        assertTrue(manager.isPluginRequirementSatisfied(newSkill("luckperms", "LuckPerms")));
    }

    @Test
    @DisplayName("无依赖的 Skill 不受影响")
    void testNoRequirementPasses() {
        manager.getRegistry().register(newSkill("give"));
        mockInstalledPlugins();

        List<Skill> matches = manager.findMatchingSkills("give", 3, 30);
        assertEquals(1, matches.size());
    }

    @Test
    @DisplayName("多插件依赖要求全部安装")
    void testMultipleRequirements() {
        mockInstalledPlugins("Multiverse-Core");
        assertFalse(manager.isPluginRequirementSatisfied(newSkill("mv", "Multiverse-Core", "Multiverse-Portals")));
        mockInstalledPlugins("Multiverse-Core", "Multiverse-Portals");
        assertTrue(manager.isPluginRequirementSatisfied(newSkill("mv", "Multiverse-Core", "Multiverse-Portals")));
    }

    @Test
    @DisplayName("提示词技能列表过滤未装插件的 Skill")
    void testSummariesForPromptFiltered() {
        manager.getRegistry().register(newSkill("luckperms", "LuckPerms"));
        manager.getRegistry().register(newSkill("give"));
        mockInstalledPlugins("Vault");

        List<String> summaries = manager.getSkillSummariesForPrompt();
        assertEquals(1, summaries.size());
        assertTrue(summaries.get(0).startsWith("give"));

        List<String> brief = manager.getSkillBriefList();
        assertEquals(1, brief.size());
        assertTrue(brief.get(0).startsWith("give"));
    }

    @Test
    @DisplayName("手动加载未被过滤（过滤只作用于自动注入与提示词列表）")
    void testManualLoadNotFiltered() {
        manager.getRegistry().register(newSkill("luckperms", "LuckPerms"));
        mockInstalledPlugins();

        assertEquals(1, manager.getRegistry().getAllSkills().size());
    }
}
