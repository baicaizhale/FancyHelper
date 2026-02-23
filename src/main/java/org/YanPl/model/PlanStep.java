package org.YanPl.model;

/**
 * 计划步骤模型
 * 
 * 只描述要做什么，不包含具体命令和风险等级。
 * 具体的Minecraft命令将在执行阶段由AI根据步骤描述动态生成。
 */
public class PlanStep {
    private int order;
    private String description;
    private String notes;

    /**
     * 构造函数
     * 
     * @param order 步骤顺序
     * @param description 步骤描述
     */
    public PlanStep(int order, String description) {
        this.order = order;
        this.description = description;
        this.notes = "";
    }

    /**
     * 获取步骤顺序
     * 
     * @return 步骤顺序
     */
    public int getOrder() {
        return order;
    }

    /**
     * 设置步骤顺序
     * 
     * @param order 步骤顺序
     */
    public void setOrder(int order) {
        this.order = order;
    }

    /**
     * 获取步骤描述
     * 
     * @return 步骤描述
     */
    public String getDescription() {
        return description;
    }

    /**
     * 设置步骤描述
     * 
     * @param description 步骤描述
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * 获取备注信息
     * 
     * @return 备注信息
     */
    public String getNotes() {
        return notes;
    }

    /**
     * 设置备注信息
     * 
     * @param notes 备注信息
     */
    public void setNotes(String notes) {
        this.notes = notes;
    }

    @Override
    public String toString() {
        return "PlanStep{" +
                "order=" + order +
                ", description='" + description + '\'' +
                ", notes='" + notes + '\'' +
                '}';
    }
}