package org.YanPl.model;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Skill 数据模型
 * 表示一个可复用的知识模块
 */
public class Skill {

    // Front Matter 分隔符正则
    private static final Pattern FRONT_MATTER_PATTERN =
            Pattern.compile("^---\\s*\\r?\\n(.*?)\\r?\\n---\\s*\\r?\\n(.*)", Pattern.DOTALL);

    private final String id;
    private final SkillMetadata metadata;
    private final String content;
    private final String fullContent;

    // 运行时属性
    private final File sourceFile;
    private final long lastModified;
    private final boolean isRemote;
    private final boolean isBuiltIn;
    private boolean isDirty;

    /**
     * 创建 Skill 实例
     *
     * @param id          Skill 唯一标识（文件名）
     * @param metadata    元数据
     * @param content     Markdown 内容（不含 Front Matter）
     * @param fullContent 完整原始内容
     * @param sourceFile  源文件
     * @param isRemote    是否来自远程
     * @param isBuiltIn   是否内置
     */
    public Skill(String id, SkillMetadata metadata, String content, String fullContent,
                 File sourceFile, boolean isRemote, boolean isBuiltIn) {
        this.id = id;
        this.metadata = metadata != null ? metadata : new SkillMetadata();
        this.content = content != null ? content : "";
        this.fullContent = fullContent != null ? fullContent : "";
        this.sourceFile = sourceFile;
        this.lastModified = sourceFile != null ? sourceFile.lastModified() : System.currentTimeMillis();
        this.isRemote = isRemote;
        this.isBuiltIn = isBuiltIn;
        this.isDirty = false;
    }

    /**
     * 解析 Skill 文件内容
     *
     * @param fileName    文件名（作为 ID）
     * @param fullContent 完整文件内容
     * @param sourceFile  源文件
     * @param isRemote    是否远程
     * @param isBuiltIn   是否内置
     * @return 解析后的 Skill 对象
     */
    public static Skill parse(String fileName, String fullContent, File sourceFile,
                              boolean isRemote, boolean isBuiltIn) {
        // 使用父目录名作为 Skill ID（如果是 skill.md）
        String id;
        if ("skill.md".equalsIgnoreCase(fileName)) {
            id = sourceFile.getParentFile().getName().toLowerCase();
        } else {
            id = fileName.replaceAll("\\.md$", "").toLowerCase();
        }

        Matcher matcher = FRONT_MATTER_PATTERN.matcher(fullContent);

        if (matcher.find()) {
            String yamlPart = matcher.group(1);
            String markdownPart = matcher.group(2);

            SkillMetadata metadata = SkillMetadata.fromYaml(yamlPart);
            return new Skill(id, metadata, markdownPart.trim(), fullContent, sourceFile, isRemote, isBuiltIn);
        } else {
            // 无 Front Matter，创建默认元数据
            SkillMetadata metadata = new SkillMetadata();
            metadata.setName(id);
            metadata.setDescription("No description provided");
            return new Skill(id, metadata, fullContent.trim(), fullContent, sourceFile, isRemote, isBuiltIn);
        }
    }

