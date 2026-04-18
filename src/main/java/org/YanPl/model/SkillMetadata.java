package org.YanPl.model;

import org.yaml.snakeyaml.Yaml;

import java.util.*;

/**
 * Skill 元数据类
 * 解析和存储 Skill 文件的 YAML Front Matter
 */
public class SkillMetadata {

    private String name = "";
    private String description = "";
    private List<String> triggers = new ArrayList<>();
    private Map<String, Integer> triggerWeights = new HashMap<>();
    private boolean autoTrigger = true;
    private String author = "Unknown";
    private String version = "1.0.0";
    private String source = "";
    private List<String> categories = new ArrayList<>();
    private Map<String, Object> extra = new HashMap<>();
    private int priority = 50;

    /**
     * 从 YAML 字符串解析元数据
     *
     * @param yamlContent YAML 格式的元数据字符串
     * @return SkillMetadata 对象
     */
    public static SkillMetadata fromYaml(String yamlContent) {
        SkillMetadata metadata = new SkillMetadata();

        if (yamlContent == null || yamlContent.trim().isEmpty()) {
            return metadata;
        }

        try {
            Yaml yaml = new Yaml();
            Map<String, Object> data = yaml.load(yamlContent);

            if (data == null) {
                return metadata;
            }

            metadata.name = getString(data, "name", "");
            metadata.description = getString(data, "description", "");
            metadata.triggers = getStringList(data, "triggers");
            metadata.triggerWeights = getTriggerWeights(data, "trigger_weights");
            metadata.autoTrigger = getBoolean(data, "auto_trigger", true);
            metadata.author = getString(data, "author", "Unknown");
            metadata.version = getString(data, "version", "1.0.0");
            metadata.source = getString(data, "source", "");
            metadata.categories = getStringList(data, "categories");
            metadata.priority = getInt(data, "priority", 50);

            // 存储额外的未知字段
            for (Map.Entry<String, Object> entry : data.entrySet()) {
                String key = entry.getKey();
                if (!isStandardField(key)) {
                    metadata.extra.put(key, entry.getValue());
                }
            }

        } catch (Exception e) {
            // 解析失败时返回默认元数据
        }

        return metadata;
    }

    /**
     * 转换为 YAML 字符串
     *
     * @return YAML 格式的元数据字符串
     */
    public String toYaml() {
        Map<String, Object> data = new LinkedHashMap<>();

        data.put("name", name);
        data.put("description", description);
        if (!triggers.isEmpty()) {
            data.put("triggers", triggers);
        }
        if (!triggerWeights.isEmpty()) {
            data.put("trigger_weights", triggerWeights);
        }
        data.put("auto_trigger", autoTrigger);
        data.put("author", author);
        data.put("version", version);
        if (!source.isEmpty()) {
            data.put("source", source);
        }
        if (!categories.isEmpty()) {
            data.put("categories", categories);
        }
        if (priority != 50) {
            data.put("priority", priority);
        }

        // 添加额外的字段
        data.putAll(extra);

        Yaml yaml = new Yaml();
        return yaml.dump(data);
    }

    /**
     * 检查是否为标准字段
     */
    private static boolean isStandardField(String key) {
        return key.equals("name") || key.equals("description") || key.equals("triggers")
                || key.equals("auto_trigger") || key.equals("author") || key.equals("version")
                || key.equals("source") || key.equals("categories");
    }

    /**
     * 安全获取字符串值
     */
    private static String getString(Map<String, Object> data, String key, String defaultValue) {
        Object value = data.get(key);
        if (value instanceof String) {
            return (String) value;
        }
        return defaultValue;
    }

    /**
     * 安全获取布尔值
     */
    private static boolean getBoolean(Map<String, Object> data, String key, boolean defaultValue) {
        Object value = data.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return defaultValue;
    }

    /**
     * 安全获取字符串列表
     */
    private static List<String> getStringList(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value instanceof List) {
            List<String> result = new ArrayList<>();
            for (Object item : (List<?>) value) {
                if (item instanceof String) {
                    result.add((String) item);
                }
            }
            return result;
        }
        if (value instanceof String) {
            String text = ((String) value).trim();
            if (text.isEmpty()) {
                return new ArrayList<>();
            }
            String[] parts = text.split(",");
            List<String> result = new ArrayList<>();
            for (String part : parts) {
                String item = part.trim();
                if (!item.isEmpty()) {
                    result.add(item);
                }
            }
            return result;
        }
        return new ArrayList<>();
    }

    /**
     * 安全获取整数
     */
    private static int getInt(Map<String, Object> data, String key, int defaultValue) {
        Object value = data.get(key);
        if (value instanceof Integer) {
            return (Integer) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }

    /**
     * 安全获取触发词权重映射
     */
    private static Map<String, Integer> getTriggerWeights(Map<String, Object> data, String key) {
        Object value = data.get(key);
        Map<String, Integer> result = new HashMap<>();
        if (value instanceof Map) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                if (entry.getKey() instanceof String && entry.getValue() instanceof Integer) {
                    result.put((String) entry.getKey(), (Integer) entry.getValue());
                }
            }
        }
        return result;
    }

    // ==================== Getter 和 Setter ====================

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<String> getTriggers() {
        return new ArrayList<>(triggers);
    }

    public void setTriggers(List<String> triggers) {
        this.triggers = new ArrayList<>(triggers);
    }

    public boolean isAutoTrigger() {
        return autoTrigger;
    }

    public void setAutoTrigger(boolean autoTrigger) {
        this.autoTrigger = autoTrigger;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public Map<String, Integer> getTriggerWeights() {
        return new HashMap<>(triggerWeights);
    }

    public void setTriggerWeights(Map<String, Integer> triggerWeights) {
        this.triggerWeights = new HashMap<>(triggerWeights);
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public List<String> getCategories() {
        return new ArrayList<>(categories);
    }

    public void setCategories(List<String> categories) {
        this.categories = new ArrayList<>(categories);
    }

    public Map<String, Object> getExtra() {
        return new HashMap<>(extra);
    }

    /**
     * 获取完整的显示名称（带版本）
     */
    public String getFullName() {
        return name + " v" + version;
    }
}
