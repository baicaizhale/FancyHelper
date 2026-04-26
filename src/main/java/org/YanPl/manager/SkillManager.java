package org.YanPl.manager;

import org.YanPl.FancyHelper;
import org.YanPl.model.Skill;
import org.YanPl.model.SkillMetadata;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Skill 管理器
 * 负责 Skill 的加载、管理和提供查询接口
 */
public class SkillManager {

    private final FancyHelper plugin;
    private final SkillLoader loader;
    private final SkillRegistry registry;

    // 记录每个玩家当前会话已加载的 Skill（避免重复加载）
    private final Map<UUID, Set<String>> playerLoadedSkills = new ConcurrentHashMap<>();

    public SkillManager(FancyHelper plugin) {
        this.plugin = plugin;
        this.loader = new SkillLoader(plugin);
        this.registry = new SkillRegistry(plugin);
    }

    /**
     * 初始化 Skill 系统
     */
    public void initialize() {
        plugin.getLogger().info("[Skill] 正在初始化 Skill 系统...");

        // 加载所有 Skill
        reloadSkills();

        plugin.getLogger().info("[Skill] 已加载 " + registry.size() + " 个 Skill");
    }

    /**
     * 重新加载所有 Skill
     */
    public void reloadSkills() {
        registry.clear();

        List<Skill> skills = loader.loadAllSkills();
        for (Skill skill : skills) {
            registry.register(skill);
        }
    }

    /**
     * 获取 SkillLoader
     */
    public SkillLoader getLoader() {
        return loader;
    }

    /**
     * 获取 SkillRegistry
     */
    public SkillRegistry getRegistry() {
        return registry;
    }

    /**
     * 通过 ID 获取 Skill
     *
     * @param id Skill ID
     * @return Skill 对象
     */
    public Skill getSkill(String id) {
        return registry.getById(id);
    }

    /**
     * 搜索 Skill
     *
     * @param query 搜索关键词
     * @return 匹配的 Skill 列表
     */
    public List<Skill> searchSkills(String query) {
        return registry.search(query);
    }

    /**
     * 获取所有 Skill
     *
     * @return Skill 列表
     */
    public List<Skill> getAllSkills() {
        return registry.getAllSkills();
    }

    /**
     * 获取所有 Skill ID 列表（用于显示在提示词中）
     *
     * @return ID 列表
     */
    public List<String> getSkillIdsForPrompt() {
        return registry.getAllIds();
    }

    /**
     * 获取所有 Skill 摘要信息（用于提示词）
     * 格式：id: name - description [TRIGGER when: trigger1, trigger2]
     *
     * @return Skill 摘要列表
     */
    public List<String> getSkillSummariesForPrompt() {
        return registry.getAllSkillSummaries();
    }

