package org.YanPl.manager;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.YanPl.FancyHelper;
import org.YanPl.model.DialogueSession;
import org.YanPl.model.NativeToolCall;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@DisplayName("ToolRegistry 单元测试")
class ToolRegistryTest {

    private final Gson gson = new Gson();

    private FancyHelper mockPlugin() {
        FancyHelper plugin = Mockito.mock(FancyHelper.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("TestLogger"));
        return plugin;
    }

    private ConfigManager mockConfig(boolean readEnabled, boolean writeEnabled, boolean mcpEnabled) {
        ConfigManager cfg = Mockito.mock(ConfigManager.class);
        when(cfg.isPlayerToolEnabled(Mockito.any(), Mockito.eq("read"))).thenReturn(readEnabled);
        when(cfg.isPlayerToolEnabled(Mockito.any(), Mockito.eq("write"))).thenReturn(writeEnabled);
        when(cfg.isMcpClientEnabled()).thenReturn(mcpEnabled);
        return cfg;
    }

    @Test
    @DisplayName("模型黑名单：已知不支持 FC 的模型返回 false")
    void testIsNativeActiveForModel() {
        assertFalse(ToolRegistry.isNativeActiveForModel(false, "deepseek-v4"));
        assertTrue(ToolRegistry.isNativeActiveForModel(true, "deepseek-v4"));
        assertFalse(ToolRegistry.isNativeActiveForModel(true, null));
        // 黑名单命中
        assertFalse(ToolRegistry.isNativeActiveForModel(true, "@cf/meta/llama2-7b"));
        assertFalse(ToolRegistry.isNativeActiveForModel(true, "gemma-7b"));
        // 主流模型
        assertTrue(ToolRegistry.isNativeActiveForModel(true, "gpt-oss-120b"));
        assertTrue(ToolRegistry.isNativeActiveForModel(true, "deepseek-chat"));
        assertTrue(ToolRegistry.isNativeActiveForModel(true, "glm-4.6"));
    }

    @Test
    @DisplayName("buildToolsArray 按权限门控 file/MCP 工具")
    void testBuildToolsArrayGating() {
        FancyHelper plugin = mockPlugin();
        Player player = Mockito.mock(Player.class);
        DialogueSession session = new DialogueSession();

        // read+write 开启，MCP 开启
        ConfigManager full = mockConfig(true, true, true);
        when(plugin.getConfigManager()).thenReturn(full);
        JsonArray allTools = ToolRegistry.buildToolsArray(plugin, player, session);
        assertTrue(hasTool(allTools, "read"));
        assertTrue(hasTool(allTools, "write"));
        assertTrue(hasTool(allTools, "edit"));
        assertTrue(hasTool(allTools, "mcp"));
        assertTrue(hasTool(allTools, "mcp_tools"));
        assertTrue(hasTool(allTools, "run"));
        assertTrue(hasTool(allTools, "remember_global"));
        // 非 plan 模式不应有 start
        assertFalse(hasTool(allTools, "start"));

        // 全部关闭
        ConfigManager none = mockConfig(false, false, false);
        when(plugin.getConfigManager()).thenReturn(none);
        JsonArray noTools = ToolRegistry.buildToolsArray(plugin, player, session);
        assertFalse(hasTool(noTools, "read"));
        assertFalse(hasTool(noTools, "write"));
        assertFalse(hasTool(noTools, "mcp"));
        assertTrue(hasTool(noTools, "run"));
    }

    @Test
    @DisplayName("buildToolsArray plan 模式才包含 start")
    void testBuildToolsArrayPlanStart() {
        FancyHelper plugin = mockPlugin();
        Player player = Mockito.mock(Player.class);
        ConfigManager cfg = mockConfig(false, false, false);
        when(plugin.getConfigManager()).thenReturn(cfg);

        DialogueSession normal = new DialogueSession();
        assertFalse(hasTool(ToolRegistry.buildToolsArray(plugin, player, normal), "start"));

        DialogueSession plan = new DialogueSession();
        plan.setMode(DialogueSession.Mode.PLAN);
        assertTrue(hasTool(ToolRegistry.buildToolsArray(plugin, player, plan), "start"));
    }

