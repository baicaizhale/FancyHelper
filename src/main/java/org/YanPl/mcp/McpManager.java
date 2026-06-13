package org.YanPl.mcp;

import com.google.gson.JsonObject;
import org.YanPl.FancyHelper;
import org.YanPl.mcp.client.McpClientManager;
import org.YanPl.mcp.client.McpClientConfig;
import org.YanPl.mcp.core.McpTypes;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * MCP 总管理器，负责初始化和生命周期管理
 */
public class McpManager {

    private final FancyHelper plugin;
    private final Logger logger;
    private McpClientManager clientManager;
    private boolean enabled;

    public McpManager(FancyHelper plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    /**
     * 根据配置初始化 MCP Client
     */
    public void initialize() {
        this.enabled = plugin.getConfigManager().isMcpClientEnabled();
        if (!enabled) {
            logger.info("[MCP] MCP Client 未启用");
            return;
        }

        List<McpClientConfig> serverConfigs = loadServerConfigs();
        if (serverConfigs.isEmpty()) {
            logger.info("[MCP] 没有配置 MCP 服务器");
            return;
        }

        clientManager = new McpClientManager(serverConfigs, logger);
        clientManager.setEnabled(enabled);
        clientManager.setToolStateFile(new File(plugin.getDataFolder(), "mcp_tools.json"));
        clientManager.loadToolStates();
        clientManager.connectAll();

        logger.info("[MCP] MCP Client 初始化完成，已连接 " + clientManager.getClients().size() + " 个服务器");
    }

    @SuppressWarnings("unchecked")
    private List<McpClientConfig> loadServerConfigs() {
        List<McpClientConfig> configs = new ArrayList<>();
        List<Map<?, ?>> serversRaw = plugin.getConfigManager().getMcpClientServers();
        if (serversRaw == null) return configs;

        for (Map<?, ?> raw : serversRaw) {
            McpClientConfig cfg = new McpClientConfig();
            Object nameObj = raw.get("name");
            Object urlObj = raw.get("url");
            Object apiKeyObj = raw.get("api_key");
            cfg.setName(nameObj != null ? nameObj.toString() : "");
            cfg.setUrl(urlObj != null ? urlObj.toString() : "");
            cfg.setApiKey(apiKeyObj != null ? apiKeyObj.toString() : "");
            if (raw.containsKey("enabled")) {
                Object e = raw.get("enabled");
                cfg.setEnabled(e != null && Boolean.parseBoolean(e.toString()));
            }
            if (raw.containsKey("transport")) {
                Object t = raw.get("transport");
                cfg.setTransport(t != null ? t.toString() : "auto");
            }
            if (raw.containsKey("call_timeout")) {
                Object ct = raw.get("call_timeout");
                try { cfg.setCallTimeout(ct != null ? Integer.parseInt(ct.toString()) : 30); }
                catch (NumberFormatException ignored) {}
            }

            if (!cfg.getName().isEmpty() && !cfg.getUrl().isEmpty()) {
                configs.add(cfg);
            }
        }
        return configs;
    }

    // ── 委托方法 ──

    public McpClientManager getClientManager() { return clientManager; }
    public boolean isEnabled() { return enabled && clientManager != null; }

    /** 检查工具是否启用 */
    public boolean isToolEnabled(String serverName, String toolName) {
        if (clientManager == null) return false;
        return clientManager.isToolEnabled(serverName, toolName);
    }

    /** 获取所有工具状态（用于 #mcp_tools 工具） */
    public List<McpClientManager.ExternalToolInfo> getAllToolsWithState() {
        if (clientManager == null) return List.of();
        return clientManager.getAllToolsWithState();
    }

    /** 调用外部工具 */
    public McpTypes.McpToolCallResult callExternalTool(String serverName, String toolName, JsonObject arguments) {
        if (clientManager == null)
            return McpTypes.McpToolCallResult.error("MCP Client 未启用");
        return clientManager.callExternalTool(serverName, toolName, arguments);
    }

    /**
     * 关闭所有 MCP 资源
     */
    public void shutdown() {
        if (clientManager != null) {
            clientManager.disconnectAll();
        }
        logger.info("[MCP] MCP Manager 已关闭");
    }
}
