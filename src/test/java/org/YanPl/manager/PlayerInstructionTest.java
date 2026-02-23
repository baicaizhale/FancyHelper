package org.YanPl.manager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("InstructionManager.PlayerInstruction 测试")
class PlayerInstructionTest {

    @Test
    @DisplayName("构造函数应该正确设置内容")
    void testConstructor() {
        InstructionManager.PlayerInstruction instruction = 
            new InstructionManager.PlayerInstruction("记住这个", "general");
        
        assertEquals("记住这个", instruction.getContent());
        assertEquals("general", instruction.getCategory());
        assertNotNull(instruction.getTimestamp());
    }

    @Test
    @DisplayName("category 为 null 时应该默认为 general")
    void testNullCategoryDefaultsToGeneral() {
        InstructionManager.PlayerInstruction instruction = 
            new InstructionManager.PlayerInstruction("内容", null);
        
        assertEquals("内容", instruction.getContent());
        assertEquals("general", instruction.getCategory());
    }

    @Test
    @DisplayName("timestamp 应该是 ISO 格式")
    void testTimestampFormat() {
        InstructionManager.PlayerInstruction instruction = 
            new InstructionManager.PlayerInstruction("内容", "test");
        
        String timestamp = instruction.getTimestamp();
        assertNotNull(timestamp);
        assertTrue(timestamp.contains("T"));
    }

    @Test
    @DisplayName("不同分类应该正确设置")
    void testDifferentCategories() {
        InstructionManager.PlayerInstruction instruction1 = 
            new InstructionManager.PlayerInstruction("内容1", "preference");
        InstructionManager.PlayerInstruction instruction2 = 
            new InstructionManager.PlayerInstruction("内容2", "reminder");
        
        assertEquals("preference", instruction1.getCategory());
        assertEquals("reminder", instruction2.getCategory());
    }
}
