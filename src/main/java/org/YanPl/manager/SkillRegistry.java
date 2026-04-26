package org.YanPl.manager;

import org.YanPl.FancyHelper;
import org.YanPl.model.Skill;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Skill 注册表
 * 管理所有已加载的 Skill，提供快速查找和索引功能
 */
public class SkillRegistry {

    private final FancyHelper plugin;

    // Skill 存储
    private final Map<String, Skill> skillsById = new ConcurrentHashMap<>();
    private final Map<String, List<Skill>> skillsByTrigger = new ConcurrentHashMap<>();
    private final List<Skill> allSkills = new CopyOnWriteArrayList<>();

    public SkillRegistry(FancyHelper plugin) {
        this.plugin = plugin;
    }

    /**
     * 注册一个 Skill
     *
     * @param skill Skill 对象
     */
    public void register(Skill skill) {
        // 检查 ID 冲突
        if (skillsById.containsKey(skill.getId())) {
            Skill existing = skillsById.get(skill.getId());
            // 本地 Skill 优先级高于内置，远程优先级最高
            if (shouldReplace(existing, skill)) {
                unregister(existing);
            } else {
                if (plugin.getConfigManager().isDebug()) {
                    plugin.getLogger().info("[Skill] 跳过重复 Skill: " + skill.getId());
                }
                return;
            }
        }

        skillsById.put(skill.getId(), skill);
        allSkills.add(skill);

        // 建立触发词索引
        for (String trigger : skill.getMetadata().getTriggers()) {
            String lowerTrigger = trigger.toLowerCase();
            skillsByTrigger.computeIfAbsent(lowerTrigger, k -> new ArrayList<>()).add(skill);
        }

        if (plugin.getConfigManager().isDebug()) {
            plugin.getLogger().info("[Skill] 已注册: " + skill.getId() + " (" + skill.getMetadata().getName() + ")");
        }
    }

    /**
     * 判断是否应该用新 Skill 替换旧的
     */
    private boolean shouldReplace(Skill existing, Skill newer) {
        // 远程 > 本地 > 内置
        if (newer.isRemote() && !existing.isRemote()) return true;
        if (!existing.isRemote() && newer.isRemote()) return false;

        if (!existing.isBuiltIn() && newer.isBuiltIn()) return false;
        if (existing.isBuiltIn() && !newer.isBuiltIn()) return true;

        // 同类型时，后加载的替换先加载的（更新）
        return true;
    }

    /**
     * 注销一个 Skill
     *
     * @param skill Skill 对象
     */
    public void unregister(Skill skill) {
        skillsById.remove(skill.getId());
        allSkills.remove(skill);

        // 移除触发词索引
        for (String trigger : skill.getMetadata().getTriggers()) {
            String lowerTrigger = trigger.toLowerCase();
            List<Skill> list = skillsByTrigger.get(lowerTrigger);
            if (list != null) {
                list.remove(skill);
                if (list.isEmpty()) {
                    skillsByTrigger.remove(lowerTrigger);
                }
            }
        }
    }

    /**
     * 清空所有注册的 Skill
     */
    public void clear() {
        skillsById.clear();
        skillsByTrigger.clear();
        allSkills.clear();
    }

    /**
     * 通过 ID 获取 Skill
     *
     * @param id Skill ID
     * @return Skill 对象，不存在返回 null
     */
    public Skill getById(String id) {
        return skillsById.get(id.toLowerCase());
    }

    /**
     * 通过触发词获取 Skill 列表
     *
     * @param trigger 触发词
     * @return Skill 列表
     */
    public List<Skill> getByTrigger(String trigger) {
        return skillsByTrigger.getOrDefault(trigger.toLowerCase(), Collections.emptyList());
    }

    /**
     * 获取所有已注册的 Skill
     *
     * @return Skill 列表
     */
    public List<Skill> getAllSkills() {
        return new ArrayList<>(allSkills);
    }

    /**
     * 搜索 Skill
     *
     * @param query 搜索关键词
     * @return 匹配的 Skill 列表
     */
    public List<Skill> search(String query) {
        String lowerQuery = query.toLowerCase();

        return allSkills.stream()
                .filter(skill -> {
                    // 搜索 ID
                    if (skill.getId().contains(lowerQuery)) return true;

                    // 搜索名称
                    if (skill.getMetadata().getName().toLowerCase().contains(lowerQuery)) return true;

                    // 搜索描述
                    if (skill.getMetadata().getDescription().toLowerCase().contains(lowerQuery)) return true;

                    // 搜索触发词
                    for (String trigger : skill.getMetadata().getTriggers()) {
                        if (trigger.toLowerCase().contains(lowerQuery)) return true;
                    }

                    // 搜索分类
                    for (String category : skill.getMetadata().getCategories()) {
                        if (category.toLowerCase().contains(lowerQuery)) return true;
                    }

                    return false;
                })
                .collect(Collectors.toList());
    }