    @Test
    @DisplayName("Responses 格式 tools 无 function 包裹层")
    void testResponsesFormatShape() {
        FancyHelper plugin = mockPlugin();
        Player player = Mockito.mock(Player.class);
        ConfigManager cfg = mockConfig(false, false, false);
        when(plugin.getConfigManager()).thenReturn(cfg);

        JsonArray chatTools = ToolRegistry.buildToolsArray(plugin, player, new DialogueSession(), false);
        JsonObject chatTool = findTool(chatTools, "run");
        assertTrue(chatTool.has("function"));
        assertFalse(chatTool.has("name"));

        JsonArray respTools = ToolRegistry.buildToolsArray(plugin, player, new DialogueSession(), true);
        JsonObject respTool = findTool(respTools, "run");
        assertTrue(respTool.has("name"));
        assertFalse(respTool.has("function"));
    }

    @Test
    @DisplayName("parameters 是 JSON Schema：最外层 type=object + properties")
    void testParametersAreJsonSchema() {
        FancyHelper plugin = mockPlugin();
        Player player = Mockito.mock(Player.class);
        ConfigManager cfg = mockConfig(false, false, false);
        when(plugin.getConfigManager()).thenReturn(cfg);

        JsonArray tools = ToolRegistry.buildToolsArray(plugin, player, new DialogueSession(), false);
        JsonObject fn = findTool(tools, "run").getAsJsonObject("function");
        JsonObject params = fn.getAsJsonObject("parameters");

        assertNotNull(params, "parameters 应存在");
        assertEquals("object", params.get("type").getAsString(), "parameters 最外层 type 必须为 object");
        assertTrue(params.has("properties"), "parameters 应包含 properties");
        assertTrue(params.getAsJsonObject("properties").has("command"), "run 工具应含 command 属性");

        // 无参工具（exit）也应有合法 object schema
        JsonObject exitParams = findTool(tools, "exit").getAsJsonObject("function").getAsJsonObject("parameters");
        assertEquals("object", exitParams.get("type").getAsString());
        assertTrue(exitParams.has("properties"));
    }

    @Test
    @DisplayName("bridgeToText run 工具")
    void testBridgeRun() {
        NativeToolCall call = new NativeToolCall("c1", "run", "{\"command\":\"give @p apple\"}");
        assertEquals("#run: give @p apple", ToolRegistry.bridgeToText(call));
    }

    @Test
    @DisplayName("bridgeToText edit 四段 join")
    void testBridgeEdit() {
        NativeToolCall call = new NativeToolCall("c1", "edit",
                "{\"path\":\"config.yml\",\"range\":\"10-10\",\"original\":\"enabled: true\",\"replacement\":\"enabled: false\"}");
        assertEquals("#edit: config.yml|10-10|enabled: true|enabled: false", ToolRegistry.bridgeToText(call));
    }

    @Test
    @DisplayName("bridgeToText edit 无 range 用 auto")
    void testBridgeEditAutoRange() {
        NativeToolCall call = new NativeToolCall("c1", "edit",
                "{\"path\":\"config.yml\",\"original\":\"a\",\"replacement\":\"b\"}");
        assertEquals("#edit: config.yml|auto|a|b", ToolRegistry.bridgeToText(call));
    }

    @Test
    @DisplayName("bridgeToText write 换行转义")
    void testBridgeWrite() {
        NativeToolCall call = new NativeToolCall("c1", "write",
                "{\"path\":\"config.yml\",\"content\":\"enabled: true\\nsetting: value\"}");
        assertEquals("#write: config.yml|enabled: true\nsetting: value", ToolRegistry.bridgeToText(call));
    }

    @Test
    @DisplayName("bridgeToText skill 子命令")
    void testBridgeSkill() {
        NativeToolCall load = new NativeToolCall("c1", "skill", "{\"id\":\"essentials\",\"action\":\"load\"}");
        assertEquals("#skill: essentials", ToolRegistry.bridgeToText(load));

        NativeToolCall list = new NativeToolCall("c2", "skill", "{\"id\":\"essentials\",\"action\":\"list\"}");
        assertEquals("#skill: essentials list", ToolRegistry.bridgeToText(list));

        NativeToolCall read = new NativeToolCall("c3", "skill", "{\"id\":\"essentials\",\"action\":\"read\",\"file\":\"setup.md\"}");
        assertEquals("#skill: essentials read setup.md", ToolRegistry.bridgeToText(read));
    }

