package org.YanPl.manager;

import org.YanPl.FancyHelper;
import org.YanPl.model.Skill;
import org.YanPl.util.ResourceUtil;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;


/**
 * Skill 加载器
 * 负责从本地文件系统和插件资源中加载 Skill 文件
 */
public class SkillLoader {

    private final FancyHelper plugin;

    // Skill 目录
    private final File skillsDir;
    private final File builtinDir;
    private final File localDir;
    private final File remoteDir;

    public SkillLoader(FancyHelper plugin) {
        this.plugin = plugin;
        this.skillsDir = new File(plugin.getDataFolder(), "skills");
        this.builtinDir = new File(skillsDir, "builtin");
        this.localDir = new File(skillsDir, "local");
        this.remoteDir = new File(skillsDir, "remote");

        // 确保目录存在
        ensureDirectories();
    }

    /**
     * 确保所有 Skill 目录存在
     */
    private void ensureDirectories() {
        if (!skillsDir.exists()) {
            skillsDir.mkdirs();
        }
        if (!builtinDir.exists()) {
            builtinDir.mkdirs();
        }
        if (!localDir.exists()) {
            localDir.mkdirs();
        }
        if (!remoteDir.exists()) {
            remoteDir.mkdirs();
        }
    }

    /**
     * 加载所有 Skill
     *
     * @return 加载的 Skill 列表
     */
    public List<Skill> loadAllSkills() {
        List<Skill> skills = new ArrayList<>();

        // 1. 释放内置 Skill 资源到 builtin 目录
        releaseBuiltinSkills();

        // 2. 从 builtin 目录加载内置 Skill
        skills.addAll(loadFromDirectory(builtinDir, true, false));

        // 3. 加载本地 Skill
        skills.addAll(loadFromDirectory(localDir, false, false));

        // 4. 加载远程 Skill
        skills.addAll(loadFromDirectory(remoteDir, false, true));

        plugin.getLogger().info("[Skill] 已加载 " + skills.size() + " 个 Skill");

        return skills;
    }

    /**
     * 从指定目录加载 Skill
     *
     * @param directory 目录
     * @param isBuiltIn 是否内置
     * @param isRemote  是否远程
     * @return Skill 列表
     */
    public List<Skill> loadFromDirectory(File directory, boolean isBuiltIn, boolean isRemote) {
        List<Skill> skills = new ArrayList<>();

        if (!directory.exists() || !directory.isDirectory()) {
            return skills;
        }

        // 递归加载所有 .md 文件
        loadFromDirectoryRecursive(directory, directory, isBuiltIn, isRemote, skills, Collections.emptySet());

        return skills;
    }

