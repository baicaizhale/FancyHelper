package org.YanPl.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("SkillMetadata requires_plugin 解析测试")
class SkillMetadataRequiresPluginTest {

    @Test
    @DisplayName("字符串形式解析")
    void testParseStringForm() {
        SkillMetadata metadata = SkillMetadata.fromYaml("name: \"luckperms\"\nrequires_plugin: \"LuckPerms\"");
        assertEquals(Arrays.asList("LuckPerms"), metadata.getRequiresPlugin());
    }

    @Test
    @DisplayName("列表形式解析")
    void testParseListForm() {
        SkillMetadata metadata = SkillMetadata.fromYaml(
                "name: \"multiverse\"\nrequires_plugin:\n  - \"Multiverse-Core\"\n  - \"Multiverse-Portals\"");
        assertEquals(Arrays.asList("Multiverse-Core", "Multiverse-Portals"), metadata.getRequiresPlugin());
    }

    @Test
    @DisplayName("逗号分隔字符串解析")
    void testParseCommaSeparated() {
        SkillMetadata metadata = SkillMetadata.fromYaml("requires_plugin: \"Multiverse-Core, Multiverse-Portals\"");
        assertEquals(Arrays.asList("Multiverse-Core", "Multiverse-Portals"), metadata.getRequiresPlugin());
    }

    @Test
    @DisplayName("缺省为空列表")
    void testDefaultEmpty() {
        SkillMetadata metadata = SkillMetadata.fromYaml("name: \"give\"");
        assertTrue(metadata.getRequiresPlugin().isEmpty());
    }

    @Test
    @DisplayName("toYaml 往返保留字段")
    void testYamlRoundTrip() {
        SkillMetadata metadata = SkillMetadata.fromYaml("name: \"vault\"\nrequires_plugin: \"Vault\"");
        SkillMetadata parsed = SkillMetadata.fromYaml(metadata.toYaml());
        assertEquals(Arrays.asList("Vault"), parsed.getRequiresPlugin());
    }
}
