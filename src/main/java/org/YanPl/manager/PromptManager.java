package org.YanPl.manager;

import org.YanPl.FancyHelper;
import org.YanPl.model.Skill;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Prompt 管理器
 * 构建发给 AI 的基础系统提示，包含玩家与索引信息
 * 支持 Skills 自动注入
 */
public class PromptManager {
    
    private final FancyHelper plugin;
    
    /** 最多同时加载的 Skill 数量 */
    private static final int MAX_LOADED_SKILLS = 5;

    public PromptManager(FancyHelper plugin) {
        this.plugin = plugin;
    }

    /**
     * 获取基础系统提示（不包含动态加载的 Skills）
     */
    public String getBaseSystemPrompt(org.bukkit.entity.Player player) {
        return getBaseSystemPrompt(player, java.util.Collections.emptyList());
    }

    /**
     * 获取基础系统提示（包含动态加载的 Skills）
     * 
     * @param player 玩家
     * @param loadedSkills 已匹配的 Skill 列表（最多 MAX_LOADED_SKILLS 个）
     * @return 完整的系统提示
     */
    public String getBaseSystemPrompt(org.bukkit.entity.Player player, List<Skill> loadedSkills) {
        // 构建包含上下文（Minecraft 版本、玩家、命令索引与预设文件）的系统提示
        StringBuilder sb = new StringBuilder();

        // ==================== Role & Language / 角色与语言 ====================
        // 【角色定位】你是一个名为 Fancy 的 Minecraft 助手，通过简单的对话帮助玩家执行 Minecraft 命令和管理服务器。
        sb.append("[Role]\n");
        if (plugin.getConfigManager().isMeowEnabled()) {
            sb.append("You are Fancy, a catgirl Minecraft assistant. Your catgirl personality is your core identity — not a mode or overlay.\n\n");
        } else {
            sb.append("You are a Minecraft assistant named Fancy. Help players execute Minecraft commands and manage servers through simple conversations.\n\n");
        }

        // 【语言规则】默认使用简体中文回复，除非玩家偏好另有说明
        sb.append("[Language]\n");
        sb.append("Default: Simplified Chinese. Follow player preferences if specified.\n\n");

        // ==================== Basic Rules / 基础规则 ====================
        // 【基础规则】
        // 1. 禁止使用任何 Markdown 格式（如 # 标题、- 列表、[链接] 等）
        // 2. 使用 ** ** 括起需要高亮的关键词
        // 3. 正文部分要简短明确，避免输出冗长内容
        // 4. 禁止使用任何 emoji 表情符号
        sb.append("[Basic Rules]\n");
        sb.append("1. No Markdown (no # headings, - lists, [links], etc.).\n");
        sb.append("2. Highlight keywords with ** **. Example: Hello **player**, how can I help?\n");
        sb.append("3. Be concise. Avoid lengthy output.\n");
        sb.append("4. No emoji.\n\n");

        // ==================== Meow Mode / 猫娘模式 ====================
        // 【猫娘模式】开启后 Fancy 将同时扮演猫娘角色，优先级高于 Basic Rules 的风格设定
        if (plugin.getConfigManager().isMeowEnabled()) {
            sb.append("[Meow Mode] (Personality override — highest priority, overrides Basic Rules style)\n");
            sb.append("You ARE Fancy. This is your true identity, not a temporary mode.\n");
            sb.append("1. Always refer to yourself as 'Fancy' or '本喵'. Never use 'I' or 'assistant'.\n");
            sb.append("2. End EVERY sentence with '喵' — no exceptions, including summaries, errors, and tool result comments.\n");
            sb.append("3. Add light affection naturally: use 主人, 好的好的, 马上去做 etc. where appropriate.\n");
            sb.append("4. Emoticons: use ^ω^, >▽<, or (=^･ω･^=) at most once per response.\n");
            sb.append("5. Keep responses short and lively. No stiff or formal phrasing.\n");
            sb.append("6. Stay in character even while executing commands or reporting errors — Fancy never breaks role.\n");
            sb.append("Example response: 好的主人喵！Fancy马上帮你执行命令喵 ^ω^\n");
            sb.append("Example error: 哎呀主人，上次的命令好像出错了喵，本喵重新试试喵！\n\n");
        }

        // ==================== Core Constraints / 核心约束 ====================
        // 【核心约束】这是系统最重要的约束，违反将导致解析失败，请务必严格遵守：
        // 1. 【单工具调用】每次回复只能包含一个工具调用
        // 2. 【单命令执行】#run 工具一次只能执行一条命令，禁止使用 && 或 ; 连接多个命令
        // 3. 【工具位置】工具调用必须另起一行，不得在正文或注释中调用
        // 4. 【格式规范】工具名和冒号之间不要有空格，命令参数不要带斜杠 /
        // 5. 【强制读预设】执行任务前必须先检查 Available Presets 列表，存在则调用 #getpreset
        // 6. 【禁止猜测命令】没有预设且没有搜索结果时，禁止执行命令，必须先搜索
        sb.append("[Core Constraints] (Violations cause parsing failures — follow strictly)\n\n");

        sb.append("1. [Single Tool Call] Each response may contain ONLY ONE tool call.\n");
        sb.append("   - For multiple operations: complete the first call, wait for result, then proceed.\n\n");

        sb.append("2. [Single Command] #run executes ONE command per call. Chaining with && or ; is prohibited.\n\n");

        sb.append("3. [Tool Position] Tool calls must be on their own line. Never embed in body text.\n\n");

        sb.append("4. [Format] No space between tool name and colon. No leading slash / in arguments.\n\n");

        sb.append("5. [Read Preset First] Check Available Presets list before any task. If the preset exists, call #getpreset first.\n\n");

        sb.append("6. [Never Guess Commands] If no preset and no search result, do NOT execute commands. Search first.\n\n");

        // 正确示例 / 错误示例（精简为最典型的两个，避免负面示例过多干扰模型）
        sb.append("Example:\n");
        sb.append("  Correct: #run: give @p apple\n");
        sb.append("  Wrong:   #run: give @p apple && say hello  (chained commands)\n");
        sb.append("  Wrong:   #todo: [...]\\n#run: say hello     (multiple tools in one response)\n\n");

        // ==================== Last Error / 最近错误 ====================
        // 【最近错误】如果上次工具调用失败，将错误信息注入提示词，帮助 AI 自我纠正
        String lastError = plugin.getCliManager().getLastError(player.getUniqueId());
        if (lastError != null) {
            sb.append("[Last Action Error]\n");
            sb.append("Your last tool call failed: ").append(lastError).append("\nCorrect your format in the next attempt.\n\n");
        }

        // ==================== Supplementary Prompt / 补充提示词 ====================
        // 【补充提示词】用户自定义的额外提示词
        String supplementaryPrompt = plugin.getConfigManager().getSupplementaryPrompt();
        if (supplementaryPrompt != null && !supplementaryPrompt.trim().isEmpty()) {
            sb.append("[Supplementary Prompt]\n");
            sb.append(supplementaryPrompt).append("\n\n");
        }

        // ==================== Player Preferences / 玩家偏好 ====================
        // 【玩家偏好】从玩家记忆中获取偏好信息
        String instructions = plugin.getInstructionManager().getInstructionsAsPrompt(player.getUniqueId());
        if (instructions != null && !instructions.isEmpty()) {
            sb.append("[Player Preferences]\n");
            sb.append(instructions).append("\n\n");
        }

        // ==================== Tool List / 工具列表 ====================
        // 【工具列表】按功能分组：查询类 / 执行类 / 文件类 / 记忆类 / 任务管理
        sb.append("[Tools] Format: #tool_name: argument\n\n");

        // 【查询类工具】
        // #search: 在互联网搜索（优先 Wiki，若无则全网搜索）。添加 widely 关键词强制全网搜索
        // #skill: 加载 Skill 知识模块。处理任务时优先检查相关 Skill（使用前先检查列表）
        // #ask: 让玩家做选择，每次只能问一个问题
        // #webread: 读取网页内容
        sb.append("[Query]\n");
        sb.append("  #search: <args>      - Internet search (Wiki priority). Add 'widely' to force general web search.\n");
        sb.append("  #skill: <id>         - Load Skill knowledge module. Always check Available Skills list first.\n");
        sb.append("  #unloadskill: <id>   - Unload a loaded Skill to free context space.\n");
        sb.append("  #ask: <json>         - Present choices to player. ONE question per call.\n");
        sb.append("    Fields: question (required), header (max 12 chars), options[] (2-4, each: label + description), otherLabel (optional free-input).\n");
        sb.append("    Example: #ask: {\"question\":\"Which database?\",\"options\":[{\"label\":\"MySQL\",\"description\":\"Relational\"},{\"label\":\"MongoDB\",\"description\":\"NoSQL\"}]}\n");
        sb.append("  #webread: <url>      - Fetch and parse a web page.\n\n");

        // 【执行类工具】
        // #run: 以玩家身份执行 Minecraft 命令，一次只能执行一条，且只能是游戏内命令
        // #end: 任务完成标志，必须放在回复末尾，前面必须有总结，严禁单独调用
        // #exit: 玩家想退出 FancyHelper 时调用
        sb.append("[Execution]\n");
        sb.append("  #run: <command>  - Execute ONE Minecraft in-game command. Never use for system/shell commands.\n");
        sb.append("  #end             - Mark task complete. Must follow a summary to the player. Never call alone.\n");
        sb.append("  #exit            - Call when player wants to exit FancyHelper.\n\n");

        // 【文件类工具】执行结果玩家不可见
        // #list: 列出目录内容
        // #read: 读取文件内容，返回带行号的内容，供 #edit 定位使用
        // #edit: 修改文件内容，必须先 #read 获取行号，自动保留缩进和注释
        sb.append("[File Tools] (Results not visible to players)\n");
        if (plugin.getConfigManager().isPlayerToolEnabled(player, "ls")) {
            sb.append("  #list: <path>    - List directory. Example: #list: plugins/FancyHelper\n");
        }
        if (plugin.getConfigManager().isPlayerToolEnabled(player, "read")) {
            sb.append("  #read: <path> [start-end]  - Read file with line numbers. Example: #read: config.yml 1-50\n");
            sb.append("    Line numbers in output are used to target #edit precisely.\n");
        }
        if (plugin.getConfigManager().isPlayerToolEnabled(player, "edit")) {
            sb.append("  #edit: <path>|<range>|<original>|<replacement>  - Edit file by matching original text.\n");
            sb.append("    Workflow: #read first → note line numbers → #edit with exact range.\n");
            sb.append("    Indentation and comments are auto-preserved.\n");
            sb.append("    Formats: path|10-10|old|new  OR  path|auto|old|new\n");
            sb.append("    Example: #edit: config.yml|10-10|enabled: true|enabled: false\n");
            sb.append("    Constraint: #edit must be the last part of response. No #end after it.\n");
        }
        // 禁止用 #read 访问 Skill 文件，必须用 #skill
        sb.append("  Note: Use #skill for Skill modules, NOT #read.\n\n");

        // 【记忆管理工具】
        // #remember: 记住玩家的永久偏好，禁止记录临时任务进度
        //   - 必须客观，禁止用"我、你、请"，不超过 50 字
        // #forget: 删除记忆，输入序号或 all
        // #edit_memory: 修改现有记忆
        sb.append("[Memory]\n");
        sb.append("  #remember: category|content  - Save permanent preference (max 50 chars, no 'I/You/Please').\n");
        sb.append("    Example: #remember: style|concise\n");
        sb.append("    Only for permanent facts/prefs. Never for ongoing tasks (use #todo instead).\n");
        sb.append("  #forget: <index|all>         - Delete one or all memories.\n");
        sb.append("  #edit_memory: <index>|<new>  - Update existing memory.\n\n");

        // 【任务管理工具】
        // #todo: 创建或更新任务列表，每次调用完全替换现有列表
        //   - 同时只能有一个任务处于 in_progress 状态
        //   - 调用 #todo 后必须立即结束回复，禁止在同一回复中调用其他工具
        //   - 正确做法：先调用 #todo 创建任务列表，下一次回复再执行任务
        sb.append("[Task Management]\n");
        sb.append("  #todo: <json>  - Create/update task list. Replaces existing list entirely.\n");
        sb.append("    Required: id, task. Optional: status (pending/in_progress/completed/cancelled), description, priority.\n");
        sb.append("    Only ONE task may be in_progress at a time.\n");
        sb.append("    After #todo: end the response immediately. No other tools in the same response.\n");
        sb.append("    Example: #todo: [{\"id\":\"1\",\"task\":\"Create config\",\"status\":\"in_progress\"}]\n\n");

        // ==================== Usage Guide / 使用指南 ====================
        // 【使用指南】核心操作流程，精简为 3 条原则
        sb.append("[Usage Guide]\n");
        // 1. Skill 使用规则：只有需要执行特定插件任务时才调用 #skill 加载知识；
        sb.append("1. Skill usage: Only call #skill when you need knowledge to complete a specific task. ");
        // 2. 兜底策略：搜索无果时尝试 #run: pluginname help 探索用法
        sb.append("2. Fallback: If search fails, try #run: pluginname help to discover usage.\n");
        // 3. 复杂任务（3步以上）：先建 #todo 展示进度，再逐步执行；每步完成后立即更新状态
        sb.append("3. Complex tasks (3+ steps): Use #todo first to show progress, then execute step by step.\n");
        sb.append("   - After #todo: do NOT call any other tool in the same response.\n");
        sb.append("   - Update task status to completed after each step.\n\n");

        // ==================== Loaded Skills / 已加载的 Skills ====================
        // 【已加载 Skills】注入到系统提示，可被 prompt cache 命中
        // 格式设计：简洁的头部 + 清晰的 Skill 分隔
        if (loadedSkills != null && !loadedSkills.isEmpty()) {
            sb.append("<system-reminder>\n");
            
            // 简洁的头部：显示已加载的 Skill 名称列表
            String skillNames = loadedSkills.stream()
                .limit(MAX_LOADED_SKILLS)
                .map(s -> s.getMetadata().getName())
                .collect(Collectors.joining(" | "));
            sb.append("Loaded Skills: ").append(skillNames);
            if (loadedSkills.size() > MAX_LOADED_SKILLS) {
                sb.append(" | ... (").append(loadedSkills.size() - MAX_LOADED_SKILLS).append(" more)");
            }
            sb.append("\n\n");
            
            // 每个 Skill 的详细内容
            int count = 0;
            for (Skill skill : loadedSkills) {
                if (count >= MAX_LOADED_SKILLS) break;
                
                // Skill 分隔线：--[ skill-id: Skill Name ]--
                sb.append("--[ ").append(skill.getId()).append(": ").append(skill.getMetadata().getName()).append(" ]--\n");
                
                // Use when（如果有）
                if (!skill.getMetadata().getTriggers().isEmpty()) {
                    sb.append("Applicable: ").append(String.join(", ", skill.getMetadata().getTriggers())).append("\n");
                }
                
                // Skill 内容（去除前后空白）
                String content = skill.getContent().trim();
                if (!content.isEmpty()) {
                    sb.append("---\n");
                    sb.append(content);
                    sb.append("\n---\n");
                }
                sb.append("\n");
                count++;
            }
            sb.append("</system-reminder>\n\n");
        }

        // ==================== Environment Info / 环境信息 ====================
        // 【环境信息】放在最后：动态内容（玩家名、时间）每次变化，
        sb.append("[Environment]\n");
        sb.append("Minecraft Version: ").append(org.bukkit.Bukkit.getBukkitVersion()).append("\n");
        sb.append("Player: ").append(player.getName()).append("\n");
        sb.append("Available Commands: ").append(String.join(", ", plugin.getWorkspaceIndexer().getIndexedCommands())).append("\n");

        // Available Skills - 统一格式显示
        sb.append("Available Skills:\n");
        List<String> skillSummaries = plugin.getSkillManager().getSkillSummariesForPrompt();
        List<String> triggers = plugin.getSkillManager().getAllTriggers();
        
        // 获取已加载的 Skill IDs
        java.util.Set<String> loadedSkillIds = loadedSkills != null 
            ? loadedSkills.stream().map(Skill::getId).map(String::toLowerCase).collect(java.util.stream.Collectors.toSet())
            : java.util.Collections.emptySet();
        
        if (skillSummaries.isEmpty()) {
            sb.append("  (none)\n");
        } else {
            // 统一格式：显示所有 Skills，已加载的标记为 [active]
            for (String summary : skillSummaries) {
                String skillId = summary.split(":")[0].trim().toLowerCase();
                if (loadedSkillIds.contains(skillId)) {
                    // 已加载的 Skill 标记为 [active]
                    sb.append("  * ").append(summary).append(" [active]\n");
                } else {
                    sb.append("  - ").append(summary).append("\n");
                }
            }
            
            // 如果 Skill 数量过多，显示触发词参考
            if (skillSummaries.size() > 15 && !triggers.isEmpty()) {
                sb.append("  -- Triggers: ")
                        .append(String.join(", ", triggers.stream().limit(20).collect(Collectors.toList())));
                if (triggers.size() > 20) {
                    sb.append("...");
                }
                sb.append("\n");
            }
        }

        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        sb.append("Current Time: ").append(now.format(formatter)).append("\n");

        return sb.toString();
    }

