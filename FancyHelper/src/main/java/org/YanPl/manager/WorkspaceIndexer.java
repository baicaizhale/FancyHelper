package org.YanPl.manager;

import org.YanPl.FancyHelper;
import org.YanPl.util.ResourceUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.SimpleCommandMap;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class WorkspaceIndexer {
    /**
     * 工作区索引器：用于索引服务器上可用的命令以及插件内的预设文件列表。
     */
    private final FancyHelper plugin;
    private List<String> indexedCommands = new ArrayList<>();
    private List<String> indexedPresets = new ArrayList<>();

    public WorkspaceIndexer(FancyHelper plugin) {
        this.plugin = plugin;
    }

    public void indexAll() {
        // 同步执行命令与预设索引（调用方可选择异步）
        indexCommands();
        indexPresets();
    }

    @SuppressWarnings("unchecked")
    public void indexCommands() {
        indexedCommands.clear();
        try {
            Field commandMapField = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            commandMapField.setAccessible(true);
            SimpleCommandMap commandMap = (SimpleCommandMap) commandMapField.get(Bukkit.getServer());
            
            Field knownCommandsField = SimpleCommandMap.class.getDeclaredField("knownCommands");
            knownCommandsField.setAccessible(true);
            Map<String, Command> knownCommands = (Map<String, Command>) knownCommandsField.get(commandMap);
            
            indexedCommands.addAll(knownCommands.keySet().stream()
                    .filter(name -> !name.contains(":")) 
                    .collect(Collectors.toList()));
            
            plugin.getLogger().info("已索引 " + indexedCommands.size() + " 个命令。");
        } catch (Exception e) {
            plugin.getLogger().warning("索引命令时出错: " + e.getMessage());
            plugin.getCloudErrorReport().report(e);
        }
    }

    public void indexPresets() {
        indexedPresets.clear();
        File presetDir = new File(plugin.getDataFolder(), "preset");
        if (!presetDir.exists()) {
            presetDir.mkdirs();
        }
        
        ResourceUtil.releaseResources(plugin, "preset/", false, ".txt");
        
        File[] files = presetDir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile() && file.getName().endsWith(".txt")) {
                    indexedPresets.add(file.getName());
                }
            }
        }
        plugin.getLogger().info("已索引 " + indexedPresets.size() + " 个预设文件。");
    }

    public List<String> getIndexedCommands() {
        return indexedCommands;
    }

    public List<String> getIndexedPresets() {
        return indexedPresets;
    }
}
