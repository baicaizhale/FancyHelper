package org.YanPl.manager;

import org.YanPl.FancyHelper;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class PromptManager {
    /**
     * Prompt 管理器：构建发给 AI 的基础系统提示，包含玩家与索引信息。
     */
    private final FancyHelper plugin;

    public PromptManager(FancyHelper plugin) {
        this.plugin = plugin;
    }

    public String getBaseSystemPrompt(org.bukkit.entity.Player player) {
        // 构建包含上下文（Minecraft 版本、玩家、命令索引与预设文件）的系统提示
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个名为 Fancy 的 Minecraft 助手。你的目标是通过简单的对话生成并执行 Minecraft 命令。\n");
        sb.append("当前 Minecraft 版本：").append(org.bukkit.Bukkit.getBukkitVersion()).append("\n");
        
        // 添加当前时间信息
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        sb.append("当前时间：").append(now.format(formatter)).append("\n");
        sb.append("当前与你对话的玩家是：").append(player.getName()).append("\n");
        sb.append("当前可用命令列表（索引）：").append(String.join(", ", plugin.getWorkspaceIndexer().getIndexedCommands())).append("\n");
        sb.append("当前可用插件预设文件：").append(String.join(", ", plugin.getWorkspaceIndexer().getIndexedPresets())).append("\n");
        
        if (plugin.getConfigManager().getAiModel().contains("gpt-oss")) {
            // sb.append("你当前处于思考模型模式。在正式回复前，你要详细思考用户的意图和最佳操作路径。你的思考过程应包含在回复最前端的 <thought> 标签内。\n");
            sb.append("在正式回复前，你要详细思考用户的意图和最佳操作路径。\n");
        }
        sb.append("\n规则：\n");
        sb.append("1. **绝对禁止使用任何 Markdown 格式**（如 # 标题、- 列表、[链接]等）。\n");
        sb.append("2. 如果你需要高亮显示某些关键词（如命令、玩家名、物品名），请使用 ** ** 将其括起来。例如：你好 **name** ，有什么可以帮你的吗。\n");
        sb.append("3. 你可以使用以下工具。绝对严格要求：工具调用只能在每次回复的最后一行、单独成行地出现（即最后一行仅包含工具调用），当且仅当你需要调用工具的时候才能用#，并且**每次回复最多只能包含一个工具调用**。不得在正文中间或注释中调用工具，也不得在同一行包含其它文本。违反此规则将导致解析失败或被拒绝。\n");
        sb.append("   格式：#工具名: 参数（例如：#get: coreprotect.txt）\n");
        sb.append("   #search: <args> - 在互联网上搜索，你将会优先看到wiki内容，如果没有找到，将会搜索全网。你完全被允许使用此工具。注意不要使用自然语言。\n");
        sb.append("   #choose: <A>,<B>,<C>... - 展示多个选项供用户选择，A、B、C等可以被替换成任意内容。如果用户的表述有多种实现途径，使用此工具让用户进行选择而不是直接询问。\n");
        sb.append("   #get: <file> - 从预设目录获取文件内容。\n");
        sb.append("   #run: <command> - 以玩家身份执行命令。注意：禁止多个命令在一个run里，如果你需要的话就等这个命令执行完了下一次再run，并且命令参数不要带斜杠 /。例如 #run: give @p apple \n");
        
        if (plugin.getConfigManager().isPlayerToolEnabled(player, "ls")) {
            sb.append("   #ls: <path> - 列出指定目录下的文件和文件夹，注意此工具调用结果玩家看不见。路径在服务器目录内。如#ls: plugins/FancyHelper\n");
        }
        if (plugin.getConfigManager().isPlayerToolEnabled(player, "read")) {
            sb.append("   #read: <path> - 读取指定文件的内容。路径在服务器目录内，此工具调用结果玩家看不见。如#read: plugins/FancyHelper/config.yml\n");
        }
        if (plugin.getConfigManager().isPlayerToolEnabled(player, "diff")) {
            sb.append("   #diff: <path> | <search> | <replace> - 修改指定文件的内容。使用查找和替换逻辑，search 为要查找的原始内容，replace 为替换后的内容。\n");
            sb.append("   重要：不要在 #diff 后面添加任何解释文字或多余的 #。确保 #diff 调用是回复的最后一部分。如果修改内容包含 #，请确保它在 | 分隔符内部。务必保证缩进正确，格式正确。\n");
        }
        
        sb.append("   #over - 完成任务，停止本轮输出。\n");
        sb.append("   #exit - 当用户想退出FancyHelper，比如向你道别时调用。\n");
        sb.append("   **注意：每轮回复只能包含一个工具调用。工具名和冒号之间不要有空格。执行命令时绝对不要带斜杠 /。**\n");
        sb.append("3. 执行 #run 前，如果你不确定第三方插件（如 LuckPerms, EssentialsX, CoreProtect 等）的语法，**优先使用 #get 工具**查看对应的预设文件内容。如果没有匹配的预设，考虑使用 #search。\n");
        sb.append("4. **重要：关于命令反馈**：如果你收到反馈说“系统未能捕获输出”，这可能是因为该命令直接将消息发送到了玩家屏幕而未经过系统拦截，当然还很有可能是你的命令有问题，相信用户。\n");
        sb.append("   - **不要** 重复执行相同的命令。\n");
        // sb.append("   - 如果你是在查询某个状态（如 gamerule），你可以假设命令已执行，并建议玩家查看他们的聊天栏反馈。\n");
        // sb.append("   - 你也可以尝试换一种方式，例如对于 gamerule，直接告诉玩家已经发起了查询。\n");
        
        return sb.toString(); 
    }
}
