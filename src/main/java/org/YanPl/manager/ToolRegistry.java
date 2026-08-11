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

        addTool(tools, "search", responsesFormat, "Web search (Wiki first). Add 'widely' to force a broad web search.",
                obj("query", str("string", "Search keyword")));
        addTool(tools, "skill", responsesFormat, "Load/manage Skill knowledge modules. Check the Available Skills list before calling.",
                obj("id", str("string", "Skill ID"),
                        "action", str("string", "Subcommand: load / list / read, default load"),
                        "file", str("string", "File name to read with the read subcommand")));
        addTool(tools, "unloadskill", responsesFormat, "Unload a loaded Skill to free up context space.",
                obj("id", str("string", "Skill ID")));
        addTool(tools, "ask", responsesFormat, "Show the player options to choose from. Ask only one question at a time. Parameters are JSON: question (required), header (<=12 chars), options (2-4 items, each with label and description), otherLabel (optional free-text input).",
                obj("question", str("string", "Question text, required"),
                        "header", str("string", "Header, <=12 chars"),
                        "options", arr("Options array, 2-4 items, each with label and description", "object"),
                        "otherLabel", str("string", "Optional free-text input label")));
        addTool(tools, "webfetch", responsesFormat, "Fetch and parse web page content.",
                obj("url", str("string", "URL to fetch")));

        // 注意：force 键刻意不进 schema。它是命令被拦截（NORMAL/SMART 风险确认）后的二次尝试逃生通道，
        // 若声明为常规参数，模型可能随意带上 force 绕过全部确认；故仅在拦截反馈文案中示例引导，见 ToolExecutor.handleBlockedCommand。
        addTool(tools, "run", responsesFormat, "Execute a single Minecraft in-game command. Never chain multiple commands with && or ;. Do not include | characters in the parameter.",
                obj("command", str("string", "The single command to execute")));
        addTool(tools, "end", responsesFormat, "Mark the task as complete. Call only after replying to the player with a summary. Never call alone.", new JsonObject());
        addTool(tools, "exit", responsesFormat, "Call when the player asks to exit FancyHelper.", new JsonObject());
        if (planMode) {
            addTool(tools, "start", responsesFormat, "End Plan Mode and choose an execution mode to start.", new JsonObject());
        }

        if (readEnabled) {
            addTool(tools, "list", responsesFormat, "List directory contents. Result is not visible to the player.",
                    obj("path", str("string", "Directory path")));
            addTool(tools, "read", responsesFormat, "Read a file (with line numbers). Line numbers are used to precisely locate #edit later. Result is not visible to the player.",
                    obj("path", str("string", "File path"),
                            "range", str("string", "Line range, e.g. 1-50; omit to read the whole file")));
        }
        if (writeEnabled) {
            addTool(tools, "edit", responsesFormat, "Edit a file by matching original text. #read first, then #edit. Parameters are sent as structured JSON, no delimiter escaping needed. Format: path + original + replacement, optional range (10-10 or auto).",
                    obj("path", str("string", "File path"),
                            "range", str("string", "Line range 10-10 or auto; omit to auto-search"),
                            "original", str("string", "Original text to match"),
                            "replacement", str("string", "Replacement text")));
            addTool(tools, "write", responsesFormat, "Overwrite a file completely. Parameters are sent as structured JSON, use \\n for newlines. For existing files, #read the file first in the same session.",
                    obj("path", str("string", "File path"),
                            "content", str("string", "New file content, use \\n for newlines")));
        }

        addTool(tools, "remember", responsesFormat, "Save a permanent player preference (<=50 chars, avoid first/second-person wording like I/you/please). Format: category|content. Do not include | characters in the parameter.",
                obj("content", str("string", "Memory content"),
                        "category", str("string", "Category, default general")));
        addTool(tools, "forget", responsesFormat, "Delete one or all player memories.",
                obj("index", str("string", "Index number or all")));
        addTool(tools, "edit_memory", responsesFormat, "Update one player memory. Format: index|category|content. Do not include | characters in the parameter.",
                obj("index", str("number", "Memory index"),
                        "content", str("string", "New content"),
                        "category", str("string", "Category, default general")));

        addTool(tools, "remember_global", responsesFormat, "Save a server-level rule/fact (admin only, affects all players, <=100 chars). Do not include | characters in the parameter.",
                obj("content", str("string", "Memory content"),
                        "category", str("string", "Category, default rule")));
        addTool(tools, "forget_global", responsesFormat, "Delete one or all server memories (admin only).",
                obj("index", str("string", "Index number or all")));
        addTool(tools, "edit_global", responsesFormat, "Update one server memory (admin only). Format: index|category|content. Do not include | characters in the parameter.",
                obj("index", str("number", "Memory index"),
                        "content", str("string", "New content"),
                        "category", str("string", "Category, default rule")));

        addTool(tools, "todo", responsesFormat, "Create/update the task list (full replacement). todos is an array; each item has id (required), task (required), status, description, priority. Only one item can be in_progress at a time.",
                obj("todos", arr("Task array", "object")));

        if (mcpEnabled) {
            addTool(tools, "mcp_tools", responsesFormat, "List all MCP external tools and their enabled status.", new JsonObject());
            addTool(tools, "mcp", responsesFormat, "Call an external MCP tool. Format: server.tool|jsonArgs. Call mcp_tools first to see available tools.",
                    obj("server", str("string", "MCP server name"),
                            "tool", str("string", "MCP tool name"),
                            "arguments", obj("type", str("string", "object"),
                                    "description", str("string", "Tool arguments JSON object"))));
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
     * #write / #edit 输出 JSON 行格式（标准 JSON 转义），其余文本协议字段用 | 分隔，已在描述里提示模型避免。
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
                // 输出 JSON 行格式（标准 JSON 转义，无 | 分隔符冲突、无 \n 三层转义约定）
                JsonObject json = new JsonObject();
                json.addProperty("path", path);
                json.addProperty("content", content);
                return "#write: " + GSON.toJson(json);
            }
            case "edit": {
                String path = str(args, "path", "");
                String range = str(args, "range", "");
                String original = str(args, "original", "");
                String replacement = str(args, "replacement", "");
                // 输出 JSON 行格式；range 空则省略（解析端默认 auto）
                JsonObject json = new JsonObject();
                json.addProperty("path", path);
                if (!range.isEmpty()) {
                    json.addProperty("range", range);
                }
                json.addProperty("original", original);
                json.addProperty("replacement", replacement);
                return "#edit: " + GSON.toJson(json);
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
            case "end":
                return "#end";
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

    /**
     * 提取原生 run 调用的 force 键：{"command": "...", "force": true}。
     * 缺省视为 false。
     *
     * 设计意图：force 是逃生通道而非常规参数——刻意不进 run 的 schema（见 addTool "run" 处的注释），
     * 避免模型将其当作普通参数滥用而绕过 NORMAL/SMART 模式的风险确认；
     * 仅在被拦截后的反馈文案中示例引导模型在二次尝试时自发带上（{"command":"...","force":true}）。
     */
    public static boolean isForceCall(NativeToolCall call) {
        if (call == null) {
            return false;
        }
        JsonObject args = parseArgsObject(call.argumentsJson());
        return args.has("force") && !args.get("force").isJsonNull()
                && args.get("force").getAsBoolean();
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
