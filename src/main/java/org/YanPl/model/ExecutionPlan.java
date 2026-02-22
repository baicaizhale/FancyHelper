package org.YanPl.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 执行计划模型
 * 
 * 不包含具体命令，只描述要做什么。
 * 具体的Minecraft命令将在执行阶段由AI动态生成。
 */
public class ExecutionPlan {
    private String id;
    private String title;
    private String description;
    private List<PlanStep> steps;
    private long createdAt;
    private boolean modified;
    private ExecutionMode executionMode;

    /**
     * 执行模式枚举
     */
    public enum ExecutionMode {
        YOLO,       // YOLO模式：AI自动生成命令并执行
        NORMAL,     // 普通模式：每步需要确认
        CONTINUE    // 继续规划：重新提问
    }

    /**
     * 构造函数
     * 
     * @param title 计划标题
     * @param description 计划描述
     */
    public ExecutionPlan(String title, String description) {
        this.id = generateId();
        this.title = title;
        this.description = description;
        this.steps = new ArrayList<>();
        this.createdAt = System.currentTimeMillis();
        this.modified = false;
        this.executionMode = ExecutionMode.NORMAL;
    }

    /**
     * 生成唯一ID
     * 
     * @return 唯一ID
     */
    private String generateId() {
        return "plan_" + System.currentTimeMillis();
    }

    /**
     * 从另一个计划复制数据（深拷贝）
     * 
     * @param other 源计划
     */
    public void copyFrom(ExecutionPlan other) {
        if (other == null) {
            return;
        }

        this.id = other.id;
        this.title = other.title;
        this.description = other.description;
        this.createdAt = other.createdAt;
        this.modified = other.modified;
        this.executionMode = other.executionMode;

        // 深拷贝步骤列表
        this.steps.clear();
        for (PlanStep otherStep : other.steps) {
            PlanStep newStep = new PlanStep(otherStep.getOrder(), otherStep.getDescription());
            newStep.setNotes(otherStep.getNotes());
            this.steps.add(newStep);
        }
    }

    /**
     * 添加步骤
     * 
     * @param step 步骤
     */
    public void addStep(PlanStep step) {
        step.setOrder(steps.size() + 1);
        steps.add(step);
    }

    /**
     * 移除步骤
     * 
     * @param index 步骤索引
     */
    public void removeStep(int index) {
        if (index >= 0 && index < steps.size()) {
            steps.remove(index);
            // 重新排序
            for (int i = 0; i < steps.size(); i++) {
                steps.get(i).setOrder(i + 1);
            }
        }
    }

    /**
     * 获取计划ID
     * 
     * @return 计划ID
     */
    public String getId() {
        return id;
    }

    /**
     * 设置计划ID
     * 
     * @param id 计划ID
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * 获取计划标题
     * 
     * @return 计划标题
     */
    public String getTitle() {
        return title;
    }

    /**
     * 设置计划标题
     * 
     * @param title 计划标题
     */
    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * 获取计划描述
     * 
     * @return 计划描述
     */
    public String getDescription() {
        return description;
    }

    /**
     * 设置计划描述
     * 
     * @param description 计划描述
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * 获取步骤列表
     * 
     * @return 步骤列表
     */
    public List<PlanStep> getSteps() {
        return steps;
    }

    /**
     * 设置步骤列表
     * 
     * @param steps 步骤列表
     */
    public void setSteps(List<PlanStep> steps) {
        this.steps = steps;
        // 重新排序
        for (int i = 0; i < steps.size(); i++) {
            steps.get(i).setOrder(i + 1);
        }
    }

    /**
     * 获取创建时间
     * 
     * @return 创建时间（毫秒）
     */
    public long getCreatedAt() {
        return createdAt;
    }

    /**
     * 设置创建时间
     * 
     * @param createdAt 创建时间（毫秒）
     */
    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * 检查是否已修改
     * 
     * @return 如果已修改返回true，否则返回false
     */
    public boolean isModified() {
        return modified;
    }

    /**
     * 设置是否已修改
     * 
     * @param modified 是否已修改
     */
    public void setModified(boolean modified) {
        this.modified = modified;
    }

    /**
     * 获取执行模式
     * 
     * @return 执行模式
     */
    public ExecutionMode getExecutionMode() {
        return executionMode;
    }

    /**
     * 设置执行模式
     * 
     * @param executionMode 执行模式
     */
    public void setExecutionMode(ExecutionMode executionMode) {
        this.executionMode = executionMode;
    }

    @Override
    public String toString() {
        return "ExecutionPlan{" +
                "id='" + id + '\'' +
                ", title='" + title + '\'' +
                ", description='" + description + '\'' +
                ", steps=" + steps.size() +
                ", createdAt=" + createdAt +
                ", modified=" + modified +
                ", executionMode=" + executionMode +
                '}';
    }
}
