package org.YanPl.manager;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 初始化向导：OpenAI 兼容端点 → 模型列表地址的推导规则。
 */
class InitWizardManagerModelsUrlTest {

    @Test
    void deepseekFullUrl() {
        // 用户按向导示例粘贴完整 chat/completions 地址
        assertEquals("https://api.deepseek.com/v1/models",
                InitWizardManager.deriveModelsUrl("https://api.deepseek.com/chat/completions"));
    }

    @Test
    void openAiWithV1() {
        assertEquals("https://api.openai.com/v1/models",
                InitWizardManager.deriveModelsUrl("https://api.openai.com/v1/chat/completions"));
    }

    @Test
    void aliyunCompatibleMode() {
        assertEquals("https://dashscope.aliyuncs.com/compatible-mode/v1/models",
                InitWizardManager.deriveModelsUrl("https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"));
    }

    @Test
    void bareDomain() {
        // 只贴域名：补全 /v1/models（DeepSeek 官方两种写法都支持）
        assertEquals("https://api.deepseek.com/v1/models",
                InitWizardManager.deriveModelsUrl("https://api.deepseek.com"));
    }

    @Test
    void trailingSlashStripped() {
        assertEquals("https://api.deepseek.com/v1/models",
                InitWizardManager.deriveModelsUrl("https://api.deepseek.com/chat/completions/"));
        assertEquals("https://api.deepseek.com/v1/models",
                InitWizardManager.deriveModelsUrl("https://api.deepseek.com/v1/"));
    }

    @Test
    void localServerV1Only() {
        assertEquals("http://localhost:1234/v1/models",
                InitWizardManager.deriveModelsUrl("http://localhost:1234/v1"));
    }

    @Test
    void completionsSuffixAlsoStripped() {
        assertEquals("https://example.com/api/v1/models",
                InitWizardManager.deriveModelsUrl("https://example.com/api/v1/completions"));
    }
}