    @Test
    @DisplayName("bridgeToText mcp 拼接 server.tool|json")
    void testBridgeMcp() {
        NativeToolCall call = new NativeToolCall("c1", "mcp",
                "{\"server\":\"mysql\",\"tool\":\"query\",\"arguments\":{\"sql\":\"SELECT 1\"}}");
        assertEquals("#mcp: mysql.query|{\"sql\":\"SELECT 1\"}", ToolRegistry.bridgeToText(call));
    }

    @Test
    @DisplayName("bridgeToText ask/todo JSON 原样透传")
    void testBridgeJsonPassthrough() {
        NativeToolCall ask = new NativeToolCall("c1", "ask",
                "{\"question\":\"选择?\",\"options\":[{\"label\":\"A\"}]}");
        assertEquals("#ask: {\"question\":\"选择?\",\"options\":[{\"label\":\"A\"}]}", ToolRegistry.bridgeToText(ask));

        NativeToolCall todo = new NativeToolCall("c2", "todo", "[{\"id\":\"1\",\"task\":\"t\"}]");
        assertEquals("#todo: [{\"id\":\"1\",\"task\":\"t\"}]", ToolRegistry.bridgeToText(todo));
    }

    @Test
    @DisplayName("bridgeToText edit_memory 序号|分类|内容")
    void testBridgeEditMemory() {
        NativeToolCall call = new NativeToolCall("c1", "edit_memory",
                "{\"index\":2,\"category\":\"style\",\"content\":\"concise\"}");
        assertEquals("#edit_memory: 2|style|concise", ToolRegistry.bridgeToText(call));
    }

    @Test
    @DisplayName("bridgeToText remember 分类可选")
    void testBridgeRemember() {
        NativeToolCall noCat = new NativeToolCall("c1", "remember", "{\"content\":\"keep it short\"}");
        assertEquals("#remember: keep it short", ToolRegistry.bridgeToText(noCat));

        NativeToolCall withCat = new NativeToolCall("c2", "remember", "{\"content\":\"short\",\"category\":\"style\"}");
        assertEquals("#remember: style|short", ToolRegistry.bridgeToText(withCat));
    }

    @Test
    @DisplayName("renderForHistory 渲染 assistant 历史")
    void testRenderForHistory() {
        List<NativeToolCall> calls = List.of(
                new NativeToolCall("c1", "search", "{\"query\":\"minecraft wiki\"}"),
                new NativeToolCall("c2", "run", "{\"command\":\"say hi\"}")
        );
        String rendered = ToolRegistry.renderForHistory("先查一下", calls);
        assertEquals("先查一下\n#search: minecraft wiki\n#run: say hi", rendered);

        String empty = ToolRegistry.renderForHistory("", calls);
        assertEquals("#search: minecraft wiki\n#run: say hi", empty);

        String noCalls = ToolRegistry.renderForHistory("普通文本", List.of());
        assertEquals("普通文本", noCalls);
    }

    @Test
    @DisplayName("无效 arguments JSON 保留原始片段")
    void testBridgeInvalidJson() {
        NativeToolCall call = new NativeToolCall("c1", "run", "{\"command\" 不完整");
        // 解析失败时 fallback 到 raw
        assertTrue(ToolRegistry.bridgeToText(call).contains("#run:"));
    }

    private boolean hasTool(JsonArray tools, String name) {
        for (int i = 0; i < tools.size(); i++) {
            JsonObject tool = tools.get(i).getAsJsonObject();
            if (tool.has("function")) {
                if (name.equals(tool.getAsJsonObject("function").get("name").getAsString())) return true;
            } else if (tool.has("name")) {
                if (name.equals(tool.get("name").getAsString())) return true;
            }
        }
        return false;
    }

    private JsonObject findTool(JsonArray tools, String name) {
        for (int i = 0; i < tools.size(); i++) {
            JsonObject tool = tools.get(i).getAsJsonObject();
            if (tool.has("function") && name.equals(tool.getAsJsonObject("function").get("name").getAsString())) {
                return tool;
            }
            if (tool.has("name") && name.equals(tool.get("name").getAsString())) {
                return tool;
            }
        }
        fail("tool not found: " + name);
        return null;
    }
}
