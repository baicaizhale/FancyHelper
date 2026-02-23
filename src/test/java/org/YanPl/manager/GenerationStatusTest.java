package org.YanPl.manager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CLIManager.GenerationStatus 测试")
class GenerationStatusTest {

    @Test
    @DisplayName("GenerationStatus 枚举值数量")
    void testEnumValuesCount() {
        assertEquals(8, CLIManager.GenerationStatus.values().length);
    }

    @Test
    @DisplayName("GenerationStatus 枚举值")
    void testEnumValues() {
        assertNotNull(CLIManager.GenerationStatus.valueOf("THINKING"));
        assertNotNull(CLIManager.GenerationStatus.valueOf("EXECUTING_TOOL"));
        assertNotNull(CLIManager.GenerationStatus.valueOf("WAITING_CONFIRM"));
        assertNotNull(CLIManager.GenerationStatus.valueOf("WAITING_CHOICE"));
        assertNotNull(CLIManager.GenerationStatus.valueOf("COMPLETED"));
        assertNotNull(CLIManager.GenerationStatus.valueOf("CANCELLED"));
        assertNotNull(CLIManager.GenerationStatus.valueOf("ERROR"));
        assertNotNull(CLIManager.GenerationStatus.valueOf("IDLE"));
    }

    @Test
    @DisplayName("GenerationStatus valueOf 对无效名称抛出异常")
    void testValueOfInvalid() {
        assertThrows(IllegalArgumentException.class, () -> 
            CLIManager.GenerationStatus.valueOf("INVALID")
        );
    }
}
