package org.YanPl.manager;

import org.YanPl.FancyHelper;
import org.YanPl.model.Skill;
import org.YanPl.model.SkillMetadata;
import org.YanPl.util.ResourceUtil;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;


/**
 * Skill 加载器
 * 负责从本地文件系统加载 Skill 文件
 */
public class SkillLoader {

    private final FancyHelper plugin;

    // Skill 目录（所有 Skill 均存放在此目录下）
    private final File skillsDir;

    public SkillLoader(FancyHelper plugin) {
        this.plugin = plugin;
        this.skillsDir = new File(plugin.getDataFolder(), "skills");

        if (!skillsDir.exists()) {
            skillsDir.mkdirs();
        }
    }

    /**
     * 加载所有 Skill
     *
     * @return 加载的 Skill 列表
     */
    public List<Skill> loadAllSkills() {
        List<Skill> skills = new ArrayList<>();

        // 释放内置 Skill 资源（不覆盖已存在的文件，避免覆盖远程下载的新版本）
        ResourceUtil.releaseResources(plugin, "skills/", false, ".md");

        // 从 skillsDir 递归加载所有 Skill
        skills.addAll(loadFromDirectory(skillsDir, false, false));

        plugin.getLogger().info("[Skill] 已加载 " + skills.size() + " 个 Skill");

        return skills;
    }

    /**
     * 从指定目录加载 Skill
     *
     * @param directory 目录
     * @param isBuiltIn 是否内置（保留参数，始终传 false）
     * @param isRemote  是否远程（保留参数，始终传 false）
     * @return Skill 列表
     */
    public List<Skill> loadFromDirectory(File directory, boolean isBuiltIn, boolean isRemote) {
        List<Skill> skills = new ArrayList<>();

        if (!directory.exists() || !directory.isDirectory()) {
            return skills;
        }

        // 递归加载所有 .md 文件
        loadFromDirectoryRecursive(directory, directory, isBuiltIn, isRemote, skills);

        return skills;
    }

    /**
     * 递归从目录加载 Skill
     */
    private void loadFromDirectoryRecursive(File baseDir, File currentDir, boolean isBuiltIn, boolean isRemote, List<Skill> skills) {
        File[] files = currentDir.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                loadFromDirectoryRecursive(baseDir, file, isBuiltIn, isRemote, skills);
            } else if (file.getName().endsWith(".md")) {
                try {
                    Skill skill = loadFromFile(file, isBuiltIn, isRemote);
                    if (skill != null) {
                        skills.add(skill);
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("[Skill] 加载失败: " + file.getName() + " - " + e.getMessage());
                }
            }
        }
    }

    /**
     * 从文件加载 Skill
     *
     * @param file      文件
     * @param isBuiltIn 是否内置（保留参数，始终传 false）
     * @param isRemote  是否远程（保留参数，始终传 false）
     * @return Skill 对象
     */
    public Skill loadFromFile(File file, boolean isBuiltIn, boolean isRemote) throws IOException {
        String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        String fileName = file.getName();

        return Skill.parse(fileName, content, file, isRemote, isBuiltIn);
    }

    /**
     * 保存 Skill 到本地目录
     */
    public boolean saveSkill(Skill skill) {
        File targetFile = new File(skillsDir, sanitizeFileName(skill.getId()) + ".md");

        try {
            String content = Skill.createFileContent(skill.getMetadata(), skill.getContent());
            Files.write(targetFile.toPath(), content.getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (IOException e) {
            plugin.getLogger().warning("[Skill] 保存失败: " + skill.getId() + " - " + e.getMessage());
            return false;
        }
    }

    /**
     * 创建新的 Skill 文件
     *
     * @param id       Skill ID
     * @param metadata 元数据
     * @param content  内容
     * @return 创建的 Skill 对象，失败返回 null
     */
    public Skill createSkill(String id, SkillMetadata metadata, String content) {
        String fileName = sanitizeFileName(id) + ".md";
        File targetFile = new File(skillsDir, fileName);

        // 检查是否已存在
        if (targetFile.exists()) {
            return null;
        }

        try {
            String fileContent = Skill.createFileContent(metadata, content);
            Files.write(targetFile.toPath(), fileContent.getBytes(StandardCharsets.UTF_8));

            return loadFromFile(targetFile, false, false);
        } catch (IOException e) {
            plugin.getLogger().warning("[Skill] 创建失败: " + id + " - " + e.getMessage());
            return null;
        }
    }

    /**
     * 删除 Skill
     */
    public boolean deleteSkill(String id) {
        String sanitizedId = sanitizeFileName(id);

        // 尝试删除目录格式 skills/<id>/skill.md
        File dirFile = new File(skillsDir, sanitizedId + File.separator + "skill.md");
        if (dirFile.exists()) {
            File parentDir = dirFile.getParentFile();
            if (dirFile.delete()) {
                parentDir.delete(); // 尝试删除空目录
                return true;
            }
            return false;
        }

        // 尝试删除平面格式 skills/<id>.md
        File flatFile = new File(skillsDir, sanitizedId + ".md");
        if (flatFile.exists()) {
            return flatFile.delete();
        }

        return false;
    }

    /**
     * 更新 Skill 内容
     */
    public boolean updateSkill(String id, SkillMetadata metadata, String content) {
        // 尝试目录格式 skills/<id>/skill.md
        File dirFile = new File(skillsDir, sanitizeFileName(id) + File.separator + "skill.md");
        if (dirFile.exists()) {
            try {
                String fileContent = Skill.createFileContent(metadata, content);
                Files.write(dirFile.toPath(), fileContent.getBytes(StandardCharsets.UTF_8));
                return true;
            } catch (IOException e) {
                plugin.getLogger().warning("[Skill] 更新失败: " + id + " - " + e.getMessage());
                return false;
            }
        }

        // 尝试平面格式 skills/<id>.md
        File flatFile = new File(skillsDir, sanitizeFileName(id) + ".md");
        if (flatFile.exists()) {
            try {
                String fileContent = Skill.createFileContent(metadata, content);
                Files.write(flatFile.toPath(), fileContent.getBytes(StandardCharsets.UTF_8));
                return true;
            } catch (IOException e) {
                plugin.getLogger().warning("[Skill] 更新失败: " + id + " - " + e.getMessage());
                return false;
            }
        }

        return false;
    }

    /**
     * 统一处理文件名中的非法字符
     */
    public String sanitizeFileName(String id) {
        return id.toLowerCase().replaceAll("[^a-z0-9_-]", "");
    }

    /**
     * 获取 Skill 目录
     */
    public File getSkillsDir() {
        return skillsDir;
    }
}
