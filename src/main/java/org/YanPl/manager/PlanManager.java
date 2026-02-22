package org.YanPl.manager;

import org.YanPl.FancyHelper;
import org.YanPl.model.*;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 计划模式管理器
 * 
 * 负责管理计划模式的完整生命周期：
 * 1. 进入/退出计划模式
 * 2. 问题管理（提问、回答）
 * 3. 计划生成和展示
 * 4. 计划修改（Inventory GUI）
 * 5. 计划执行（YOLO/普通模式）
 */
public class PlanManager {
    private final FancyHelper plugin;
    private final CLIManager cliManager;
    private final ToolExecutor toolExecutor;
    
    // 玩家状态映射
    private final Map<UUID, List<Question>> questionsMap = new ConcurrentHashMap<>();
    private final Map<UUID, ExecutionPlan> plansMap = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> questionIndexMap = new ConcurrentHashMap<>();

    public PlanManager(FancyHelper plugin, CLIManager cliManager, ToolExecutor toolExecutor) {
        this.plugin = plugin;
        this.cliManager = cliManager;
        this.toolExecutor = toolExecutor;
    }

    // ===== 计划模式入口 =====

    /**
     * 进入计划模式
     * 
     * @param player 玩家
     * @deprecated 使用 CLIManager.switchMode() 切换到 PLAN 模式
     */
    @Deprecated
    public void enterPlanMode(Player player) {
        cliManager.switchMode(player, DialogueSession.Mode.PLAN);
    }

    /**
     * 退出计划模式
     * 
     * @param player 玩家
     * @deprecated 使用 CLIManager.switchMode() 切换到 NORMAL 模式
     */
    @Deprecated
    public void exitPlanMode(Player player) {
        UUID uuid = player.getUniqueId();
        DialogueSession session = cliManager.getSession(uuid);
        
        if (session != null) {
            session.setPlanQuestions(new ArrayList<>());
            session.setCurrentPlan(null);
        }
        
        questionsMap.remove(uuid);
        plansMap.remove(uuid);
        questionIndexMap.remove(uuid);
        
        player.sendMessage(ChatColor.GRAY + "» " + ChatColor.WHITE + "已退出计划模式");
    }

    // ===== 问题管理 =====

    /**
     * 设置问题列表（由AI调用）
     * 
     * @param player 玩家
     * @param questions 问题列表
     */
    public void setQuestions(Player player, List<Question> questions) {
        UUID uuid = player.getUniqueId();
        questionsMap.put(uuid, questions);
        questionIndexMap.put(uuid, 0);
        
        DialogueSession session = cliManager.getSession(uuid);
        if (session != null) {
            session.setPlanQuestions(questions);
            session.setCurrentQuestionIndex(0);
        }
        
        // 显示第一个问题
        displayCurrentQuestion(player);
    }

    /**
     * 显示当前问题
     * 
     * @param player 玩家
     */
    private void displayCurrentQuestion(Player player) {
        UUID uuid = player.getUniqueId();
        List<Question> questions = questionsMap.get(uuid);
        Integer currentIndex = questionIndexMap.get(uuid);

        if (questions == null || currentIndex == null || currentIndex >= questions.size()) {
            // 所有问题已回答，通知AI生成计划
            notifyAllQuestionsAnswered(player);
            return;
        }

        Question question = questions.get(currentIndex);

        // 根据问题类型显示输入提示
        switch (question.getType()) {
            case TEXT:
                sendTextInputPrompt(player, question.getText());
                break;
            case CHECKBOX:
                sendOptionPrompt(player, question);
                break;
            case RADIO:
                sendOptionPrompt(player, question);
                break;
        }
    }

    /**
     * 发送文本输入提示
     *
     * @param player 玩家
     * @param prompt 提示文本
     */
    private void sendTextInputPrompt(Player player, String prompt) {
        // 显示问题提示
        player.sendMessage(ChatColor.GRAY + "⨀ [ " + ChatColor.WHITE + prompt + ChatColor.GRAY + " ]");
        player.sendMessage(ChatColor.GRAY + "  » " + ChatColor.WHITE + "请直接在聊天框输入您的答案");
    }