    /**
     * 检查输入是否匹配此 Skill 的触发词
     *
     * @param input 用户输入
     * @return 匹配分数（0-100，0 表示不匹配）
     */
    public int matchTrigger(String input) {
        if (input == null || input.trim().isEmpty()) {
            return 0;
        }

        String lowerInput = input.toLowerCase();
        int maxScore = 0;

        for (String trigger : metadata.getTriggers()) {
            String lowerTrigger = trigger.toLowerCase();
            if (lowerTrigger.isEmpty()) {
                continue;
            }

            boolean asciiWord = isAsciiWord(lowerTrigger);

            // 精确匹配（最高分）
            if (lowerInput.equals(lowerTrigger)) {
                return 100;
            }

            int baseScore = 0;
            if (asciiWord) {
                if (hasWordBoundaryMatch(lowerInput, lowerTrigger)) {
                    baseScore = 70 + Math.min(lowerTrigger.length() * 2, 20);
                } else if (startsWithWord(lowerInput, lowerTrigger)) {
                    baseScore = 80 + Math.min(lowerTrigger.length() * 2, 20);
                }
            } else {
                if (lowerTrigger.length() >= 2 && lowerInput.contains(lowerTrigger)) {
                    baseScore = 50 + Math.min(lowerTrigger.length() * 2, 20);
                }
            }

            if (baseScore > 0) {
                // 应用权重
                int weight = metadata.getTriggerWeights().getOrDefault(trigger, 100);
                int weightedScore = baseScore * weight / 100;
                maxScore = Math.max(maxScore, Math.min(weightedScore, 99));
            }
        }

        // 名称匹配（较低权重）
        String lowerName = metadata.getName().toLowerCase();
        if (lowerInput.contains(lowerName)) {
            maxScore = Math.max(maxScore, 40);
        }

        return maxScore;
    }

