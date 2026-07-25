package org.YanPl.model;

/**
 * 提供商配置模型
 * 将 config.yml 中 provider 段落的扁平枚举解析为类型安全的枚举值
 */
public class ProviderConfig {

    public enum AIProvider {
        FANCY,      // 走 FancyConsole 代理（开箱即用兜底）
        OPENAI,     // BYOK 直连 OpenAI 兼容 API
        CLOUDFLARE; // BYOK 直连 Cloudflare Workers AI

        public static AIProvider fromString(String s) {
            if (s == null || s.isEmpty()) return FANCY;
            switch (s.toLowerCase()) {
                case "openai":     return OPENAI;
                case "cloudflare": return CLOUDFLARE;
                default:           return FANCY;
            }
        }
    }

    public enum SearchProvider {
        FANCY_METASO,   // 走 FancyConsole 代理 → Metaso
        FANCY_TAVILY,   // 走 FancyConsole 代理 → Tavily
        METASO,         // BYOK 直连 Metaso
        TAVILY;         // BYOK 直连 Tavily

        public static SearchProvider fromString(String s) {
            if (s == null || s.isEmpty()) return FANCY_TAVILY;
            switch (s.toLowerCase()) {
                case "fancy-metaso": return FANCY_METASO;
                case "metaso":       return METASO;
                case "tavily":       return TAVILY;
                default:             return FANCY_TAVILY;
            }
        }
    }

    public enum JinaProvider {
        FANCY,  // 走 FancyConsole 代理（Worker 端持有 Jina key）
        NONE;   // 直连 URL + Jsoup 解析（现有回退逻辑）

        public static JinaProvider fromString(String s) {
            if (s == null || s.isEmpty()) return FANCY;
            switch (s.toLowerCase()) {
                case "none": return NONE;
                default:     return FANCY;
            }
        }
    }

    private final AIProvider aiProvider;
    private final SearchProvider searchProvider;
    private final JinaProvider jinaProvider;

    public ProviderConfig(AIProvider aiProvider, SearchProvider searchProvider, JinaProvider jinaProvider) {
        this.aiProvider = aiProvider;
        this.searchProvider = searchProvider;
        this.jinaProvider = jinaProvider;
    }

    public AIProvider getAiProvider() { return aiProvider; }
    public SearchProvider getSearchProvider() { return searchProvider; }
    public JinaProvider getJinaProvider() { return jinaProvider; }

    /**
     * AI 是否走 FancyConsole 代理
     */
    public boolean isAiFancy() {
        return aiProvider == AIProvider.FANCY;
    }

    /**
     * 搜索是否走 FancyConsole 代理
     */
    public boolean isSearchFancy() {
        return searchProvider == SearchProvider.FANCY_METASO
            || searchProvider == SearchProvider.FANCY_TAVILY;
    }

    /**
     * Jina 是否走 FancyConsole 代理
     */
    public boolean isJinaFancy() {
        return jinaProvider == JinaProvider.FANCY;
    }
}