    /**
     * 查找最匹配的 Skill
     *
     * @param input 输入文本
     * @return 最匹配的 Skill，无匹配返回 null
     */
    public Skill findBestMatch(String input) {
        Skill bestMatch = null;
        int bestScore = 0;

        for (Skill skill : allSkills) {
            int score = skill.matchTrigger(input);
            // 应用优先级加成
            int priority = skill.getMetadata().getPriority();
            if (priority != 50) {
                score = score * priority / 50;
                score = Math.min(score, 100);
            }
            if (score > bestScore) {
                bestScore = score;
                bestMatch = skill;
            }
        }

        // 阈值：至少 30 分才算匹配
        return bestScore >= 30 ? bestMatch : null;
    }

    /**
     * 查找所有匹配的 Skill（按匹配度排序）
     *
     * @param input 输入文本
     * @param limit 最大返回数量
     * @return 匹配的 Skill 列表
     */
    public List<Skill> findMatches(String input, int limit) {
        return allSkills.stream()
                .map(skill -> new AbstractMap.SimpleEntry<>(skill, skill.matchTrigger(input)))
                .filter(entry -> entry.getValue() >= 30)
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(limit)
                .map(AbstractMap.SimpleEntry::getKey)
                .collect(Collectors.toList());
    }

    /**
     * 获取所有可自动触发的 Skill
     *
     * @return Skill 列表
     */
    public List<Skill> getAutoTriggerSkills() {
        return allSkills.stream()
                .filter(skill -> skill.getMetadata().isAutoTrigger())
                .collect(Collectors.toList());
    }

    /**
     * 获取所有本地 Skill
     *
     * @return Skill 列表
     */
    public List<Skill> getLocalSkills() {
        return allSkills.stream()
                .filter(skill -> !skill.isBuiltIn() && !skill.isRemote())
                .collect(Collectors.toList());
    }

    /**
     * 获取所有内置 Skill
     *
     * @return Skill 列表
     */
    public List<Skill> getBuiltinSkills() {
        return allSkills.stream()
                .filter(Skill::isBuiltIn)
                .collect(Collectors.toList());
    }

    /**
     * 获取所有远程 Skill
     *
     * @return Skill 列表
     */
    public List<Skill> getRemoteSkills() {
        return allSkills.stream()
                .filter(Skill::isRemote)
                .collect(Collectors.toList());
    }

    /**
     * 获取 Skill 数量
     *
     * @return Skill 总数
     */
    public int size() {
        return allSkills.size();
    }

    /**
     * 检查是否存在指定 ID 的 Skill
     *
     * @param id Skill ID
     * @return 是否存在
     */
    public boolean hasSkill(String id) {
        return skillsById.containsKey(id.toLowerCase());
    }

    /**
     * 获取所有 Skill ID 列表
     *
     * @return ID 列表
     */
    public List<String> getAllIds() {
        return new ArrayList<>(skillsById.keySet());
    }

    /**
     * 获取所有 Skill 摘要信息（用于提示词）
     * 格式：id: name - description (triggers)
     *
     * @return Skill 摘要列表
     */
    public List<String> getAllSkillSummaries() {
        return allSkills.stream()
                .map(skill -> {
                    StringBuilder sb = new StringBuilder();
                    sb.append(skill.getId()).append(": ");
                    sb.append(skill.getMetadata().getName());
                    if (!skill.getMetadata().getDescription().isEmpty()) {
                        sb.append(" - ").append(skill.getMetadata().getDescription());
                    }
                    List<String> triggers = skill.getMetadata().getTriggers();
                    if (!triggers.isEmpty()) {
                        sb.append(" [TRIGGER when: ").append(String.join(", ", triggers)).append("]");
                    }
                    return sb.toString();
                })
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * 获取所有触发词列表
     *
     * @return 触发词列表
     */
    public List<String> getAllTriggers() {
        return new ArrayList<>(skillsByTrigger.keySet());
    }
}
