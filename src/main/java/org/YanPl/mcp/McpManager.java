package org.YanPl.mcp;

import com.google.gson.JsonObject;
import org.YanPl.FancyHelper;
import org.YanPl.mcp.client.McpClientManager;
import org.YanPl.mcp.client.McpClientConfig;
import org.YanPl.mcp.core.McpTypes;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class McpManager {

    private final FancyHelper plugin;
    private final Logger logger;
    private McpClientManager clientManager;
    private boolean enabled;
    private BukkitRunnable reconnectTask;

    public McpManager(FancyHelper plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

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

        int connectTimeout = plugin.getConfigManager().getMcpClientConnectTimeout();

        clientManager = new McpClientManager(serverConfigs, connectTimeout, logger);
        clientManager.setEnabled(enabled);
        clientManager.setToolStateFile(new File(plugin.getDataFolder(), "mcp_tools.json"));
        clientManager.loadToolStates();

        // 异步连接，不阻塞主线程（SSE 模式需要阻塞读取事件流）
        logger.info("[MCP] 正在连接 " + serverConfigs.size() + " 个 MCP 服务器...");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            clientManager.connectAll();
            logger.info("[MCP] MCP Client 初始化完成，已连接 " + clientManager.getClients().size() + " 个服务器");
        });

        startReconnectTask();
    }

    private void startReconnectTask() {
        int intervalMinutes = plugin.getConfigManager().getMcpClientReconnectInterval();
        if (intervalMinutes <= 0) {
            logger.info("[MCP] 自动重连已关闭（reconnect_interval <= 0）");
            return;
        }

        long intervalTicks = intervalMinutes * 60L * 20L;
        reconnectTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (clientManager == null || !enabled) {
                    cancel();
                    return;
                }
                List<McpClientConfig> disconnected = clientManager.getDisconnectedServers();
                if (!disconnected.isEmpty()) {
                    logger.fine("[MCP] 检测到 " + disconnected.size() + " 个服务器断线，尝试重连...");
                    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                        for (McpClientConfig cfg : disconnected) {
                            logger.fine("[MCP] 正在重连 " + cfg.getName() + "...");
                            boolean ok = clientManager.connectServer(cfg);
                            if (ok) {
                                logger.fine("[MCP] " + cfg.getName() + " 重连成功");
                            } else {
                                logger.warning("[MCP] " + cfg.getName() + " 重连失败，下次重试");
                            }
                        }
                    });
                }
            }
        };
        reconnectTask.runTaskTimer(plugin, intervalTicks, intervalTicks);
    }

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

    public McpClientManager getClientManager() { return clientManager; }
    public boolean isEnabled() { return enabled && clientManager != null; }

    public boolean isToolEnabled(String serverName, String toolName) {
        if (clientManager == null) return false;
        return clientManager.isToolEnabled(serverName, toolName);
    }

    public List<McpClientManager.ExternalToolInfo> getAllToolsWithState() {
        if (clientManager == null) return List.of();
        return clientManager.getAllToolsWithState();
    }

    public McpTypes.McpToolCallResult callExternalTool(String serverName, String toolName, JsonObject arguments) {
        if (clientManager == null)
            return McpTypes.McpToolCallResult.error("MCP Client 未启用");
        return clientManager.callExternalTool(serverName, toolName, arguments);
    }

    public void reload() {
        shutdown();
        initialize();
    }

    public void shutdown() {
        if (reconnectTask != null) {
            reconnectTask.cancel();
            reconnectTask = null;
        }
        if (clientManager != null) {
            clientManager.disconnectAll();
        }
        logger.info("[MCP] MCP Manager 已关闭");
    }
}