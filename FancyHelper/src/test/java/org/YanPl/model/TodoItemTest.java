package org.YanPl.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

/**
 * TodoItem 单元测试
 */
@DisplayName("TodoItem 测试")
class TodoItemTest {

    private TodoItem todoItem;

    @BeforeEach
    void setUp() {
        todoItem = new TodoItem();
    }

    @Test
    @DisplayName("默认构造函数应该设置正确的默认值")
    void testDefaultConstructor() {
        TodoItem item = new TodoItem();
        assertNull(item.getId());
        assertNull(item.getTask());
        assertEquals(TodoItem.Status.PENDING, item.getStatus());
        assertEquals("medium", item.getPriority());
    }

    @Test
    @DisplayName("带 id 和 task 的构造函数应该正确设置属性")
    void testConstructorWithIdAndTask() {
        TodoItem item = new TodoItem("1", "测试任务");
        assertEquals("1", item.getId());
        assertEquals("测试任务", item.getTask());
        assertEquals(TodoItem.Status.PENDING, item.getStatus());
    }

    @Test
    @DisplayName("带 id、task 和 status 的构造函数应该正确设置属性")
    void testConstructorWithIdTaskAndStatus() {
        TodoItem item = new TodoItem("2", "进行中任务", TodoItem.Status.IN_PROGRESS);
        assertEquals("2", item.getId());
        assertEquals("进行中任务", item.getTask());
        assertEquals(TodoItem.Status.IN_PROGRESS, item.getStatus());
    }

    @Test
    @DisplayName("完整构造函数应该正确设置所有属性")
    void testFullConstructor() {
        TodoItem item = new TodoItem("3", "完成任务", TodoItem.Status.COMPLETED, "任务描述");
        assertEquals("3", item.getId());
        assertEquals("完成任务", item.getTask());
        assertEquals(TodoItem.Status.COMPLETED, item.getStatus());
        assertEquals("任务描述", item.getDescription());
    }

    @Test
    @DisplayName("Setter 和 Getter 应该正常工作")
    void testSettersAndGetters() {
        todoItem.setId("test-id");
        todoItem.setTask("测试任务");
        todoItem.setStatus(TodoItem.Status.IN_PROGRESS);
        todoItem.setDescription("测试描述");
        todoItem.setPriority("high");

        assertEquals("test-id", todoItem.getId());
        assertEquals("测试任务", todoItem.getTask());
        assertEquals(TodoItem.Status.IN_PROGRESS, todoItem.getStatus());
        assertEquals("测试描述", todoItem.getDescription());
        assertEquals("high", todoItem.getPriority());
    }

    @ParameterizedTest
    @DisplayName("Status.fromString 应该正确解析各种字符串")
    @CsvSource({
        "pending, PENDING",
        "PENDING, PENDING",
        "in_progress, IN_PROGRESS",
        "IN_PROGRESS, IN_PROGRESS",
        "in-progress, IN_PROGRESS",
        "inprogress, IN_PROGRESS",
        "completed, COMPLETED",
        "COMPLETED, COMPLETED",
        "done, COMPLETED",
        "DONE, COMPLETED",
        "cancelled, CANCELLED",
        "CANCELLED, CANCELLED",
        "canceled, CANCELLED"
    })
    void testStatusFromString(String input, TodoItem.Status expected) {
        assertEquals(expected, TodoItem.Status.fromString(input));
    }

    @Test
    @DisplayName("Status.fromString 对 null 应该返回 PENDING")
    void testStatusFromStringNull() {
        assertEquals(TodoItem.Status.PENDING, TodoItem.Status.fromString(null));
    }

    @Test
    @DisplayName("Status.fromString 对未知字符串应该返回 PENDING")
    void testStatusFromStringUnknown() {
        assertEquals(TodoItem.Status.PENDING, TodoItem.Status.fromString("unknown"));
    }

    @Test
    @DisplayName("字符串 setter 应该正确解析状态")
    void testSetStatusWithString() {
        todoItem.setStatus("completed");
        assertEquals(TodoItem.Status.COMPLETED, todoItem.getStatus());

        todoItem.setStatus("in_progress");
        assertEquals(TodoItem.Status.IN_PROGRESS, todoItem.getStatus());
    }

    @Test
    @DisplayName("getDisplayText 应该返回正确的格式")
    void testGetDisplayText() {
        todoItem.setTask("待办事项");
        todoItem.setStatus(TodoItem.Status.PENDING);
        assertEquals("☐ 待办事项", todoItem.getDisplayText());

        todoItem.setStatus(TodoItem.Status.IN_PROGRESS);
        assertEquals("» 待办事项", todoItem.getDisplayText());

        todoItem.setStatus(TodoItem.Status.COMPLETED);
        assertEquals("✓ 待办事项", todoItem.getDisplayText());

        todoItem.setStatus(TodoItem.Status.CANCELLED);
        assertEquals("✗ 待办事项", todoItem.getDisplayText());
    }

    @Test
    @DisplayName("getFullDisplayText 应该包含描述（如果有）")
    void testGetFullDisplayText() {
        todoItem.setTask("任务名称");
        todoItem.setStatus(TodoItem.Status.PENDING);
        todoItem.setDescription("详细描述");

        String result = todoItem.getFullDisplayText();
        assertTrue(result.contains("任务名称"));
        assertTrue(result.contains("详细描述"));
        assertTrue(result.contains("\n"));
    }

    @Test
    @DisplayName("getFullDisplayText 不应该包含描述（如果没有）")
    void testGetFullDisplayTextWithoutDescription() {
        todoItem.setTask("任务名称");
        todoItem.setStatus(TodoItem.Status.PENDING);

        String result = todoItem.getFullDisplayText();
        assertTrue(result.contains("任务名称"));
        assertFalse(result.contains("\n"));
    }

    @Test
    @DisplayName("toString 应该包含所有属性")
    void testToString() {
        todoItem.setId("1");
        todoItem.setTask("测试");
        todoItem.setStatus(TodoItem.Status.COMPLETED);
        todoItem.setDescription("描述");
        todoItem.setPriority("high");

        String result = todoItem.toString();
        assertTrue(result.contains("id='1'"));
        assertTrue(result.contains("task='测试'"));
        assertTrue(result.contains("status=COMPLETED"));
        assertTrue(result.contains("description='描述'"));
        assertTrue(result.contains("priority='high'"));
    }

    @Test
    @DisplayName("Status 枚举应该有正确的图标和描述")
    void testStatusEnum() {
        assertEquals("☐", TodoItem.Status.PENDING.getIcon());
        assertEquals("待办", TodoItem.Status.PENDING.getDescription());

        assertEquals("»", TodoItem.Status.IN_PROGRESS.getIcon());
        assertEquals("进行中", TodoItem.Status.IN_PROGRESS.getDescription());

        assertEquals("✓", TodoItem.Status.COMPLETED.getIcon());
        assertEquals("已完成", TodoItem.Status.COMPLETED.getDescription());

        assertEquals("✗", TodoItem.Status.CANCELLED.getIcon());
        assertEquals("已取消", TodoItem.Status.CANCELLED.getDescription());
    }
}
