package org.YanPl.manager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import org.YanPl.FancyHelper;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InstructionManager {
    private final FancyHelper plugin;
    private final File instructionDir;
    private final Gson gson;
    private final Map<UUID, List<PlayerInstruction>> cache;
    private static final Type LIST_TYPE = new TypeToken<List<PlayerInstruction>>(){}.getType();

    public static class PlayerInstruction {
        private String content;
        private String timestamp;
        private String category;

        public PlayerInstruction(String content, String category) {
            this.content = content;
            this.category = category != null ? category : "general";
            this.timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }

        public String getContent() { return content; }
        public String getTimestamp() { return timestamp; }
        public String getCategory() { return category; }
    }

    public InstructionManager(FancyHelper plugin) {
        this.plugin = plugin;
        this.instructionDir = new File(plugin.getDataFolder(), "instruction");
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.cache = new ConcurrentHashMap<>();
        
        if (!instructionDir.exists()) {
            instructionDir.mkdirs();
        }
    }

    public String addInstruction(Player player, String content, String category) {
        UUID uuid = player.getUniqueId();
        List<PlayerInstruction> instructions = getInstructions(uuid);
        
        if (instructions.size() >= 50) {
            return "error: 已达到最大记忆数量限制 (50条)，请先删除一些旧记忆";
        }
        
        PlayerInstruction instruction = new PlayerInstruction(content, category);
        instructions.add(instruction);
        saveInstructions(uuid, instructions);
        
        if (plugin.getConfigManager().isDebug()) {
            plugin.getLogger().info("[Instruction] 玩家 " + player.getName() + " 添加了新记忆: " + content);
        }
        return "success: 已记住: " + content;
    }

    public String removeInstruction(Player player, int index) {
        UUID uuid = player.getUniqueId();
        List<PlayerInstruction> instructions = getInstructions(uuid);
        
        if (index < 1 || index > instructions.size()) {
            return "error: 无效的序号，当前共有 " + instructions.size() + " 条记忆";
        }
        
        PlayerInstruction removed = instructions.remove(index - 1);
        saveInstructions(uuid, instructions);
        
        if (plugin.getConfigManager().isDebug()) {
            plugin.getLogger().info("[Instruction] 玩家 " + player.getName() + " 删除了记忆: " + removed.getContent());
        }
        return "success: 已删除第 " + index + " 条记忆: " + removed.getContent();
    }

    public String updateInstruction(Player player, int index, String content, String category) {
        UUID uuid = player.getUniqueId();
        List<PlayerInstruction> instructions = getInstructions(uuid);
        
        if (index < 1 || index > instructions.size()) {
            return "error: 无效的序号，当前共有 " + instructions.size() + " 条记忆";
        }
        
        if (content == null || content.trim().isEmpty()) {
            return "error: 记忆内容不能为空";
        }
        
        PlayerInstruction updated = new PlayerInstruction(content.trim(), category);
        instructions.set(index - 1, updated);
        saveInstructions(uuid, instructions);
        
        if (plugin.getConfigManager().isDebug()) {
            plugin.getLogger().info("[Instruction] 玩家 " + player.getName() + " 修改了第 " + index + " 条记忆: " + content);
        }
        return "success: 已修改第 " + index + " 条记忆为: " + content;
    }

    public String clearInstructions(Player player) {
        UUID uuid = player.getUniqueId();
        cache.remove(uuid);
        
        File file = getInstructionFile(uuid);
        if (file.exists()) {
            file.delete();
        }
        
        if (plugin.getConfigManager().isDebug()) {
            plugin.getLogger().info("[Instruction] 玩家 " + player.getName() + " 清空了所有记忆");
        }
        return "success: 已清空所有记忆";
    }

    public String listInstructions(Player player) {
        UUID uuid = player.getUniqueId();
        List<PlayerInstruction> instructions = getInstructions(uuid);
        
        if (instructions.isEmpty()) {
            return "当前没有任何记忆";
        }
        
        StringBuilder sb = new StringBuilder("你的记忆列表:\n");
        for (int i = 0; i < instructions.size(); i++) {
            PlayerInstruction inst = instructions.get(i);
            sb.append(i + 1).append(". [").append(inst.getCategory()).append("] ");
            sb.append(inst.getContent()).append("\n");
        }
        return sb.toString().trim();
    }

    public List<PlayerInstruction> getInstructions(UUID uuid) {
        if (cache.containsKey(uuid)) {
            return new ArrayList<>(cache.get(uuid));
        }
        
        File file = getInstructionFile(uuid);
        if (!file.exists()) {
            return new ArrayList<>();
        }
        
        try {
            String json = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            List<PlayerInstruction> instructions = gson.fromJson(json, LIST_TYPE);
            if (instructions == null) {
                instructions = new ArrayList<>();
            }
            cache.put(uuid, new ArrayList<>(instructions));
            return new ArrayList<>(instructions);
        } catch (IOException | JsonSyntaxException e) {
            plugin.getLogger().warning("[Instruction] 读取玩家 " + uuid + " 的记忆失败: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    public String getInstructionsAsPrompt(UUID uuid) {
        List<PlayerInstruction> instructions = getInstructions(uuid);
        if (instructions.isEmpty()) {
            return null;
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("以下是玩家 ").append(getPlayerName(uuid)).append(" 的偏好和记忆，请在对话中参考这些信息。\n");
        sb.append("注意：如果记忆之间存在冲突，以列表中靠后的（较新的）记忆为准。\n");
        for (PlayerInstruction inst : instructions) {
            sb.append("- [").append(inst.getCategory()).append("] ").append(inst.getContent()).append("\n");
        }
        return sb.toString().trim();
    }

    private String getPlayerName(UUID uuid) {
        org.bukkit.OfflinePlayer offlinePlayer = plugin.getServer().getOfflinePlayer(uuid);
        return offlinePlayer.getName() != null ? offlinePlayer.getName() : uuid.toString().substring(0, 8);
    }

    private void saveInstructions(UUID uuid, List<PlayerInstruction> instructions) {
        cache.put(uuid, new ArrayList<>(instructions));
        
        File file = getInstructionFile(uuid);
        try {
            String json = gson.toJson(instructions);
            Files.write(file.toPath(), json.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            plugin.getLogger().warning("[Instruction] 保存玩家 " + uuid + " 的记忆失败: " + e.getMessage());
        }
    }

    private File getInstructionFile(UUID uuid) {
        return new File(instructionDir, uuid.toString() + ".json");
    }

    public void shutdown() {
        cache.clear();
    }
}