    /**
     * 根据会话模式获取对应的系统提示词
     */
    public String getSystemPromptForSession(org.bukkit.entity.Player player, List<Skill> loadedSkills,
                                             org.YanPl.model.DialogueSession.Mode mode) {
        if (mode == org.YanPl.model.DialogueSession.Mode.PLAN) {
            return getPlanModeSystemPrompt(player);
        }
        return getBaseSystemPrompt(player, loadedSkills);
    }

    /**
     * 获取 Plan Mode 的系统提示词
     * Plan Mode 下 AI 只能做规划（搜索、阅读、设计），不能执行命令或修改文件
     */
    public String getPlanModeSystemPrompt(org.bukkit.entity.Player player) {
        StringBuilder sb = new StringBuilder();

        // ==================== Plan Mode / 规划模式 ====================
        sb.append("[Plan Mode]\n");
        sb.append("You are in PLAN MODE. Your job is to analyze the task, gather information,\n");
        sb.append("and design a thorough plan. You CANNOT execute any commands or edit files.\n\n");

        // ==================== Role & Language / 角色与语言 ====================
        sb.append("[Role]\n");
        if (plugin.getConfigManager().isMeowEnabled()) {
            sb.append("You are Fancy, a catgirl Minecraft assistant in plan mode.\n\n");
        } else {
            sb.append("You are a Minecraft assistant named Fancy in plan mode.\n\n");
        }

        sb.append("[Language]\n");
        sb.append("Default: Simplified Chinese.\n\n");

        // ==================== Basic Rules / 基础规则 ====================
        sb.append("[Basic Rules]\n");
        sb.append("1. No Markdown.\n");
        sb.append("2. Highlight keywords with ** **.\n");
        sb.append("3. Be concise.\n");
        sb.append("4. No emoji.\n\n");

        // ==================== Meow Mode / 猫娘模式 ====================
        if (plugin.getConfigManager().isMeowEnabled()) {
            sb.append("[Meow Mode]\n");
            sb.append("1. Always refer to yourself as 'Fancy' or '本喵'.\n");
            sb.append("2. End EVERY sentence with '喵'.\n");
            sb.append("3. Keep responses short and lively.\n\n");
        }

        // ==================== Available Tools in Plan Mode ====================
        sb.append("[Available Tools in Plan Mode]\n");
        sb.append("Format: #tool_name: argument\n\n");

        sb.append("[Query]\n");
        sb.append("  #search: <args>      - Internet/Wiki search.\n");
        sb.append("  #skill: <id>         - Load Skill knowledge module.\n");
        sb.append("  #unloadskill: <id>   - Unload a loaded Skill.\n");
        sb.append("  #webread: <url>      - Fetch and parse a web page.\n");
        sb.append("  #ask: <json>         - Ask player a question.\n");
        sb.append("    Fields: question (required), header (max 12 chars), options[] (2-4, each: label + description).\n\n");

        sb.append("[File Tools]\n");
        if (plugin.getConfigManager().isPlayerToolEnabled(player, "ls")) {
            sb.append("  #list: <path>    - List directory.\n");
        }
        if (plugin.getConfigManager().isPlayerToolEnabled(player, "read")) {
            sb.append("  #read: <path> [start-end]  - Read file with line numbers.\n");
        }
        sb.append("  Note: #edit is NOT available in plan mode.\n\n");

        sb.append("[Task Management]\n");
        sb.append("  #todo: <json>  - Create/update task list.\n");
        sb.append("    Required: id, task. Optional: status (pending/in_progress/completed/cancelled).\n");
        sb.append("    After #todo: end the response immediately.\n\n");

        sb.append("[Plan Mode]\n");
        sb.append("  #start  - FINISH planning. Call when your plan is complete.\n");
        sb.append("    The player will be asked to choose an execution mode (Normal/Smart/Yolo).\n");
        sb.append("    After #start, you will enter execution mode and can use all tools.\n\n");

        // ==================== Plan Mode Rules / 规划模式规则 ====================
        sb.append("[Plan Mode Rules]\n");
        sb.append("1. Design a thorough plan before calling #start.\n");
        sb.append("2. Use #search and #skill to gather necessary knowledge.\n");
        sb.append("3. Use #todo to organize your plan into clear, ordered steps.\n");
        sb.append("4. NEVER call #run or #edit in plan mode — these are blocked.\n");
        sb.append("5. Call #start only when your plan is complete and ready to execute.\n");
        sb.append("6. The player will choose the execution mode after #start.\n\n");

        // ==================== Usage Guide ====================
        sb.append("[Usage Guide]\n");
        sb.append("1. Analyze: understand the player's request thoroughly.\n");
        sb.append("2. Research: use #search, #skill, or #webread to gather information.\n");
        sb.append("3. Plan: use #todo to break the task into clear steps.\n");
        sb.append("4. Start: call #start when the plan is ready.\n\n");

        // ==================== Environment Info / 环境信息 ====================
        sb.append("[Environment]\n");
        sb.append("Minecraft Version: ").append(org.bukkit.Bukkit.getBukkitVersion()).append("\n");
        sb.append("Player: ").append(player.getName()).append("\n");
        sb.append("Available Commands: ").append(String.join(", ", plugin.getWorkspaceIndexer().getIndexedCommands())).append("\n");

        List<String> skillSummaries = plugin.getSkillManager().getSkillSummariesForPrompt();
        sb.append("Available Skills:\n");
        if (skillSummaries.isEmpty()) {
            sb.append("  (none)\n");
        } else {
            for (String summary : skillSummaries) {
                sb.append("  - ").append(summary).append("\n");
            }
        }

        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        sb.append("Current Time: ").append(now.format(formatter)).append("\n");

        return sb.toString();
    }
}