    /**
     * 递归从目录加载 Skill
     *
     * @param baseDir   基础目录（用于计算相对路径）
     * @param currentDir 当前目录
     * @param isBuiltIn 是否内置
     * @param isRemote  是否远程
     * @param skills    Skill 列表
     */
    private void loadFromDirectoryRecursive(File baseDir, File currentDir, boolean isBuiltIn, boolean isRemote, List<Skill> skills, Set<File> excludedDirs) {
        File[] files = currentDir.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                if (isExcludedDir(file, excludedDirs)) {
                    continue;
                }
                // 递归处理子目录
                loadFromDirectoryRecursive(baseDir, file, isBuiltIn, isRemote, skills, excludedDirs);
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

    private boolean isExcludedDir(File dir, Set<File> excludedDirs) {
        if (excludedDirs.isEmpty()) {
            return false;
        }
        for (File excluded : excludedDirs) {
            if (isSamePath(dir, excluded)) {
                return true;
            }
        }
        return false;
    }

    private boolean isSamePath(File a, File b) {
        try {
            return a.getCanonicalFile().equals(b.getCanonicalFile());
        } catch (IOException e) {
            return a.equals(b);
        }
    }

    /**
     * 从文件加载 Skill
     *
     * @param file      文件
     * @param isBuiltIn 是否内置
     * @param isRemote  是否远程
     * @return Skill 对象
     */
    public Skill loadFromFile(File file, boolean isBuiltIn, boolean isRemote) throws IOException {
        String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        String fileName = file.getName();

        return Skill.parse(fileName, content, file, isRemote, isBuiltIn);
    }

    /**
     * 释放内置 Skill 资源到 builtin 目录
     */
    private void releaseBuiltinSkills() {
        // 先释放到临时目录，然后移动到 builtin 目录
        ResourceUtil.releaseResources(plugin, "skills/", true, ".md");
        
        // 将释放的文件从 skills/ 移动到 skills/builtin/
        moveToBuiltinDir();
    }
    
    /**
     * 将 skills/ 目录下的 Skill 目录移动到 builtin/ 目录
     */
    private void moveToBuiltinDir() {
        if (!skillsDir.exists() || !skillsDir.isDirectory()) {
            return;
        }
        
        File[] files = skillsDir.listFiles();
        if (files == null) {
            return;
        }
        
        for (File file : files) {
            if (file.isDirectory() && !file.equals(builtinDir) && 
                !file.equals(localDir) && !file.equals(remoteDir)) {
                // 这是 Skill 目录，移动到 builtin/
                File targetDir = new File(builtinDir, file.getName());
                try {
                    if (targetDir.exists()) {
                        // 如果目标已存在，删除旧的
                        deleteDirectory(targetDir);
                    }
                    Files.move(file.toPath(), targetDir.toPath());
                } catch (IOException e) {
                    plugin.getLogger().warning("[Skill] 移动目录失败: " + file.getName() + " - " + e.getMessage());
                }
            }
        }
    }
    
    /**
     * 递归删除目录
     */
    private void deleteDirectory(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }
        dir.delete();
    }

    /**
     * 保存 Skill 到本地目录
     *
     * @param skill Skill 对象
     * @return 是否保存成功
     */
    public boolean saveSkill(Skill skill) {
        File targetFile = new File(localDir, skill.getId() + ".md");

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
    public Skill createSkill(String id, org.YanPl.model.SkillMetadata metadata, String content) {
        String fileName = id.toLowerCase().replaceAll("[^a-z0-9_-]", "") + ".md";
        File targetFile = new File(localDir, fileName);

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
     * 删除本地 Skill
     *
     * @param id Skill ID
     * @return 是否删除成功
     */
    public boolean deleteSkill(String id) {
        File file = new File(localDir, id + ".md");

        if (!file.exists()) {
            // 尝试从远程目录删除
            file = new File(remoteDir, id + ".md");
        }

        if (file.exists()) {
            return file.delete();
        }

        return false;
    }

    /**
     * 更新 Skill 内容
     *
     * @param id       Skill ID
     * @param metadata 新元数据
     * @param content  新内容
     * @return 是否更新成功
     */
    public boolean updateSkill(String id, org.YanPl.model.SkillMetadata metadata, String content) {
        File file = new File(localDir, id + ".md");

        if (!file.exists()) {
            return false;
        }

        try {
            String fileContent = Skill.createFileContent(metadata, content);
            Files.write(file.toPath(), fileContent.getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (IOException e) {
            plugin.getLogger().warning("[Skill] 更新失败: " + id + " - " + e.getMessage());
            return false;
        }
    }

    /**
     * 获取内置 Skill 目录
     */
    public File getBuiltinDir() {
        return builtinDir;
    }

    /**
     * 获取本地 Skill 目录
     */
    public File getLocalDir() {
        return localDir;
    }

    /**
     * 获取远程 Skill 目录
     */
    public File getRemoteDir() {
        return remoteDir;
    }

    /**
     * 获取所有 Skill 目录
     */
    public List<File> getAllSkillDirs() {
        return Arrays.asList(builtinDir, localDir, remoteDir);
    }
}