    /**
     * 发送选项提示（单选或多选）
     *
     * @param player 玩家
     * @param question 问题对象
     */
    private void sendOptionPrompt(Player player, Question question) {
        // 显示问题提示
        player.sendMessage(ChatColor.GRAY + "⨀ [ " + ChatColor.WHITE + question.getText() + ChatColor.GRAY + " ]");

        if (question.getOptions() == null || question.getOptions().isEmpty()) {
            player.sendMessage(ChatColor.RED + "  » 无可用选项");
            return;
        }

        // 显示选项按钮
        for (int i = 0; i < question.getOptions().size(); i++) {
            String option = question.getOptions().get(i);
            TextComponent optionBtn = new TextComponent(ChatColor.GRAY + "  [");
            TextComponent optionText = new TextComponent(ChatColor.WHITE + String.valueOf(i + 1) + ". " + option);
            TextComponent btnEnd = new TextComponent(ChatColor.GRAY + "]");

            TextComponent fullBtn = new TextComponent("");
            fullBtn.addExtra(optionBtn);
            fullBtn.addExtra(optionText);
            fullBtn.addExtra(btnEnd);

            // 设置点击事件
            String answer = String.valueOf(i + 1);
            fullBtn.setClickEvent(new ClickEvent(
                ClickEvent.Action.RUN_COMMAND,
                "/cli answer " + answer
            ));

            // 设置悬停提示
            fullBtn.setHoverEvent(new HoverEvent(
                HoverEvent.Action.SHOW_TEXT,
                new Text(ChatColor.GRAY + "点击选择此选项")
            ));

            player.spigot().sendMessage(fullBtn);
        }

        // 显示多选提示
        if (question.getType() == Question.QuestionType.CHECKBOX) {
            player.sendMessage(ChatColor.GRAY + "  » " + ChatColor.WHITE + "可多选多个选项，输入数字用逗号分隔（如：1,2,3）");
        } else {
            player.sendMessage(ChatColor.GRAY + "  » " + ChatColor.WHITE + "请选择一个选项，输入对应的数字");
        }
    }

    /**
     * 发送输入提示（使用聊天栏按钮）
     *
     * @param player 玩家
     * @param prompt 提示文本
     */
    private void sendInputPrompt(Player player, String prompt) {
        // 显示问题提示
        player.sendMessage(ChatColor.GRAY + "⨀ [ " + ChatColor.WHITE + prompt + ChatColor.GRAY + " ]");
        player.sendMessage(ChatColor.GRAY + "  » " + ChatColor.WHITE + "请直接在聊天框输入您的答案");
    }

    /**
     * 处理用户答案
     * 
     * @param player 玩家
     * @param answer 答案
     */
    public void handleAnswer(Player player, String answer) {
        UUID uuid = player.getUniqueId();
        List<Question> questions = questionsMap.get(uuid);
        Integer currentIndex = questionIndexMap.get(uuid);
        
        if (questions == null || currentIndex == null || currentIndex >= questions.size()) {
            return;
        }
        
        Question question = questions.get(currentIndex);
        question.setAnswer(answer);
        
        player.sendMessage(ChatColor.GREEN + "✓ " + ChatColor.WHITE + "已记录答案");
        
        // 移动到下一个问题
        questionIndexMap.put(uuid, currentIndex + 1);
        
        DialogueSession session = cliManager.getSession(uuid);
        if (session != null) {
            session.setCurrentQuestionIndex(currentIndex + 1);
        }
        
        // 显示下一个问题
        displayCurrentQuestion(player);
    }

    /**
     * 通知AI所有问题已回答
     * 
     * @param player 玩家
     */
    private void notifyAllQuestionsAnswered(Player player) {
        player.sendMessage(ChatColor.GRAY + "» " + ChatColor.WHITE + "正在生成执行计划...");
        
        // 收集所有问答对
        UUID uuid = player.getUniqueId();
        List<Question> questions = questionsMap.get(uuid);
        StringBuilder qaString = new StringBuilder();
        
        for (Question q : questions) {
            qaString.append("问题: ").append(q.getText()).append("\n");
            qaString.append("答案: ").append(q.getAnswer()).append("\n\n");
        }
        
        // 反馈给AI
        cliManager.feedbackToAI(player, "#questions_answered: " + qaString.toString());
    }

    // ===== 计划管理 =====

    /**
     * 创建执行计划（由AI调用）
     * 
     * @param player 玩家
     * @param plan 执行计划
     */
    public void createPlan(Player player, ExecutionPlan plan) {
        UUID uuid = player.getUniqueId();
        plansMap.put(uuid, plan);

        DialogueSession session = cliManager.getSession(uuid);
        if (session != null) {
            session.setCurrentPlan(plan);
        }

        // 在聊天栏显示打开计划的按钮
        sendPlanBookButton(player);

        // 显示执行选项
        sendExecutionOptions(player);
    }

