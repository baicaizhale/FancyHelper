package org.YanPl.mcp.client;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import org.YanPl.mcp.core.McpTypes;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class McpClientManager {

    private static final Gson gson = new Gson();
    private static final Type TOOL_STATE_TYPE = new TypeToken<Map<String, Map<String, Boolean>>>() {}.getType();

    private final Logger logger;
    private final List<McpClientConfig> serverConfigs;
    private final int connectTimeoutSeconds;
    private final Map<String, McpClient> clients = new ConcurrentHashMap<>();
    private Map<String, Map<String, Boolean>> toolStates = new ConcurrentHashMap<>();
    private File toolStateFile;
    private boolean enabled = false;

    public McpClientManager(List<McpClientConfig> serverConfigs, int connectTimeoutSeconds, Logger logger) {
        this.serverConfigs = serverConfigs;
        this.connectTimeoutSeconds = connectTimeoutSeconds;
        this.logger = logger;
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Map<String, McpClient> getClients() { return Collections.unmodifiableMap(clients); }

    public void setToolStateFile(File file) {
        this.toolStateFile = file;
    }

    public void connectAll() {
        if (!enabled) return;

        for (McpClientConfig config : serverConfigs) {
            if (!config.isEnabled()) {
                logger.info("[MCP] 跳过已禁用的服务器 " + config.getName());
                continue;
            }
            if (clients.containsKey(config.getName())) {
                logger.info("[MCP] 服务器已连接，跳过 " + config.getName());
                continue;
            }

            McpClient client = new McpClient(config, connectTimeoutSeconds, logger);
            if (client.connect()) {
                clients.put(config.getName(), client);
                for (McpTypes.McpTool tool : client.getTools()) {
                    setToolEnabledIfAbsent(config.getName(), tool.name, true);
                }
            }
        }
        saveToolStates();
    }

    public boolean connectServer(McpClientConfig config) {
        if (!enabled || !config.isEnabled()) return false;
        if (clients.containsKey(config.getName())) return true;

        McpClient client = new McpClient(config, connectTimeoutSeconds, logger);
        if (client.connect()) {
            clients.put(config.getName(), client);
            for (McpTypes.McpTool tool : client.getTools()) {
                setToolEnabledIfAbsent(config.getName(), tool.name, true);
            }
            saveToolStates();
            return true;
        }
        return false;
    }

    public List<ExternalToolInfo> getAllEnabledTools() {
        List<ExternalToolInfo> result = new ArrayList<>();
        for (Map.Entry<String, McpClient> entry : clients.entrySet()) {
            String serverName = entry.getKey();
            McpClient client = entry.getValue();
            Map<String, Boolean> serverStates = toolStates.getOrDefault(serverName, Collections.emptyMap());

            for (McpTypes.McpTool tool : client.getTools()) {
                boolean toolEnabled = serverStates.getOrDefault(tool.name, true);
                if (toolEnabled) {
                    result.add(new ExternalToolInfo(serverName, tool));
                }
            }
        }
        return result;
    }

    public List<ExternalToolInfo> getAllToolsWithState() {
        List<ExternalToolInfo> result = new ArrayList<>();
        for (Map.Entry<String, McpClient> entry : clients.entrySet()) {
            String serverName = entry.getKey();
            McpClient client = entry.getValue();
            Map<String, Boolean> serverStates = toolStates.getOrDefault(serverName, Collections.emptyMap());

            for (McpTypes.McpTool tool : client.getTools()) {
                boolean toolEnabled = serverStates.getOrDefault(tool.name, true);
                result.add(new ExternalToolInfo(serverName, tool, toolEnabled, client.isConnected()));
            }
        }
        for (McpClientConfig config : serverConfigs) {
            if (!config.isEnabled()) continue;
            if (!clients.containsKey(config.getName())) {
                result.add(new ExternalToolInfo(config.getName(), null, false, false));
            }
        }
        return result;
    }

    public McpTypes.McpToolCallResult callExternalTool(String serverName, String toolName, JsonObject arguments) {
        McpClient client = clients.get(serverName);
        if (client == null) {
            return McpTypes.McpToolCallResult.error("MCP 服务器未连接: " + serverName);
        }
        if (!isToolEnabled(serverName, toolName)) {
            return McpTypes.McpToolCallResult.error("工具已被管理员禁用 " + serverName + "." + toolName);
        }
        return client.callTool(toolName, arguments);
    }

    public void disconnectAll() {
        for (McpClient client : clients.values()) {
            client.disconnect();
        }
        clients.clear();
    }

    public boolean reconnectClient(String serverName) {
        McpClient old = clients.remove(serverName);
        if (old != null) {
            old.disconnect();
        }
        for (McpClientConfig cfg : serverConfigs) {
            if (cfg.getName().equals(serverName) && cfg.isEnabled()) {
                return connectServer(cfg);
            }
        }
        return false;
    }

    public List<McpClientConfig> getDisconnectedServers() {
        List<McpClientConfig> result = new ArrayList<>();
        for (McpClientConfig cfg : serverConfigs) {
            if (cfg.isEnabled() && !clients.containsKey(cfg.getName())) {
                result.add(cfg);
            }
        }
        return result;
    }

    public boolean isToolEnabled(String serverName, String toolName) {
        Map<String, Boolean> serverStates = toolStates.get(serverName);
        if (serverStates == null) return true;
        return serverStates.getOrDefault(toolName, true);
    }

    public void setToolEnabled(String serverName, String toolName, boolean enabled) {
        toolStates.computeIfAbsent(serverName, k -> new LinkedHashMap<>()).put(toolName, enabled);
        saveToolStates();
    }

    private void setToolEnabledIfAbsent(String serverName, String toolName, boolean enabled) {
        Map<String, Boolean> serverStates = toolStates.computeIfAbsent(serverName, k -> new LinkedHashMap<>());
        if (!serverStates.containsKey(toolName)) {
            serverStates.put(toolName, enabled);
        }
    }

    public void toggleTool(String serverName, String toolName) {
        boolean current = isToolEnabled(serverName, toolName);
        setToolEnabled(serverName, toolName, !current);
    }

    public void loadToolStates() {
        if (toolStateFile == null || !toolStateFile.exists()) return;
        try {
            String json = new String(Files.readAllBytes(toolStateFile.toPath()), StandardCharsets.UTF_8);
            Map<String, Map<String, Boolean>> loaded = gson.fromJson(json, TOOL_STATE_TYPE);
            if (loaded != null) {
                toolStates = new ConcurrentHashMap<>(loaded);
            }
        } catch (Exception e) {
            logger.warning("[MCP] 加载工具状态文件失败 " + e.getMessage());
        }
    }

    public void saveToolStates() {
        if (toolStateFile == null) return;
        try {
            Files.createDirectories(toolStateFile.getParentFile().toPath());
            String json = gson.toJson(toolStates, TOOL_STATE_TYPE);
            Files.write(toolStateFile.toPath(), json.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            logger.warning("[MCP] 保存工具状态文件失败 " + e.getMessage());
        }
    }

    public McpClient findClientForTool(String fullToolName) {
        int dotIndex = fullToolName.indexOf('.');
        if (dotIndex <= 0) return null;
        String serverName = fullToolName.substring(0, dotIndex);
        return clients.get(serverName);
    }

    public static class ExternalToolInfo {
        public final String serverName;
        public final McpTypes.McpTool tool;
        public final boolean enabled;
        public final boolean serverConnected;

        ExternalToolInfo(String serverName, McpTypes.McpTool tool) {
            this(serverName, tool, true, true);
        }

        ExternalToolInfo(String serverName, McpTypes.McpTool tool, boolean enabled, boolean serverConnected) {
            this.serverName = serverName;
            this.tool = tool;
            this.enabled = enabled;
            this.serverConnected = serverConnected;
        }

        public String getFullName() {
            return tool != null ? serverName + "." + tool.name : serverName;
        }
    }
}