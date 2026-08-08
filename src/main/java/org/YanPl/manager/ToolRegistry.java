package org.YanPl.manager;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.YanPl.FancyHelper;
import org.YanPl.model.DialogueSession;
import org.YanPl.model.NativeToolCall;

import java.util.List;

/**
 * 原生函数调用（Native Function Calling）的工具注册表。
 *
 * 职责：
 * 1. 按功能门控组装发往 LLM API 的 `tools` 数组（OpenAI function-calling 格式 / Responses 格式）。
 * 2. 把模型返回的结构化 tool_call 桥接回现有 `#tool: args` 文本格式，复用 ToolExecutor 的全部 handler。
 * 3. 判断某模型是否启用原生 FC。
 *
 * 设计约束（见实现计划）：默认开关关闭时本类完全不参与请求；开启后仅当模型支持原生 FC 才发送 tools。
 */
public final class ToolRegistry {

    private static final Gson GSON = new Gson();

    /**
     * 已知不支持原生函数调用的模型（黑名单，模型名子串匹配）。
     * 默认仅少数已知旧模型；大多数模型（Claude/GPT-4o+/DeepSeek-V3.1+/GLM-4.5+/Qwen2.5+/gpt-oss 等）都支持。
     * 即使误配，运行时遇到 400/422 会自动去掉 tools 重试并降级到文本协议。
     */
    private static final List<String> KNOWN_NO_FC_MODELS = List.of("llama2", "vicuna", "gemma-7b", "gemma-2b");

    private ToolRegistry() {
    }

