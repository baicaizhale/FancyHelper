package org.YanPl.model;

/**
 * 待办 任务项数据模型
 */
public class TodoItem {
    /**
     * 任务状态
     */
    public enum Status {
        PENDING("☐", "待办"),
        IN_PROGRESS("»", "进行中"),
        COMPLETED("✓", "已完成"),
        CANCELLED("✗", "已取消");

        private final String icon;
        private final String description;

        Status(String icon, String description) {
            this.icon = icon;
            this.description = description;
        }

        public String getIcon() {
            return icon;
        }

        public String getDescription() {
            return description;
        }

        /**
         * 从字符串解析状态
         */
        public static Status fromString(String str) {
            if (str == null) return PENDING;
            switch (str.toLowerCase()) {
                case "in_progress":
                case "in-progress":
                case "inprogress":
                    return IN_PROGRESS;
                case "completed":
                case "done":
                    return COMPLETED;
                case "cancelled":
                case "canceled":
                    return CANCELLED;
                default:
                    return PENDING;
            }
        }
    }

    private String id;
    private String task;
    private Status status;
    private String description;
    private String priority;

    public TodoItem() {
        this.status = Status.PENDING;
        this.priority = "medium";
    }

    public TodoItem(String id, String task) {
        this();
        this.id = id;
        this.task = task;
    }

    public TodoItem(String id, String task, Status status) {
        this(id, task);
        this.status = status;
    }

    public TodoItem(String id, String task, Status status, String description) {
        this(id, task, status);
        this.description = description;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTask() {
        return task;
    }

    public void setTask(String task) {
        this.task = task;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    /**
     * 从字符串设置状态
     */
    public void setStatus(String status) {
        this.status = Status.fromString(status);
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    /**
     * 获取带图标的状态显示文本
     */
    public String getDisplayText() {
        return status.getIcon() + " " + task;
    }

    /**
     * 获取完整的显示文本（包含描述）
     */
    public String getFullDisplayText() {
        StringBuilder sb = new StringBuilder();
        sb.append(status.getIcon()).append(" ").append(task);
        if (description != null && !description.isEmpty()) {
            sb.append("\n  ").append(description);
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return "TodoItem{" +
                "id='" + id + '\'' +
                ", task='" + task + '\'' +
                ", status=" + status +
                ", description='" + description + '\'' +
                ", priority='" + priority + '\'' +
                '}';
    }
}
