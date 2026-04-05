package org.YanPl.manager;

import org.YanPl.FancyHelper;
import org.YanPl.api.CloudFlareAI;
import org.YanPl.model.AIResponse;

import java.io.IOException;

public class RiskAssessmentManager {
    private final FancyHelper plugin;
    private final CloudFlareAI ai;

    public RiskAssessmentManager(FancyHelper plugin) {
        this.plugin = plugin;
        this.ai = new CloudFlareAI(plugin);
    }

    /**
     * 风险评估结果
     */
    public static class RiskAssessment {
        public final int level;
        public final String reason;

        public RiskAssessment(int level, String reason) {
            this.level = level;
            this.reason = reason;
        }
    }

    /**
     * 评估操作的风险
     * @param actionType 操作类型（如 "run", "edit"）
     * @param actionContent 操作内容
     * @return 风险评估结果
     */
    public RiskAssessment assessRisk(String actionType, String actionContent) {
        String systemPrompt = "你是一个专门用于评估操作风险的AI助手。请评估Minecraft服务器操作的风险等级。";
        String userPrompt = buildRiskAssessmentPrompt(actionType, actionContent);
        
        try {
            // 使用 co-model 进行风险评估
            String response = ai.chatWithCompressionModel(systemPrompt, userPrompt);
            return parseRiskAssessment(response);
        } catch (IOException e) {
            plugin.getLogger().warning("[Smart] 风险评估失败: " + e.getMessage());
            // 回退到主模型
            try {
                AIResponse fallbackResponse = ai.chatSimple(userPrompt);
                return parseRiskAssessment(fallbackResponse.getContent());
            } catch (IOException fallbackError) {
                plugin.getLogger().warning("[Smart] 主模型风险评估也失败: " + fallbackError.getMessage());
                return new RiskAssessment(100, "评估失败，默认高风险");
            }
        }
    }

    /**
     * 构建风险评估的提示词
     */
    private String buildRiskAssessmentPrompt(String actionType, String actionContent) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个专门用于评估操作风险的AI助手。请评估以下Minecraft服务器操作的风险等级。\n\n");
        sb.append("操作类型: ").append(actionType).append("\n");
        sb.append("操作内容: ").append(actionContent).append("\n\n");
        sb.append("请按照以下格式输出评估结果：\n");
        sb.append("level: (风险值，0到100)\n");
        sb.append("reason: (可选，要求简短，非必要最多20字)\n\n");
        sb.append("风险评估标准：\n");
        sb.append("- 0-20: 完全安全，如简单的查询命令\n");
        sb.append("- 21-50: 低风险，如修改游戏模式、传送等\n");
        sb.append("- 51-80: 中等风险，如OP权限变更、重要配置修改\n");
        sb.append("- 81-100: 高风险，如停止服务器、删除世界数据等\n");
        return sb.toString();
    }

    /**
     * 解析AI返回的风险评估结果
     */
    private RiskAssessment parseRiskAssessment(String aiResponse) {
        int level = 50;
        String reason = null;

        String[] lines = aiResponse.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.toLowerCase().startsWith("level:")) {
                try {
                    String levelStr = line.substring(6).trim();
                    level = Integer.parseInt(levelStr);
                    level = Math.max(0, Math.min(100, level));
                } catch (NumberFormatException e) {
                    level = 50;
                }
            } else if (line.toLowerCase().startsWith("reason:")) {
                reason = line.substring(7).trim();
                if (reason.isEmpty()) {
                    reason = null;
                }
            }
        }

        return new RiskAssessment(level, reason);
    }
}