    /** 该模型是否启用原生 FC：开关开启 && 模型不在黑名单。 */
    public static boolean isNativeActiveForModel(boolean toggleEnabled, String model) {
        if (!toggleEnabled || model == null) {
            return false;
        }
        String m = model.toLowerCase();
        for (String noFc : KNOWN_NO_FC_MODELS) {
            if (m.contains(noFc)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 组装 OpenAI chat/completions 格式的 tools 数组。
     * 门控镜像 PromptManager.getBaseSystemPrompt 中的 [File Tools]/[MCP External Tools]/[Plan] 逻辑。
     */
    public static JsonArray buildToolsArray(FancyHelper plugin, org.bukkit.entity.Player player, DialogueSession session) {
        return buildToolsArray(plugin, player, session, false);
    }

    /**
     * 组装 tools 数组。
     *
     * @param responsesFormat true 时输出 Responses API 格式（扁平 name/description/parameters，无 function 包裹层）
     */
    public static JsonArray buildToolsArray(FancyHelper plugin, org.bukkit.entity.Player player, DialogueSession session, boolean responsesFormat) {
        JsonArray tools = new JsonArray();
        ConfigManager cfg = plugin.getConfigManager();
        boolean readEnabled = cfg.isPlayerToolEnabled(player, "read");
        boolean writeEnabled = cfg.isPlayerToolEnabled(player, "write");
        boolean mcpEnabled = cfg.isMcpClientEnabled();
        boolean planMode = session != null && session.getMode() == DialogueSession.Mode.PLAN;

        addTool(tools, "search", responsesFormat, "全网搜索（Wiki 优先）。查询里加 widely 强制全网搜索。",
                obj("query", str("string", "搜索关键词")));
        addTool(tools, "skill", responsesFormat, "加载/管理 Skill 知识模块。先看 Available Skills 列表再调用。",
                obj("id", str("string", "Skill ID"),
                        "action", str("string", "子命令：load 加载 / list 查看文件列表 / read 读取文件，默认 load"),
                        "file", str("string", "read 子命令时读取的文件名")));
        addTool(tools, "unloadskill", responsesFormat, "卸载已加载的 Skill 以释放上下文空间。",
                obj("id", str("string", "Skill ID")));
        addTool(tools, "ask", responsesFormat, "向玩家展示选项让其选择。一次只问一个问题。参数为 JSON：question(必填)、header(≤12字符)、options(2-4 项，每项 label+description)、otherLabel(可选自由输入)。",
                obj("question", str("string", "问题内容，必填"),
                        "header", str("string", "标题，≤12 字符"),
                        "options", arr("选项数组，2-4 项，每项含 label 与 description", "object"),
                        "otherLabel", str("string", "可选自由输入标签")));
        addTool(tools, "webfetch", responsesFormat, "抓取并解析网页内容。",
                obj("url", str("string", "要抓取的 URL")));

        addTool(tools, "run", responsesFormat, "执行一条 Minecraft 游戏内命令。禁止用 && 或 ; 拼接多条命令。参数中不要包含 | 字符。",
                obj("command", str("string", "要执行的单条命令")));
        addTool(tools, "exit", responsesFormat, "玩家要求退出 FancyHelper 时调用。", new JsonObject());
        if (planMode) {
            addTool(tools, "start", responsesFormat, "结束 Plan Mode，选择执行模式开始执行。", new JsonObject());
        }

        if (readEnabled) {
            addTool(tools, "list", responsesFormat, "列出目录内容。结果玩家不可见。",
                    obj("path", str("string", "目录路径")));
            addTool(tools, "read", responsesFormat, "读取文件（带行号）。行号用于后续 #edit 精确定位。结果玩家不可见。",
                    obj("path", str("string", "文件路径"),
                            "range", str("string", "行范围，如 1-50；省略则读全文")));
        }
        if (writeEnabled) {
            addTool(tools, "edit", responsesFormat, "按原文匹配编辑文件。先 #read 再 #edit。格式 path|range|original|replacement。参数中不要包含 | 字符。",
                    obj("path", str("string", "文件路径"),
                            "range", str("string", "行范围 10-10 或 auto"),
                            "original", str("string", "要匹配的原文"),
                            "replacement", str("string", "替换后的内容")));
            addTool(tools, "write", responsesFormat, "整体覆盖写入文件。换行用 \\n，字面 \\n 用 \\\\n。参数中不要包含 | 字符。",
                    obj("path", str("string", "文件路径"),
                            "content", str("string", "文件新内容，换行用 \\n")));
        }

        addTool(tools, "remember", responsesFormat, "保存玩家永久偏好（≤50 字，不要用 我/你/请）。分类|内容。参数中不要包含 | 字符。",
                obj("content", str("string", "记忆内容"),
                        "category", str("string", "分类，默认 general")));
        addTool(tools, "forget", responsesFormat, "删除一条或全部玩家记忆。",
                obj("index", str("string", "序号数字或 all")));
        addTool(tools, "edit_memory", responsesFormat, "更新一条玩家记忆。序号|分类|新内容。参数中不要包含 | 字符。",
                obj("index", str("number", "记忆序号"),
                        "content", str("string", "新内容"),
                        "category", str("string", "分类，默认 general")));

        addTool(tools, "remember_global", responsesFormat, "保存服务器级规则/事实（仅管理员，影响所有玩家，≤100 字）。参数中不要包含 | 字符。",
                obj("content", str("string", "记忆内容"),
                        "category", str("string", "分类，默认 rule")));
        addTool(tools, "forget_global", responsesFormat, "删除一条或全部服务器记忆（仅管理员）。",
                obj("index", str("string", "序号数字或 all")));
        addTool(tools, "edit_global", responsesFormat, "更新一条服务器记忆（仅管理员）。序号|分类|新内容。参数中不要包含 | 字符。",
                obj("index", str("number", "记忆序号"),
                        "content", str("string", "新内容"),
                        "category", str("string", "分类，默认 rule")));

        addTool(tools, "todo", responsesFormat, "创建/更新任务列表（整体替换）。todos 为数组，每项含 id(必填)、task(必填)、status、description、priority。同一时间只有一个 in_progress。",
                obj("todos", arr("任务数组", "object")));

        if (mcpEnabled) {
            addTool(tools, "mcp_tools", responsesFormat, "列出所有 MCP 外部工具及其启用状态。", new JsonObject());
            addTool(tools, "mcp", responsesFormat, "调用外部 MCP 工具。格式 server.tool|jsonArgs。先调用 mcp_tools 查看可用工具。",
                    obj("server", str("string", "MCP 服务器名"),
                            "tool", str("string", "MCP 工具名"),
                            "arguments", obj("type", str("string", "object"),
                                    "description", str("string", "工具参数 JSON 对象"))));
        }
        return tools;
    }

    private static void addTool(JsonArray tools, String name, boolean responsesFormat,
                                String description, JsonObject parameters) {
        JsonObject fn = new JsonObject();
        fn.addProperty("name", name);
        fn.addProperty("description", description);
        // OpenAI function-calling 要求 parameters 是 JSON Schema，最外层必须为 object 类型。
        // 调用处传入的 parameters 是 properties 内容，这里统一包一层 {"type":"object","properties":{...}}。
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", parameters == null ? new JsonObject() : parameters);
        fn.add("parameters", schema);
        JsonObject tool = new JsonObject();
        tool.addProperty("type", "function");
        if (responsesFormat) {
            // Responses API：function 字段扁平展开
            for (String key : fn.keySet()) {
                tool.add(key, fn.get(key));
            }
        } else {
            tool.add("function", fn);
        }
        tools.add(tool);
    }

    private static JsonObject obj(String k1, JsonElement v1, String k2, JsonElement v2, String k3, JsonElement v3) {
        JsonObject o = new JsonObject();
        o.add(k1, v1);
        o.add(k2, v2);
        o.add(k3, v3);
        return o;
    }

    private static JsonObject obj(String k1, JsonElement v1, String k2, JsonElement v2, String k3, JsonElement v3,
                                  String k4, JsonElement v4) {
        JsonObject o = new JsonObject();
        o.add(k1, v1);
        o.add(k2, v2);
        o.add(k3, v3);
        o.add(k4, v4);
        return o;
    }

    private static JsonObject obj(String k1, JsonElement v1, String k2, JsonElement v2) {
        JsonObject o = new JsonObject();
        o.add(k1, v1);
        o.add(k2, v2);
        return o;
    }

    private static JsonObject obj(String k1, JsonElement v1) {
        JsonObject o = new JsonObject();
        o.add(k1, v1);
        return o;
    }

    private static JsonElement str(String type, String description) {
        JsonObject o = new JsonObject();
        o.addProperty("type", type);
        o.addProperty("description", description);
        return o;
    }

    /** 数组类型 schema：{"type":"array","description":"...","items":{"type":"..."}} */
    private static JsonElement arr(String description, String itemsType) {
        JsonObject o = new JsonObject();
        o.addProperty("type", "array");
        o.addProperty("description", description);
        JsonObject items = new JsonObject();
        items.addProperty("type", itemsType);
        o.add("items", items);
        return o;
    }

    /**
     * 把结构化的原生 tool_call 桥接回现有 `#tool: args` 文本，复用 ToolExecutor 的 handler。
     * 参数中可能包含 | 字符时已在描述里提示模型避免，与文本协议同限制。
     */
    public static String bridgeToText(NativeToolCall call) {
        String name = call.name() == null ? "" : call.name().toLowerCase();
        JsonObject args = parseArgsObject(call.argumentsJson());
        String raw = call.argumentsJson() == null ? "" : call.argumentsJson().trim();

        switch (name) {
            case "run":
                return "#run: " + str(args, "command", raw);
            case "search":
                return "#search: " + str(args, "query", raw);
            case "webfetch":
                return "#webfetch: " + str(args, "url", raw);
            case "list":
                return "#list: " + str(args, "path", raw);
            case "unloadskill":
                return "#unloadskill: " + str(args, "id", raw);
            case "read": {
                String path = str(args, "path", "");
                String range = str(args, "range", "");
                return range.isEmpty() ? "#read: " + path : "#read: " + path + " " + range;
            }
            case "skill": {
                String id = str(args, "id", "");
                String action = str(args, "action", "load");
                String file = str(args, "file", "");
                if ("list".equalsIgnoreCase(action)) {
                    return "#skill: " + id + " list";
                }
                if ("read".equalsIgnoreCase(action) && !file.isEmpty()) {
                    return "#skill: " + id + " read " + file;
                }
                return "#skill: " + id;
            }
            case "write": {
                String path = str(args, "path", "");
                String content = str(args, "content", "");
                // AI 用 \n 表示换行，\\n 表示字面 \n（与 executeFileOperation 的转义约定一致）
                content = content.replace("\\n", "\n");
                return "#write: " + path + "|" + content;
            }
            case "edit": {
                String path = str(args, "path", "");
                String range = str(args, "range", "");
                String original = str(args, "original", "");
                String replacement = str(args, "replacement", "");
                if (range.isEmpty()) {
                    range = "auto";
                }
                return "#edit: " + path + "|" + range + "|" + original + "|" + replacement;
            }
            case "ask":
            case "todo":
                // JSON 原样透传（handler 自己解析 JSON）
                return "#" + name + ": " + (raw.isEmpty() ? "{}" : raw);
            case "mcp": {
                String server = str(args, "server", "");
                String tool = str(args, "tool", "");
                JsonElement arguments = args.get("arguments");
                String json = arguments == null ? "{}" : GSON.toJson(arguments);
                return "#mcp: " + server + "." + tool + "|" + json;
            }
            case "remember": {
                String category = str(args, "category", "");
                String content = str(args, "content", "");
                if (category.isEmpty()) {
                    return "#remember: " + content;
                }
                return "#remember: " + category + "|" + content;
            }
            case "edit_memory": {
                String index = str(args, "index", "");
                String category = str(args, "category", "");
                String content = str(args, "content", "");
                return "#edit_memory: " + index + "|" + category + "|" + content;
            }
            case "remember_global": {
                String category = str(args, "category", "");
                String content = str(args, "content", "");
                if (category.isEmpty()) {
                    return "#remember_global: " + content;
                }
                return "#remember_global: " + category + "|" + content;
            }
            case "forget":
                return "#forget: " + str(args, "index", raw);
            case "forget_global":
                return "#forget_global: " + str(args, "index", raw);
            case "edit_global": {
                String index = str(args, "index", "");
                String category = str(args, "category", "");
                String content = str(args, "content", "");
                return "#edit_global: " + index + "|" + category + "|" + content;
            }
            case "exit":
                return "#exit";
            case "start":
                return "#start";
            case "mcp_tools":
                return "#mcp_tools";
            default:
                return raw.isEmpty() ? "#" + name : "#" + name + ": " + raw;
        }
    }

    /**
     * 把原生 tool_calls 渲染进 assistant 历史内容（混合式反馈的核心）。
     * 历史始终是 {role, content} 文本，对各类 provider 结构性有效。
     */
    public static String renderForHistory(String content, List<NativeToolCall> calls) {
        if (calls == null || calls.isEmpty()) {
            return content;
        }
        StringBuilder sb = new StringBuilder();
        if (content != null && !content.isEmpty()) {
            sb.append(content).append("\n");
        }
        for (int i = 0; i < calls.size(); i++) {
            if (i > 0) {
                sb.append("\n");
            }
            sb.append(bridgeToText(calls.get(i)));
        }
        return sb.toString();
    }

    private static JsonObject parseArgsObject(String argumentsJson) {
        if (argumentsJson == null || argumentsJson.isEmpty()) {
            return new JsonObject();
        }
        try {
            JsonElement el = JsonParser.parseString(argumentsJson);
            if (el != null && el.isJsonObject()) {
                return el.getAsJsonObject();
            }
        } catch (Exception ignored) {
        }
        return new JsonObject();
    }

    private static String str(JsonObject obj, String key, String fallback) {
        if (obj != null && obj.has(key) && !obj.get(key).isJsonNull()) {
            JsonElement el = obj.get(key);
            if (el.isJsonPrimitive()) {
                String s = el.getAsString();
                return s == null ? fallback : s;
            }
            return GSON.toJson(el);
        }
        return fallback == null ? "" : fallback;
    }
}