    /**
     * 发送打开计划Book的按钮
     *
     * @param player 玩家
     */
    private void sendPlanBookButton(Player player) {
        player.sendMessage(ChatColor.GREEN + "✓ " + ChatColor.WHITE + "计划已生成！");

        TextComponent message = new TextComponent(ChatColor.GRAY + "  » " + ChatColor.WHITE + "点击下方按钮查看计划详情\n");

        TextComponent bookBtn = new TextComponent(ChatColor.AQUA + "  [");
        TextComponent bookText = new TextComponent(ChatColor.BOLD + " 查看计划");
        TextComponent btnEnd = new TextComponent(ChatColor.AQUA + "]");

        TextComponent fullBtn = new TextComponent("");
        fullBtn.addExtra(bookBtn);
        fullBtn.addExtra(bookText);
        fullBtn.addExtra(btnEnd);

        fullBtn.setClickEvent(new ClickEvent(
            ClickEvent.Action.RUN_COMMAND,
            "/cli view_plan"
        ));

        fullBtn.setHoverEvent(new HoverEvent(
            HoverEvent.Action.SHOW_TEXT,
            new Text(ChatColor.GRAY + "点击打开计划书")
        ));

        player.spigot().sendMessage(message);
        player.spigot().sendMessage(fullBtn);
    }

    /**
     * 显示计划Book
     *
     * @param player 玩家
     */
    public void displayPlanBook(Player player) {
        ExecutionPlan plan = plansMap.get(player.getUniqueId());
        if (plan == null) return;
        
        ItemStack book = createPlanBook(plan);
        player.openBook(book);
    }

    /**
     * 创建计划Book
     * 
     * @param plan 执行计划
     * @return Book ItemStack
     */
    private ItemStack createPlanBook(ExecutionPlan plan) {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        
        if (meta != null) {
            meta.setTitle("执行计划");
            meta.setAuthor("FancyHelper");
            
            final int MAX_PAGE_CHARS = 128;
            List<String> pages = new ArrayList<>();
            
            // 第1页：封面
            StringBuilder coverPage = new StringBuilder();
            coverPage.append(ChatColor.DARK_GREEN).append(ChatColor.BOLD)
                    .append("执行计划\n\n");
            coverPage.append(ChatColor.DARK_BLUE).append(plan.getTitle()).append("\n\n");
            coverPage.append(ChatColor.GRAY).append("预计步骤: ").append(ChatColor.AQUA)
                    .append(plan.getSteps().size()).append(" 步\n");
            coverPage.append(ChatColor.GRAY).append("描述: ").append(ChatColor.DARK_PURPLE)
                    .append(plan.getDescription()).append("\n\n");
            coverPage.append(ChatColor.GRAY).append("翻页查看详细步骤");
            
            pages.add(ChatColor.RESET.toString() + coverPage.toString());
            
            // 步骤详情页
            for (int i = 0; i < plan.getSteps().size(); i++) {
                PlanStep step = plan.getSteps().get(i);
                StringBuilder stepPage = new StringBuilder();
                
                // 步骤标题
                stepPage.append(ChatColor.DARK_BLUE).append(ChatColor.BOLD)
                        .append("步骤 ").append(i + 1).append("/").append(plan.getSteps().size())
                        .append("\n\n");
                
                // 步骤描述
                String description = step.getDescription();
                
                if (description != null && !description.isEmpty()) {
                    stepPage.append(ChatColor.BLACK).append(description)
                            .append("\n\n");
                } else {
                    stepPage.append(ChatColor.DARK_GRAY).append("(无描述)")
                            .append("\n\n");
                }
                
                // 备注
                if (step.getNotes() != null && !step.getNotes().isEmpty()) {
                    stepPage.append(ChatColor.GRAY).append("备注: ").append(ChatColor.DARK_GRAY)
                            .append(step.getNotes());
                }
                
                // 智能分页
                String content = ChatColor.RESET.toString() + stepPage.toString();
                while (content.length() > MAX_PAGE_CHARS) {
                    pages.add(content.substring(0, MAX_PAGE_CHARS));
                    content = content.substring(MAX_PAGE_CHARS);
                }
                if (content.length() > 0) {
                    pages.add(content);
                }
            }

            meta.setPages(pages);
            book.setItemMeta(meta);
        }
        
        return book;
    }

