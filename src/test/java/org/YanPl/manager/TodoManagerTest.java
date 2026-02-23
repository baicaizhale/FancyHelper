package org.YanPl.manager;

import org.YanPl.FancyHelper;
import org.YanPl.model.TodoItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("TodoManager 集成测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TodoManagerTest {

    @Mock
    private FancyHelper plugin;

    @Mock
    private ConfigManager configManager;

    private TodoManager todoManager;
    private UUID testUuid;

    @BeforeEach
    void setUp() {
        when(plugin.getLogger()).thenReturn(Logger.getLogger("TestLogger"));
        when(plugin.getConfigManager()).thenReturn(configManager);
        when(configManager.getTavilyMaxResults()).thenReturn(5);
        
        todoManager = new TodoManager(plugin);
        testUuid = UUID.randomUUID();
    }

    @Test
    @DisplayName("getTodos 空列表返回空集合")
    void testGetTodosEmpty() {
        List<TodoItem> todos = todoManager.getTodos(testUuid);
        assertNotNull(todos);
        assertTrue(todos.isEmpty());
    }

    @Test
    @DisplayName("updateTodos 有效 JSON 应该成功")
    void testUpdateTodosValidJson() {
        String json = "[{\"id\":\"1\",\"task\":\"测试任务\"}]";
        
        String result = todoManager.updateTodos(testUuid, json);
        
        assertEquals("TODO 列表已更新", result);
        assertEquals(1, todoManager.getTodos(testUuid).size());
    }

    @Test
    @DisplayName("updateTodos 带状态和描述")
    void testUpdateTodosWithStatusAndDescription() {
        String json = "[{\"id\":\"1\",\"task\":\"任务\",\"status\":\"completed\",\"description\":\"描述\"}]";
        
        String result = todoManager.updateTodos(testUuid, json);
        
        assertEquals("TODO 列表已更新", result);
        List<TodoItem> todos = todoManager.getTodos(testUuid);
        assertEquals(1, todos.size());
        assertEquals(TodoItem.Status.COMPLETED, todos.get(0).getStatus());
        assertEquals("描述", todos.get(0).getDescription());
    }

    @Test
    @DisplayName("updateTodos 多个任务")
    void testUpdateTodosMultipleItems() {
        String json = "[{\"id\":\"1\",\"task\":\"任务1\"},{\"id\":\"2\",\"task\":\"任务2\"}]";
        
        String result = todoManager.updateTodos(testUuid, json);
        
        assertEquals("TODO 列表已更新", result);
        assertEquals(2, todoManager.getTodos(testUuid).size());
    }

    @Test
    @DisplayName("updateTodos 不是一个数组应该失败")
    void testUpdateTodosNotArray() {
        String json = "{\"id\":\"1\",\"task\":\"任务\"}";
        
        String result = todoManager.updateTodos(testUuid, json);
        
        assertTrue(result.startsWith("错误:"));
    }

    @Test
    @DisplayName("updateTodos 空 JSON 应该失败")
    void testUpdateTodosEmptyJson() {
        String result = todoManager.updateTodos(testUuid, "");
        
        assertTrue(result.startsWith("错误:") || result.contains("格式"));
    }

    @Test
    @DisplayName("updateTodos null 应该失败")
    void testUpdateTodosNull() {
        String result = todoManager.updateTodos(testUuid, null);
        
        assertTrue(result.startsWith("错误:") || result.contains("格式"));
    }

    @Test
    @DisplayName("updateTodos 缺少 id 字段应该失败")
    void testUpdateTodosMissingId() {
        String json = "[{\"task\":\"任务\"}]";
        
        String result = todoManager.updateTodos(testUuid, json);
        
        assertTrue(result.startsWith("错误:"));
        assertTrue(result.contains("id"));
    }

    @Test
    @DisplayName("updateTodos 缺少 task 字段应该失败")
    void testUpdateTodosMissingTask() {
        String json = "[{\"id\":\"1\"}]";
        
        String result = todoManager.updateTodos(testUuid, json);
        
        assertTrue(result.startsWith("错误:"));
        assertTrue(result.contains("task"));
    }

    @Test
    @DisplayName("updateTodos id 为空应该失败")
    void testUpdateTodosEmptyId() {
        String json = "[{\"id\":\"\",\"task\":\"任务\"}]";
        
        String result = todoManager.updateTodos(testUuid, json);
        
        assertTrue(result.startsWith("错误:"));
    }

    @Test
    @DisplayName("updateTodos task 为空应该失败")
    void testUpdateTodosEmptyTask() {
        String json = "[{\"id\":\"1\",\"task\":\"\"}]";
        
        String result = todoManager.updateTodos(testUuid, json);
        
        assertTrue(result.startsWith("错误:"));
    }

    @Test
    @DisplayName("updateTodos 多个 in_progress 应该失败")
    void testUpdateTodosMultipleInProgress() {
        String json = "[{\"id\":\"1\",\"task\":\"任务1\",\"status\":\"in_progress\"},{\"id\":\"2\",\"task\":\"任务2\",\"status\":\"in_progress\"}]";
        
        String result = todoManager.updateTodos(testUuid, json);
        
        assertTrue(result.startsWith("错误:"));
        assertTrue(result.contains("in_progress"));
    }

    @Test
    @DisplayName("updateTodos 无效 JSON 应该失败")
    void testUpdateTodosInvalidJson() {
        String json = "not valid json";
        
        String result = todoManager.updateTodos(testUuid, json);
        
        assertTrue(result.startsWith("错误:"));
    }

    @Test
    @DisplayName("updateTodos 数组项不是对象应该失败")
    void testUpdateTodosArrayItemNotObject() {
        String json = "[\"string\", 123]";
        
        String result = todoManager.updateTodos(testUuid, json);
        
        assertTrue(result.startsWith("错误:"));
    }

    @Test
    @DisplayName("updateTodos 带 priority 字段")
    void testUpdateTodosWithPriority() {
        String json = "[{\"id\":\"1\",\"task\":\"任务\",\"priority\":\"high\"}]";
        
        String result = todoManager.updateTodos(testUuid, json);
        
        assertEquals("TODO 列表已更新", result);
        assertEquals("high", todoManager.getTodos(testUuid).get(0).getPriority());
    }

    @Test
    @DisplayName("clearTodos 应该清空列表")
    void testClearTodos() {
        todoManager.updateTodos(testUuid, "[{\"id\":\"1\",\"task\":\"任务\"}]");
        assertEquals(1, todoManager.getTodos(testUuid).size());
        
        todoManager.clearTodos(testUuid);
        
        assertTrue(todoManager.getTodos(testUuid).isEmpty());
    }

    @Test
    @DisplayName("hasTodos 应该正确返回")
    void testHasTodos() {
        assertFalse(todoManager.hasTodos(testUuid));
        
        todoManager.updateTodos(testUuid, "[{\"id\":\"1\",\"task\":\"任务\"}]");
        
        assertTrue(todoManager.hasTodos(testUuid));
    }

    @Test
    @DisplayName("getTodoSummary 空列表")
    void testGetTodoSummaryEmpty() {
        String summary = todoManager.getTodoSummary(testUuid);
        
        assertEquals("当前没有 TODO 任务", summary);
    }

    @Test
    @DisplayName("getTodoSummary 有任务")
    void testGetTodoSummaryWithTasks() {
        todoManager.updateTodos(testUuid, "[{\"id\":\"1\",\"task\":\"任务1\"},{\"id\":\"2\",\"task\":\"任务2\",\"status\":\"completed\"}]");
        
        String summary = todoManager.getTodoSummary(testUuid);
        
        assertTrue(summary.contains("Progress:"));
        assertTrue(summary.contains("1/2"));
    }

    @Test
    @DisplayName("getTodoSummary 有进行中任务")
    void testGetTodoSummaryWithInProgress() {
        todoManager.updateTodos(testUuid, "[{\"id\":\"1\",\"task\":\"任务\",\"status\":\"in_progress\"}]");
        
        String summary = todoManager.getTodoSummary(testUuid);
        
        assertTrue(summary.contains("进行中"));
    }

    @Test
    @DisplayName("getTodoDetails 空列表")
    void testGetTodoDetailsEmpty() {
        String details = todoManager.getTodoDetails(testUuid);
        
        assertTrue(details.contains("没有 TODO 任务"));
    }

    @Test
    @DisplayName("getTodoDetails 有任务")
    void testGetTodoDetailsWithTasks() {
        todoManager.updateTodos(testUuid, "[{\"id\":\"1\",\"task\":\"测试任务\"}]");
        
        String details = todoManager.getTodoDetails(testUuid);
        
        assertTrue(details.contains("TODO LIST"));
        assertTrue(details.contains("测试任务"));
    }

    @Test
    @DisplayName("不同玩家 TODO 列表应该隔离")
    void testDifferentPlayersSeparateLists() {
        UUID uuid2 = UUID.randomUUID();
        
        todoManager.updateTodos(testUuid, "[{\"id\":\"1\",\"task\":\"玩家1任务\"}]");
        todoManager.updateTodos(uuid2, "[{\"id\":\"1\",\"task\":\"玩家2任务\"},{\"id\":\"2\",\"task\":\"另一个任务\"}]");
        
        assertEquals(1, todoManager.getTodos(testUuid).size());
        assertEquals(2, todoManager.getTodos(uuid2).size());
        assertEquals("玩家1任务", todoManager.getTodos(testUuid).get(0).getTask());
    }

    @Test
    @DisplayName("更新 TODO 应该完全替换旧列表")
    void testUpdateReplacesOldList() {
        todoManager.updateTodos(testUuid, "[{\"id\":\"1\",\"task\":\"旧任务1\"},{\"id\":\"2\",\"task\":\"旧任务2\"}]");
        assertEquals(2, todoManager.getTodos(testUuid).size());
        
        todoManager.updateTodos(testUuid, "[{\"id\":\"3\",\"task\":\"新任务\"}]");
        
        assertEquals(1, todoManager.getTodos(testUuid).size());
        assertEquals("新任务", todoManager.getTodos(testUuid).get(0).getTask());
    }
}
