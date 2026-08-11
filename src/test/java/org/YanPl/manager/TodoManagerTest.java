package org.YanPl.manager;

import org.YanPl.FancyHelper;
import org.YanPl.model.TodoItem;
import org.YanPl.util.I18n;
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
import static org.mockito.Mockito.when;

@DisplayName("TodoManager 测试")
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
        when(configManager.isDebug()).thenReturn(false);

        todoManager = new TodoManager(plugin);
        testUuid = UUID.randomUUID();
    }

    private List<TodoItem> todos(UUID uuid) {
        return todoManager.getTodos(uuid);
    }

    @Test
    @DisplayName("初始状态无任务")
    void testInitialState() {
        assertNotNull(todos(testUuid));
        assertTrue(todos(testUuid).isEmpty());
        assertFalse(todoManager.hasTodos(testUuid));
    }

    @Test
    @DisplayName("有效 JSON 应成功更新列表")
    void testUpdateValidJson() {
        String result = todoManager.updateTodos(testUuid, "[{\"id\":\"1\",\"task\":\"测试任务\"}]");

        assertFalse(result.startsWith("错误"));
        assertEquals(1, todos(testUuid).size());
        assertEquals("测试任务", todos(testUuid).get(0).getTask());
        assertTrue(todoManager.hasTodos(testUuid));
    }

    @Test
    @DisplayName("应解析 status/description/priority 字段")
    void testParseOptionalFields() {
        todoManager.updateTodos(testUuid,
            "[{\"id\":\"1\",\"task\":\"任务\",\"status\":\"completed\",\"description\":\"描述\",\"priority\":\"high\"}]");

        TodoItem item = todos(testUuid).get(0);
        assertEquals(TodoItem.Status.COMPLETED, item.getStatus());
        assertEquals("描述", item.getDescription());
        assertEquals("high", item.getPriority());
    }

    @Test
    @DisplayName("非数组输入应返回错误且不改动列表")
    void testNotArray() {
        assertTrue(todoManager.updateTodos(testUuid, "{\"id\":\"1\",\"task\":\"任务\"}").startsWith("错误"));
        assertTrue(todos(testUuid).isEmpty());
    }

    @Test
    @DisplayName("native 模式的 todos 包裹对象应成功解析")
    void testWrappedTodosObject() {
        String result = todoManager.updateTodos(testUuid,
            "{\"todos\":[{\"id\":\"1\",\"task\":\"包裹任务\"}]}");

        assertFalse(result.startsWith("错误"), result);
        assertEquals(1, todos(testUuid).size());
        assertEquals("包裹任务", todos(testUuid).get(0).getTask());
    }

    @Test
    @DisplayName("对象缺 todos 数组或 todos 非数组应报错")
    void testWrappedTodosMissing() {
        assertTrue(todoManager.updateTodos(testUuid, "{\"foo\":[{\"id\":\"1\",\"task\":\"x\"}]}").startsWith("错误"));
        assertTrue(todoManager.updateTodos(testUuid, "{\"todos\":\"notarray\"}").startsWith("错误"));
        assertTrue(todoManager.updateTodos(testUuid, "{\"todos\":{\"id\":\"1\"}}").startsWith("错误"));
        assertTrue(todos(testUuid).isEmpty());
    }

    @Test
    @DisplayName("null/空/无效 JSON 应返回错误")
    void testInvalidJsonInputs() {
        assertTrue(todoManager.updateTodos(testUuid, null).startsWith("错误"));
        assertTrue(todoManager.updateTodos(testUuid, "").startsWith("错误"));
        assertTrue(todoManager.updateTodos(testUuid, "not valid json").startsWith("错误"));
    }

    @Test
    @DisplayName("缺少 id 或 task 字段应失败")
    void testMissingRequiredFields() {
        assertTrue(todoManager.updateTodos(testUuid, "[{\"task\":\"任务\"}]").startsWith("错误"));
        assertTrue(todoManager.updateTodos(testUuid, "[{\"id\":\"1\"}]").startsWith("错误"));
    }

    @Test
    @DisplayName("id 或 task 为空应失败")
    void testEmptyRequiredFields() {
        assertTrue(todoManager.updateTodos(testUuid, "[{\"id\":\"\",\"task\":\"任务\"}]").startsWith("错误"));
        assertTrue(todoManager.updateTodos(testUuid, "[{\"id\":\"1\",\"task\":\"\"}]").startsWith("错误"));
    }

    @Test
    @DisplayName("数组项不是对象应失败")
    void testArrayItemNotObject() {
        assertTrue(todoManager.updateTodos(testUuid, "[\"string\", 123]").startsWith("错误"));
    }

    @Test
    @DisplayName("多个 in_progress 应被拒绝")
    void testMultipleInProgressRejected() {
        String json = "[{\"id\":\"1\",\"task\":\"任务1\",\"status\":\"in_progress\"},"
                    + "{\"id\":\"2\",\"task\":\"任务2\",\"status\":\"in_progress\"}]";

        assertTrue(todoManager.updateTodos(testUuid, json).startsWith("错误"));
        assertTrue(todos(testUuid).isEmpty());
    }

    @Test
    @DisplayName("更新应完全替换旧列表")
    void testUpdateReplacesOldList() {
        todoManager.updateTodos(testUuid, "[{\"id\":\"1\",\"task\":\"旧\"}]");
        todoManager.updateTodos(testUuid, "[{\"id\":\"3\",\"task\":\"新\"}]");

        assertEquals(1, todos(testUuid).size());
        assertEquals("新", todos(testUuid).get(0).getTask());
    }

    @Test
    @DisplayName("clearTodos 应清空列表")
    void testClear() {
        todoManager.updateTodos(testUuid, "[{\"id\":\"1\",\"task\":\"任务\"}]");
        todoManager.clearTodos(testUuid);

        assertTrue(todos(testUuid).isEmpty());
        assertFalse(todoManager.hasTodos(testUuid));
    }

    @Test
    @DisplayName("getTodoSummary 应统计进度")
    void testSummary() {
        assertEquals("当前没有 TODO 任务", todoManager.getTodoSummary(testUuid));

        todoManager.updateTodos(testUuid, "[{\"id\":\"1\",\"task\":\"任务1\"},{\"id\":\"2\",\"task\":\"任务2\",\"status\":\"completed\"}]");

        String summary = todoManager.getTodoSummary(testUuid);
        assertTrue(summary.contains("Progress: 1/2"), summary);
        assertTrue(summary.contains("1 待办"), summary);
    }

    @Test
    @DisplayName("getTodoDetails 应列出任务")
    void testDetails() {
        assertTrue(todoManager.getTodoDetails(testUuid).contains("没有 TODO 任务"));

        todoManager.updateTodos(testUuid, "[{\"id\":\"1\",\"task\":\"测试任务\"}]");
        String details = todoManager.getTodoDetails(testUuid);
        // 表头已 i18n 化（zh-cn 基准表）
        assertTrue(details.contains(I18n.t("todo.details.header")), details);
        assertTrue(details.contains("测试任务"));
    }

    @Test
    @DisplayName("不同玩家的列表互相隔离")
    void testPlayerIsolation() {
        UUID uuid2 = UUID.randomUUID();

        todoManager.updateTodos(testUuid, "[{\"id\":\"1\",\"task\":\"玩家1\"}]");
        todoManager.updateTodos(uuid2, "[{\"id\":\"1\",\"task\":\"玩家2\"}]");

        assertEquals(1, todos(testUuid).size());
        assertEquals(1, todos(uuid2).size());
        assertEquals("玩家1", todos(testUuid).get(0).getTask());
        assertEquals("玩家2", todos(uuid2).get(0).getTask());
    }
}
