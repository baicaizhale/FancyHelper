package org.YanPl.mcp.client;

/**
 * 单个 MCP 服务器的连接配置
 */
public class McpClientConfig {
    private String name;
    private String url;
    private String apiKey = "";
    private boolean enabled = true;
    private String transport = "auto";
    private int callTimeout = 30;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getApiKey() { return apiKey != null ? apiKey : ""; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getTransport() { return transport != null ? transport : "auto"; }
    public void setTransport(String transport) { this.transport = transport; }

    public int getCallTimeout() { return callTimeout > 0 ? callTimeout : 30; }
    public void setCallTimeout(int callTimeout) { this.callTimeout = callTimeout; }
}