    /**
     * 发送执行选项
     * 
     * @param player 玩家
     */
    private void sendExecutionOptions(Player player) {
        TextComponent message = new TextComponent(ChatColor.GREEN + "✓ " + ChatColor.WHITE + "计划已生成！请选择执行模式：\n");
        
        TextComponent yoloBtn = new TextComponent(ChatColor.GREEN + "[ YOLO模式 ]");
        yoloBtn.setClickEvent(new ClickEvent(
            ClickEvent.Action.RUN_COMMAND,
            "/cli approve yolo"
        ));
        yoloBtn.setHoverEvent(new HoverEvent(
            HoverEvent.Action.SHOW_TEXT,
            new Text(ChatColor.GREEN + "AI自动生成命令并执行")
        ));
        
        TextComponent normalBtn = new TextComponent(ChatColor.YELLOW +("[ 普通模式 ]"));
        normalBtn.setClickEvent(new ClickEvent(
            ClickEvent.Action.RUN_COMMAND,
            "/cli approve normal"
        ));
        normalBtn.setHoverEvent(new HoverEvent(
            HoverEvent.Action.SHOW_TEXT,
            new Text(ChatColor.YELLOW + "每步需要确认")
        ));
        
        TextComponent modifyBtn = new TextComponent(ChatColor.GRAY +("[ 手动修改 ]"));
        modifyBtn.setClickEvent(new ClickEvent(
            ClickEvent.Action.RUN_COMMAND,
            "/cli plan modify"
        ));
        modifyBtn.setHoverEvent(new HoverEvent(
            HoverEvent.Action.SHOW_TEXT,
            new Text(ChatColor.GRAY + "修改计划内容")
        ));
        
        TextComponent continueBtn = new TextComponent(ChatColor.AQUA +("[ 继续提问 ]"));
        continueBtn.setClickEvent(new ClickEvent(
            ClickEvent.Action.RUN_COMMAND,
            "/cli plan continue"
        ));
        continueBtn.setHoverEvent(new HoverEvent(
            HoverEvent.Action.SHOW_TEXT,
            new Text(ChatColor.AQUA + "让AI补充更多信息")
        ));
        
        TextComponent cancelBtn = new TextComponent(ChatColor.RED +("[ 取消 ]"));
        cancelBtn.setClickEvent(new ClickEvent(
            ClickEvent.Action.RUN_COMMAND,
            "/cli plan cancel"
        ));
        cancelBtn.setHoverEvent(new HoverEvent(
            HoverEvent.Action.SHOW_TEXT,
            new Text(ChatColor.RED + "放弃本次计划")
        ));
        
        TextComponent line = new TextComponent("  ");
        line.addExtra(yoloBtn);
        line.addExtra(ChatColor.GRAY + "  ");
        line.addExtra(normalBtn);
        line.addExtra(ChatColor.GRAY + "  ");
        line.addExtra(modifyBtn);
        line.addExtra(ChatColor.GRAY + "  ");
        line.addExtra(continueBtn);
        line.addExtra(ChatColor.GRAY + "  ");
        line.addExtra(cancelBtn);
        
        player.spigot().sendMessage(message);
        player.spigot().sendMessage(line);
    }

    // ===== 计划修改 =====

    /**
     * 打开计划修改GUI
     * 
     * @param player 玩家
     */
    public void openPlanEditGUI(Player player) {
        ExecutionPlan plan = plansMap.get(player.getUniqueId());
        if (plan == null) {
            player.sendMessage(ChatColor.RED + "没有可编辑的计划");
            return;
        }
        
        player.sendMessage(ChatColor.GRAY + "» " + ChatColor.WHITE + "计划编辑功能开发中...");
        // TODO: 实现Inventory GUI
    }

    /**
     * 修改步骤描述
     * 
     * @param player 玩家
     * @param stepIndex 步骤索引
     * @param newDescription 新描述
     */
    public void modifyStep(Player player, int stepIndex, String newDescription) {
        ExecutionPlan plan = plansMap.get(player.getUniqueId());
        if (plan == null || stepIndex < 0 || stepIndex >= plan.getSteps().size()) {
            player.sendMessage(ChatColor.RED + "无效的步骤索引");
            return;
        }
        
        PlanStep step = plan.getSteps().get(stepIndex);
        step.setDescription(newDescription);
        plan.setModified(true);
        
        player.sendMessage(ChatColor.GREEN + "✓ " + ChatColor.WHITE + "步骤 " + (stepIndex + 1) + " 已修改");
    }

    // ===== 计划执行 =====

