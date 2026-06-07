package org.YanPl.mcp.core;

import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;

import java.util.List;
import java.util.Map;

/**
 * MCP 协议类型定义
 */
public class McpTypes {

    /** MCP 工具定义 */
    public static class McpTool {
        public String name;
        public String description;
        @SerializedName("inputSchema")
        public JsonObject inputSchema;

        public McpTool() {}

        public McpTool(String name, String description, JsonObject inputSchema) {
            this.name = name;
            this.description = description;
            this.inputSchema = inputSchema;
        }
    }

    /** MCP 工具调用结果 */
    public static class McpToolCallResult {
        public List<ContentItem> content;
        @SerializedName("isError")
        public boolean isError;

        public static class ContentItem {
            public String type;
            public String text;

            public ContentItem() {}

            public ContentItem(String type, String text) {
                this.type = type;
                this.text = text;
            }
        }

        public static McpToolCallResult text(String text) {
            McpToolCallResult r = new McpToolCallResult();
            r.content = List.of(new ContentItem("text", text));
            r.isError = false;
            return r;
        }

        public static McpToolCallResult error(String text) {
            McpToolCallResult r = new McpToolCallResult();
            r.content = List.of(new ContentItem("text", text));
            r.isError = true;
            return r;
        }
    }

    /** 服务器能力声明 */
    public static class ServerCapabilities {
        public Capability tools;
        public Capability prompts;
        public Capability resources;

        public static class Capability {
            @SerializedName("listChanged")
            public boolean listChanged;

            public Capability() {}

            public Capability(boolean listChanged) {
                this.listChanged = listChanged;
            }
        }
    }

    /** 初始化结果 */
    public static class InitializeResult {
        @SerializedName("protocolVersion")
        public String protocolVersion;
        public ServerCapabilities capabilities;
        @SerializedName("serverInfo")
        public Implementation serverInfo;
        public Map<String, Object> instructions;
    }

    /** 客户端/服务器信息 */
    public static class Implementation {
        public String name;
        public String version;

        public Implementation() {}

        public Implementation(String name, String version) {
            this.name = name;
            this.version = version;
        }
    }

    /** 工具列表结果 */
    public static class ToolsListResult {
        public List<McpTool> tools;
        @SerializedName("nextCursor")
        public String nextCursor;
    }
}
