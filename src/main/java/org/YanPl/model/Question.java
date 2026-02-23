package org.YanPl.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 问题模型，用于计划模式的问答阶段
 * 
 * 支持三种问题类型：
 * - TEXT: 文本输入
 * - CHECKBOX: 多选
 * - RADIO: 单选
 */
public class Question {
    private String id;
    private QuestionType type;
    private String text;
    private List<String> options;
    private boolean required;
    private String answer;
    private int order;

    /**
     * 问题类型枚举
     */
    public enum QuestionType {
        TEXT,       // 文本输入
        CHECKBOX,   // 多选
        RADIO       // 单选
    }

    /**
     * 构造函数
     * 
     * @param id 问题唯一标识符
     * @param type 问题类型
     * @param text 问题文本
     * @param order 问题顺序
     */
    public Question(String id, QuestionType type, String text, int order) {
        this.id = id;
        this.type = type;
        this.text = text;
        this.order = order;
        this.required = true;
        this.options = new ArrayList<>();
        this.answer = null;
    }

    /**
     * 检查问题是否已回答
     * 
     * @return 如果已回答返回true，否则返回false
     */
    public boolean isAnswered() {
        return answer != null && !answer.trim().isEmpty();
    }

    /**
     * 获取问题ID
     * 
     * @return 问题ID
     */
    public String getId() {
        return id;
    }

    /**
     * 设置问题ID
     * 
     * @param id 问题ID
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * 获取问题类型
     * 
     * @return 问题类型
     */
    public QuestionType getType() {
        return type;
    }

    /**
     * 设置问题类型
     * 
     * @param type 问题类型
     */
    public void setType(QuestionType type) {
        this.type = type;
    }

    /**
     * 获取问题文本
     * 
     * @return 问题文本
     */
    public String getText() {
        return text;
    }

    /**
     * 设置问题文本
     * 
     * @param text 问题文本
     */
    public void setText(String text) {
        this.text = text;
    }

    /**
     * 获取选项列表（仅用于CHECKBOX和RADIO类型）
     * 
     * @return 选项列表
     */
    public List<String> getOptions() {
        return options;
    }

    /**
     * 设置选项列表
     * 
     * @param options 选项列表
     */
    public void setOptions(List<String> options) {
        this.options = options;
    }

    /**
     * 添加选项
     * 
     * @param option 选项
     */
    public void addOption(String option) {
        this.options.add(option);
    }

    /**
     * 检查是否必填
     * 
     * @return 如果必填返回true，否则返回false
     */
    public boolean isRequired() {
        return required;
    }

    /**
     * 设置是否必填
     * 
     * @param required 是否必填
     */
    public void setRequired(boolean required) {
        this.required = required;
    }

    /**
     * 获取答案
     * 
     * @return 答案
     */
    public String getAnswer() {
        return answer;
    }

    /**
     * 设置答案
     * 
     * @param answer 答案
     */
    public void setAnswer(String answer) {
        this.answer = answer;
    }

    /**
     * 获取问题顺序
     * 
     * @return 问题顺序
     */
    public int getOrder() {
        return order;
    }

    /**
     * 设置问题顺序
     * 
     * @param order 问题顺序
     */
    public void setOrder(int order) {
        this.order = order;
    }

    @Override
    public String toString() {
        return "Question{" +
                "id='" + id + '\'' +
                ", type=" + type +
                ", text='" + text + '\'' +
                ", options=" + options +
                ", required=" + required +
                ", answer='" + answer + '\'' +
                ", order=" + order +
                '}';
    }
}