    /**
     * 处理计划批准
     * 
     * @param player 玩家
     * @param mode 执行模式
     */
    public void handlePlanApproval(Player player, ExecutionPlan.ExecutionMode mode) {
        ExecutionPlan plan = plansMap.get(player.getUniqueId());
        if (plan == null) {
            player.sendMessage(ChatColor.RED + "没有可执行的计划");
            return;
        }

        plan.setExecutionMode(mode);

        switch (mode) {
            case YOLO:
                player.sendMessage(ChatColor.GREEN + "✓ " + ChatColor.WHITE + "YOLO模式：AI将自动生成命令并执行");
                // 切换到YOLO模式
                cliManager.switchMode(player, DialogueSession.Mode.YOLO);
                // 将计划发送给AI
                sendPlanToAI(player, plan);
                break;
            case NORMAL:
                player.sendMessage(ChatColor.YELLOW + "✓ " + ChatColor.WHITE + "普通模式：每步需要确认");
                // 切换到NORMAL模式
                cliManager.switchMode(player, DialogueSession.Mode.NORMAL);
                // 将计划发送给AI
                sendPlanToAI(player, plan);
                break;
            case CONTINUE:
                player.sendMessage(ChatColor.AQUA + "✓ " + ChatColor.WHITE + "继续规划模式");
                // 通知AI继续提问
                cliManager.feedbackToAI(player, "#continue_planning: 请继续提问以补充信息");
                break;
        }
    }

    /**
     * 将计划发送给AI
     *
     * @param player 玩家
     * @param plan 执行计划
     */
    private void sendPlanToAI(Player player, ExecutionPlan plan) {
        StringBuilder planText = new StringBuilder();
        planText.append("#execute_plan: 计划标题: ").append(plan.getTitle()).append("\n");
        planText.append("计划描述: ").append(plan.getDescription()).append("\n");
        planText.append("执行步骤:\n");

        for (int i = 0; i < plan.getSteps().size(); i++) {
            PlanStep step = plan.getSteps().get(i);
            planText.append("  ").append(i + 1).append(". ").append(step.getDescription());
            if (step.getNotes() != null && !step.getNotes().isEmpty()) {
                planText.append(" (备注: ").append(step.getNotes()).append(")");
            }
            planText.append("\n");
        }

        planText.append("\n");
        planText.append("[IMPORTANT] 请根据上述执行步骤创建 TODO 任务列表，让玩家了解任务进度。\n");
        planText.append("要求：\n");
        planText.append("1. 使用 #todo 工具创建任务列表\n");
        planText.append("2. 每个步骤对应一个任务\n");
        planText.append("3. 第一个任务状态设置为 in_progress\n");
        planText.append("4. 其他任务状态设置为 pending\n");
        planText.append("5. 任务描述要简洁明了\n");

        cliManager.feedbackToAI(player, planText.toString());
    }

    /**
     * 执行计划
     * 
     * @param player 玩家
     * @param yoloMode 是否为YOLO模式
     */
    private void executePlan(Player player, boolean yoloMode) {
        ExecutionPlan plan = plansMap.get(player.getUniqueId());
        if (plan == null) return;
        
        List<PlanStep> steps = plan.getSteps();
        
        for (int i = 0; i < steps.size(); i++) {
            PlanStep step = steps.get(i);
            executeStep(player, step, i + 1, steps.size(), yoloMode);
        }
    }

    /**
     * 执行单个步骤
     * 
     * @param player 玩家
     * @param step 步骤
     * @param currentStep 当前步骤编号
     * @param totalSteps 总步骤数
     * @param yoloMode 是否为YOLO模式
     */
    private void executeStep(Player player, PlanStep step, int currentStep, int totalSteps, boolean yoloMode) {
        player.sendMessage(ChatColor.GOLD + "⇒ " + ChatColor.WHITE + "步骤 " + currentStep + "/" + totalSteps + ": " + step.getDescription());
        
        // 通知AI生成命令
        String prompt = "请为以下步骤生成合适的Minecraft命令：\n" + step.getDescription();
        
        if (yoloMode) {
            // YOLO模式：自动执行
            cliManager.feedbackToAI(player, "#execute_step_yolo: " + prompt);
        } else {
            // 普通模式：等待确认
            cliManager.feedbackToAI(player, "#execute_step_normal: " + prompt);
        }
    }

    // ===== Getter和Setter =====

    /**
     * 获取玩家的计划
     * 
     * @param player 玩家
     * @return 执行计划
     */
    public ExecutionPlan getPlan(Player player) {
        return plansMap.get(player.getUniqueId());
    }
}
