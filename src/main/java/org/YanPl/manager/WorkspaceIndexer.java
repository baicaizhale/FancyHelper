package org.YanPl.manager;

import org.YanPl.FancyHelper;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.SimpleCommandMap;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class WorkspaceIndexer {
    /**
     * 工作区索引器：用于索引服务器上可用的命令。
     */
    private final FancyHelper plugin;
    private List<String> indexedCommands = new ArrayList<>();

    public WorkspaceIndexer(FancyHelper plugin) {
        this.plugin = plugin;
    }

    public void indexAll() {
        // 同步执行命令索引（调用方可选择异步）
        indexCommands();
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
            
            if (plugin.getConfigManager().isDebug()) {
                plugin.getLogger().info("已索引 " + indexedCommands.size() + " 个命令。");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("索引命令时出错: " + e.getMessage());
            plugin.getCloudErrorReport().report(e);
        }
    }

    public List<String> getIndexedCommands() {
        return indexedCommands;
    }
}