    private boolean isAsciiWord(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c > 127) {
                return false;
            }
            if (!(Character.isLetterOrDigit(c) || c == '_')) {
                return false;
            }
        }
        return true;
    }

    private boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    private boolean hasWordBoundaryMatch(String input, String trigger) {
        int fromIndex = 0;
        while (fromIndex <= input.length() - trigger.length()) {
            int index = input.indexOf(trigger, fromIndex);
            if (index < 0) {
                return false;
            }
            int leftIndex = index - 1;
            int rightIndex = index + trigger.length();
            boolean leftOk = leftIndex < 0 || !isWordChar(input.charAt(leftIndex));
            boolean rightOk = rightIndex >= input.length() || !isWordChar(input.charAt(rightIndex));
            if (leftOk && rightOk) {
                return true;
            }
            fromIndex = index + 1;
        }
        return false;
    }

    private boolean startsWithWord(String input, String trigger) {
        if (!input.startsWith(trigger)) {
            return false;
        }
        int nextIndex = trigger.length();
        return nextIndex >= input.length() || !isWordChar(input.charAt(nextIndex));
    }

    /**
     * 获取格式化的内容（用于发送给 AI）
     *
     * @return 格式化后的内容
     */
    public String getFormattedContent() {
        StringBuilder sb = new StringBuilder();
        sb.append("[Skill: ").append(metadata.getName()).append("]\n");
        sb.append("Version: ").append(metadata.getVersion()).append("\n");
        if (!metadata.getAuthor().equals("Unknown")) {
            sb.append("Author: ").append(metadata.getAuthor()).append("\n");
        }
        sb.append("\n");
        sb.append(content);
        return sb.toString();
    }

    /**
     * 处理模板变量并获取内容
     * 支持 {{variable}} 占位符替换
     * 替换顺序：传入的 context → YAML 中定义的 variables
     *
     * @param context 变量上下文（变量名 -> 值）
     * @return 处理后的内容
     */
    public String getProcessedContent(Map<String, String> context) {
        String processed = content;

        // 1. 替换 context 中的变量（内置变量如 player, server_name）
        if (context != null) {
            for (Map.Entry<String, String> entry : context.entrySet()) {
                processed = processed.replace("{{" + entry.getKey() + "}}",
                        entry.getValue() != null ? entry.getValue() : "");
            }
        }

        // 2. 替换 YAML variables 中定义的自定义变量
        Map<String, String> customVars = metadata.getVariables();
        for (Map.Entry<String, String> entry : customVars.entrySet()) {
            processed = processed.replace("{{" + entry.getKey() + "}}",
                    entry.getValue() != null ? entry.getValue() : "");
        }

        return processed;
    }

    /**
     * 获取格式化的、处理过模板变量的内容
     */
    public String getFormattedProcessedContent(Map<String, String> context) {
        StringBuilder sb = new StringBuilder();
        sb.append("[Skill: ").append(metadata.getName()).append("]\n");
        sb.append("Version: ").append(metadata.getVersion()).append("\n");
        if (!metadata.getAuthor().equals("Unknown")) {
            sb.append("Author: ").append(metadata.getAuthor()).append("\n");
        }
        sb.append("\n");
        sb.append(getProcessedContent(context));
        return sb.toString();
    }

    /**
     * 获取简洁信息（用于列表显示）
     *
     * @return 简洁信息字符串
     */
    public String getShortInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("§b").append(id).append(" §7- §f").append(metadata.getName());
        if (!metadata.getDescription().isEmpty()) {
            sb.append(" §7(").append(metadata.getDescription()).append(")");
        }
        return sb.toString();
    }

    /**
     * 获取详细信息（用于 info 命令）
     *
     * @return 详细信息字符串列表
     */
    public List<String> getDetailedInfo() {
        List<String> lines = new ArrayList<>();
        lines.add("§6========== Skill Info ==========");
        lines.add("§eID: §f" + id);
        lines.add("§eName: §f" + metadata.getName());
        lines.add("§eDescription: §f" + metadata.getDescription());
        lines.add("§eVersion: §f" + metadata.getVersion());
        lines.add("§eAuthor: §f" + metadata.getAuthor());

        if (!metadata.getTriggers().isEmpty()) {
            lines.add("§eTriggers: §f" + String.join(", ", metadata.getTriggers()));
        }

        if (!metadata.getTriggerWeights().isEmpty()) {
            List<String> weightStrs = new ArrayList<>();
            for (Map.Entry<String, Integer> entry : metadata.getTriggerWeights().entrySet()) {
                weightStrs.add(entry.getKey() + "=" + entry.getValue() + "%");
            }
            lines.add("§eTrigger Weights: §f" + String.join(", ", weightStrs));
        }

        if (!metadata.getCategories().isEmpty()) {
            lines.add("§eCategories: §f" + String.join(", ", metadata.getCategories()));
        }

        lines.add("§eAuto Trigger: §f" + (metadata.isAutoTrigger() ? "Yes" : "No"));
        lines.add("§ePriority: §f" + metadata.getPriority());
        lines.add("§eSource: §f" + (isRemote ? "Remote" : (isBuiltIn ? "Built-in" : "Local")));

        if (!metadata.getSource().isEmpty()) {
            lines.add("§eSource URL: §f" + metadata.getSource());
        }

        if (sourceFile != null) {
            lines.add("§eFile: §f" + sourceFile.getName());
        }

        lines.add("§eLast Modified: §f" + new java.util.Date(lastModified));
        lines.add("§6================================");

        return lines;
    }

    /**
     * 创建新的 Skill 文件内容
     *
     * @param metadata 元数据
     * @param content  正文内容
     * @return 完整的文件内容
     */
    public static String createFileContent(SkillMetadata metadata, String content) {
        StringBuilder sb = new StringBuilder();
        sb.append("---\n");
        sb.append(metadata.toYaml());
        sb.append("---\n\n");
        sb.append(content);
        return sb.toString();
    }

    // ==================== Getter ====================

    public String getId() {
        return id;
    }

    public SkillMetadata getMetadata() {
        return metadata;
    }

    public String getContent() {
        return content;
    }

    public String getFullContent() {
        return fullContent;
    }

    public File getSourceFile() {
        return sourceFile;
    }

    public long getLastModified() {
        return lastModified;
    }

    public boolean isRemote() {
        return isRemote;
    }

    public boolean isBuiltIn() {
        return isBuiltIn;
    }

    public boolean isDirty() {
        return isDirty;
    }

    public void setDirty(boolean dirty) {
        isDirty = dirty;
    }

    /**
     * 获取显示名称
     */
    public String getDisplayName() {
        return metadata.getName().isEmpty() ? id : metadata.getName();
    }
}