    /**
     * 获取精简的 Skill 列表（仅 ID 和名称，用于 prompt）
     * 格式：id: name
     *
     * @return 精简列表
     */
    public List<String> getSkillBriefList() {
        return registry.getAllSkills().stream()
                .map(skill -> skill.getId() + ": " + skill.getMetadata().getName())
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * 获取所有 Skill 触发词（去重排序）
     *
     * @return 触发词列表
     */
    public List<String> getAllTriggers() {
        return registry.getAllTriggers();
    }

    /**
     * 查找最佳匹配的 Skill
     *
     * @param input 输入文本
     * @return 最佳匹配的 Skill
     */
    public Skill findBestMatch(String input) {
        return registry.findBestMatch(input);
    }

    /**
     * 查找多个匹配的 Skill（用于自动注入）
     * 按匹配分数排序，返回前 maxResults 个
     *
     * @param input      输入文本
     * @param maxResults 最大返回数量
     * @param minScore   最小匹配分数（0-100）
     * @return 匹配的 Skill 列表
     */
    public List<Skill> findMatchingSkills(String input, int maxResults, int minScore) {
        List<SkillMatch> matches = new ArrayList<>();
        
        for (Skill skill : registry.getAllSkills()) {
            int score = skill.matchTrigger(input);
            // 应用优先级加成
            int priority = skill.getMetadata().getPriority();
            if (priority != 50) {
                score = score * priority / 50;
                score = Math.min(score, 100);
            }
            if (score >= minScore) {
                matches.add(new SkillMatch(skill, score));
            }
        }
        
        // 按分数降序排序
        matches.sort((a, b) -> Integer.compare(b.score, a.score));
        
        // 取前 maxResults 个
        List<Skill> result = new ArrayList<>();
        for (int i = 0; i < Math.min(maxResults, matches.size()); i++) {
            result.add(matches.get(i).skill);
        }
        
        return result;
    }
    
    /**
     * Skill 匹配结果
     */
    private static class SkillMatch {
        final Skill skill;
        final int score;
        
        SkillMatch(Skill skill, int score) {
            this.skill = skill;
            this.score = score;
        }
    }

    /**
     * 为玩家加载 Skill 到当前对话
     *
     * @param player  玩家
     * @param skillId Skill ID
     * @return 是否成功加载
     */
    public boolean loadSkillForPlayer(Player player, String skillId) {
        Skill skill = registry.getById(skillId);
        if (skill == null) {
            return false;
        }

        UUID uuid = player.getUniqueId();
        Set<String> loaded = playerLoadedSkills.computeIfAbsent(uuid, k -> new HashSet<>());
        return loaded.add(skill.getId().toLowerCase());
    }

    /**
     * 从玩家的会话中卸载指定 Skill
     *
     * @param player  玩家
     * @param skillId Skill ID
     * @return 是否成功卸载
     */
    public boolean unloadSkillForPlayer(Player player, String skillId) {
        UUID uuid = player.getUniqueId();
        Set<String> loaded = playerLoadedSkills.get(uuid);
        if (loaded == null) {
            return false;
        }
        return loaded.remove(skillId.toLowerCase());
    }

    /**
     * 获取玩家已加载的 Skill
     *
     * @param player 玩家
     * @return 已加载的 Skill ID 集合
     */
    public Set<String> getPlayerLoadedSkills(Player player) {
        return playerLoadedSkills.getOrDefault(player.getUniqueId(), Collections.emptySet());
    }

    /**
     * 检查玩家是否已加载某个 Skill
     *
     * @param player  玩家
     * @param skillId Skill ID
     * @return 是否已加载
     */
    public boolean hasPlayerLoadedSkill(Player player, String skillId) {
        return getPlayerLoadedSkills(player).contains(skillId.toLowerCase());
    }

    /**
     * 清除玩家的 Skill 加载记录
     *
     * @param player 玩家
     */
    public void clearPlayerSkills(Player player) {
        playerLoadedSkills.remove(player.getUniqueId());
    }

    /**
     * 创建新 Skill
     *
     * @param id       Skill ID
     * @param metadata 元数据
     * @param content  内容
     * @return 创建的 Skill，失败返回 null
     */
    public Skill createSkill(String id, SkillMetadata metadata, String content) {
        Skill skill = loader.createSkill(id, metadata, content);
        if (skill != null) {
            registry.register(skill);
        }
        return skill;
    }

    /**
     * 删除 Skill
     *
     * @param id Skill ID
     * @return 是否删除成功
     */
    public boolean deleteSkill(String id) {
        Skill skill = registry.getById(id);
        if (skill == null) {
            return false;
        }

        // 只能删除本地或远程 Skill，不能删除内置
        if (skill.isBuiltIn()) {
            return false;
        }

        if (loader.deleteSkill(id)) {
            registry.unregister(skill);
            return true;
        }
        return false;
    }

    /**
     * 更新 Skill
     *
     * @param id       Skill ID
     * @param metadata 新元数据
     * @param content  新内容
     * @return 是否更新成功
     */
    public boolean updateSkill(String id, SkillMetadata metadata, String content) {
        Skill skill = registry.getById(id);
        if (skill == null) {
            return false;
        }

        // 只能更新本地 Skill
        if (skill.isBuiltIn() || skill.isRemote()) {
            return false;
        }

        if (loader.updateSkill(id, metadata, content)) {
            // 重新加载该 Skill
            try {
                File file = new File(loader.getLocalDir(), id + ".md");
                Skill updatedSkill = loader.loadFromFile(file, false, false);
                registry.unregister(skill);
                registry.register(updatedSkill);
                return true;
            } catch (Exception e) {
                plugin.getLogger().warning("[Skill] 重新加载失败: " + id + " - " + e.getMessage());
                return false;
            }
        }
        return false;
    }

    /**
     * 获取 Skill 数量
     *
     * @return Skill 总数
     */
    public int getSkillCount() {
        return registry.size();
    }

    /**
     * 获取内置 Skill 数量
     *
     * @return 内置 Skill 数量
     */
    public int getBuiltinCount() {
        return registry.getBuiltinSkills().size();
    }

    /**
     * 获取本地 Skill 数量
     *
     * @return 本地 Skill 数量
     */
    public int getLocalCount() {
        return registry.getLocalSkills().size();
    }

    /**
     * 获取远程 Skill 数量
     *
     * @return 远程 Skill 数量
     */
    public int getRemoteCount() {
        return registry.getRemoteSkills().size();
    }

    /**
     * 检查是否存在指定 ID 的 Skill
     *
     * @param id Skill ID
     * @return 是否存在
     */
    public boolean hasSkill(String id) {
        return registry.hasSkill(id);
    }

    /**
     * 尝试自动匹配并加载 Skill（带上下文记忆）
     *
     * @param player 玩家
     * @param input  输入文本
     * @param recentSkills 最近使用的 Skill ID 列表（用于上下文加权）
     * @return 匹配的 Skill，无匹配返回 null
     */
    public Skill autoLoadSkill(Player player, String input, List<String> recentSkills) {
        Skill skill = registry.findBestMatch(input);
        if (skill != null && skill.getMetadata().isAutoTrigger()) {
            // 检查是否已加载
            if (!hasPlayerLoadedSkill(player, skill.getId())) {
                loadSkillForPlayer(player, skill.getId());
                return skill;
            }
        }
        return null;
    }

    /**
     * 获取玩家最近使用的 Skill（用于上下文记忆）
     */
    public List<String> getRecentSkills(Player player, int limit) {
        // 暂时返回空，后续可基于日志实现
        return Collections.emptyList();
    }

    /**
     * 获取格式化的 Skill 列表（用于显示）
     *
     * @return 格式化的字符串列表
     */
    public List<String> getFormattedSkillList() {
        List<String> lines = new ArrayList<>();
        List<Skill> skills = registry.getAllSkills();

        if (skills.isEmpty()) {
            lines.add("§c暂无可用 Skill");
            return lines;
        }

        lines.add("§6========== 可用 Skill 列表 ==========");

        // 按来源分组
        List<Skill> builtin = registry.getBuiltinSkills();
        List<Skill> local = registry.getLocalSkills();
        List<Skill> remote = registry.getRemoteSkills();

        if (!builtin.isEmpty()) {
            lines.add("§e§l内置 Skill:");
            for (Skill skill : builtin) {
                lines.add("  " + skill.getShortInfo());
            }
        }

        if (!local.isEmpty()) {
            lines.add("§e§l本地 Skill:");
            for (Skill skill : local) {
                lines.add("  " + skill.getShortInfo());
            }
        }

        if (!remote.isEmpty()) {
            lines.add("§e§l远程 Skill:");
            for (Skill skill : remote) {
                lines.add("  " + skill.getShortInfo());
            }
        }

        lines.add("§6====================================");
        lines.add("§7共 " + skills.size() + " 个 Skill | 使用 /fancy skill info <id> 查看详情");

        return lines;
    }
}
