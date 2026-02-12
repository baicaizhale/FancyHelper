package org.YanPl.manager;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.YanPl.FancyHelper;
import org.YanPl.model.TodoItem;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TODO 管理器：负责管理玩家的 TODO 列表
 */
public class TodoManager {
    private final FancyHelper plugin;
    private final Map<UUID, List<TodoItem>> playerTodos = new ConcurrentHashMap<>();
    private final Gson gson;

    public TodoManager(FancyHelper plugin) {
        this.plugin = plugin;
        // 使用 lenient 模式解析 JSON，以兼容 AI 可能生成的非标准格式
        this.gson = new com.google.gson.GsonBuilder().setLenient().create();
    }

    /**
     * 解析并更新玩家的 TODO 列表
     *
     * @param uuid    玩家 UUID
     * @param todoJson TODO JSON 字符串
     * @return 解析结果消息
     */
    public String updateTodos(UUID uuid, String todoJson) {
        try {
            plugin.getLogger().info("[TODO] 接收到 TODO JSON: " + todoJson);
            JsonElement jsonElement = gson.fromJson(todoJson, JsonElement.class);

            if (jsonElement == null || !jsonElement.isJsonArray()) {
                plugin.getLogger().warning("[TODO] JSON 解析结果不是数组: " + jsonElement);
                return "错误: TODO 数据必须是数组格式，格式如 [{\"id\":\"1\",\"task\":\"描述\"}]";
            }

            JsonArray jsonArray = jsonElement.getAsJsonArray();
            List<TodoItem> newTodos = new ArrayList<>();

            // 验证并解析每个 TODO 项
            for (int i = 0; i < jsonArray.size(); i++) {
                JsonElement itemElement = jsonArray.get(i);
                if (!itemElement.isJsonObject()) {
                    plugin.getLogger().warning("[TODO] 第 " + (i + 1) + " 项不是对象: " + itemElement);
                    return "错误: 第 " + (i + 1) + " 项必须是对象格式";
                }

                JsonObject itemObj = itemElement.getAsJsonObject();
                TodoItem todo = parseTodoItem(itemObj, i + 1);
                if (todo == null) {
                    return "错误: 第 " + (i + 1) + " 项缺少必需字段（id 或 task）";
                }
                newTodos.add(todo);
            }

            // 验证：只能有一个任务处于 in_progress 状态
            long inProgressCount = newTodos.stream()
                    .filter(t -> t.getStatus() == TodoItem.Status.IN_PROGRESS)
                    .count();

            if (inProgressCount > 1) {
                plugin.getLogger().warning("[TODO] 多个任务处于 in_progress 状态: " + inProgressCount);
                return "错误: 同时只能有一个任务处于 in_progress 状态，当前有 " + inProgressCount + " 个";
            }

            // 更新玩家的 TODO 列表
            playerTodos.put(uuid, newTodos);
            plugin.getLogger().info("[TODO] 成功更新 " + newTodos.size() + " 个任务");
            return "TODO 列表已更新";

        } catch (JsonSyntaxException e) {
            plugin.getLogger().warning("[TODO] JSON 语法错误: " + e.getMessage() + ", 原始数据: " + todoJson);
            return "错误: JSON 格式无效 - " + e.getMessage() + "。请确保格式为 [{\"id\":\"1\",\"task\":\"任务描述\",\"status\":\"pending\"}]";
        } catch (Exception e) {
            plugin.getLogger().warning("[TODO] 解析 TODO 时出错: " + e.getMessage() + ", 原始数据: " + todoJson);
            return "错误: 解析 TODO 失败 - " + e.getMessage();
        }
    }

    /**
     * 解析单个 TODO 项
     *
     * @param itemObj  JSON 对象
     * @param index    索引（用于错误提示）
     * @return TodoItem 或 null（表示解析失败）
     */
    private TodoItem parseTodoItem(JsonObject itemObj, int index) {
        // 检查必需字段 id 和 task
        if (!itemObj.has("id") || !itemObj.has("task")) {
            plugin.getLogger().warning("TODO 第 " + index + " 项缺少必需字段: id 或 task");
            return null;
        }

        String id = itemObj.get("id").getAsString();
        String task = itemObj.get("task").getAsString();

        if (id == null || id.trim().isEmpty() || task == null || task.trim().isEmpty()) {
            plugin.getLogger().warning("TODO 第 " + index + " 项的 id 或 task 为空");
            return null;
        }

        TodoItem todo = new TodoItem(id, task);

        // 解析状态
        if (itemObj.has("status")) {
            String statusStr = itemObj.get("status").getAsString();
            todo.setStatus(statusStr);
        }

        // 解析描述
        if (itemObj.has("description")) {
            todo.setDescription(itemObj.get("description").getAsString());
        }

        // 解析优先级
        if (itemObj.has("priority")) {
            todo.setPriority(itemObj.get("priority").getAsString());
        }

        return todo;
    }

    /**
     * 获取玩家的 TODO 列表
     *
     * @param uuid 玩家 UUID
     * @return TODO 列表（如果没有则返回空列表）
     */
    public List<TodoItem> getTodos(UUID uuid) {
        return playerTodos.getOrDefault(uuid, new ArrayList<>());
    }

    /**
     * 清除玩家的 TODO 列表
     *
     * @param uuid 玩家 UUID
     */
    public void clearTodos(UUID uuid) {
        playerTodos.remove(uuid);
    }

    /**
     * 检查玩家是否有 TODO 列表
     *
     * @param uuid 玩家 UUID
     * @return 是否有 TODO 列表
     */
    public boolean hasTodos(UUID uuid) {
        List<TodoItem> todos = playerTodos.get(uuid);
        return todos != null && !todos.isEmpty();
    }

    /**
     * 获取 TODO 列表的简要显示文本
     *
     * @param uuid 玩家 UUID
     * @return 显示文本
     */
    public String getTodoSummary(UUID uuid) {
        List<TodoItem> todos = getTodos(uuid);
        if (todos.isEmpty()) {
            return "当前没有 TODO 任务";
        }

        int total = todos.size();
        int completed = (int) todos.stream().filter(t -> t.getStatus() == TodoItem.Status.COMPLETED).count();
        int inProgress = (int) todos.stream().filter(t -> t.getStatus() == TodoItem.Status.IN_PROGRESS).count();
        int pending = (int) todos.stream().filter(t -> t.getStatus() == TodoItem.Status.PENDING).count();

        StringBuilder sb = new StringBuilder();
        sb.append("TODO 进度: ").append(completed).append("/").append(total).append(" 已完成");
        
        if (inProgress > 0) {
            sb.append(" | ").append(inProgress).append(" 进行中");
        }
        if (pending > 0) {
            sb.append(" | ").append(pending).append(" 待办");
        }

        return sb.toString();
    }

    /**
     * 获取 TODO 列表的详细显示文本
     *
     * @param uuid 玩家 UUID
     * @return 显示文本
     */
    public String getTodoDetails(UUID uuid) {
        List<TodoItem> todos = getTodos(uuid);
        if (todos.isEmpty()) {
            return "当前没有 TODO 任务";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("=== TODO 列表 ===\n");
        
        for (int i = 0; i < todos.size(); i++) {
            TodoItem todo = todos.get(i);
            sb.append((i + 1)).append(". ").append(todo.getFullDisplayText());
            
            if (i < todos.size() - 1) {
                sb.append("\n");
            }
        }

        sb.append("\n=== 进度: ").append(getProgressText(todos)).append(" ===");
        return sb.toString();
    }

    /**
     * 获取进度文本
     *
     * @param todos TODO 列表
     * @return 进度文本
     */
    private String getProgressText(List<TodoItem> todos) {
        int total = todos.size();
        int completed = (int) todos.stream().filter(t -> t.getStatus() == TodoItem.Status.COMPLETED).count();
        return completed + "/" + total;
    }

    /**
     * 创建一个包含 TODO 内容的虚拟书本
     *
     * @param player 玩家
     * @return 包含 TODO 的 ItemStack (WRITTEN_BOOK)
     */
    public ItemStack getTodoBook(Player player) {
        List<TodoItem> todos = getTodos(player.getUniqueId());
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();

        if (meta != null) {
            meta.setTitle("TODO 列表");
            meta.setAuthor("FancyHelper");

            final int MAX_PAGE_CHARS = 128;
            StringBuilder pageBuilder = new StringBuilder();

            // 添加标题
            pageBuilder.append(ChatColor.GOLD).append("=== TODO 列表 ===\n\n");
            pageBuilder.append(ChatColor.RESET).append(getTodoSummary(player.getUniqueId()));
            pageBuilder.append("\n\n");
            pageBuilder.append(ChatColor.GRAY).append("------------------\n\n");
            pageBuilder.append(ChatColor.RESET);

            // 添加 TODO 项
            for (int i = 0; i < todos.size(); i++) {
                TodoItem todo = todos.get(i);
                String itemText = (i + 1) + ". " + todo.getFullDisplayText() + "\n\n";

                // 检查是否会超出页面限制
                if (pageBuilder.length() + itemText.length() > MAX_PAGE_CHARS) {
                    if (pageBuilder.length() > 0) {
                        meta.addPage(ChatColor.RESET.toString() + pageBuilder.toString());
                        pageBuilder = new StringBuilder();
                    }
                }

                pageBuilder.append(itemText);

                // 如果单项就超过页面限制，需要分页
                while (pageBuilder.length() > MAX_PAGE_CHARS) {
                    int splitIndex = MAX_PAGE_CHARS;
                    meta.addPage(ChatColor.RESET.toString() + pageBuilder.substring(0, splitIndex));
                    pageBuilder = new StringBuilder(pageBuilder.substring(splitIndex));
                }
            }

            // 添加最后一页
            if (pageBuilder.length() > 0) {
                meta.addPage(ChatColor.RESET.toString() + pageBuilder.toString());
            }

            // 如果所有 TODO 都已完成，添加完成标记
            if (!todos.isEmpty()) {
                boolean allCompleted = todos.stream().allMatch(t -> t.getStatus() == TodoItem.Status.COMPLETED);
                if (allCompleted) {
                    meta.addPage(ChatColor.GREEN + "\n\n✓ 所有任务已完成！\n\n" + ChatColor.RESET + "输入 /cli todo 可以继续查看或更新任务列表。");
                }
            }

            book.setItemMeta(meta);
        }

        return book;
    }

    /**
     * 获取带点击事件的 TODO 显示组件
     *
     * @param player 玩家
     * @return TextComponent
     */
    public net.md_5.bungee.api.chat.TextComponent getTodoDisplayComponent(Player player) {
        List<TodoItem> todos = getTodos(player.getUniqueId());
        net.md_5.bungee.api.chat.TextComponent component = new net.md_5.bungee.api.chat.TextComponent();

        if (todos.isEmpty()) {
            component.setText(ChatColor.GRAY + "TODO: 暂无任务");
            return component;
        }

        // 构建显示文本
        String summary = getTodoSummary(player.getUniqueId());
        component.setText(ChatColor.GOLD + "☐ " + ChatColor.WHITE + summary);

        // 设置点击事件
        component.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
                net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND,
                "/fancyhelper todo"
        ));

        // 设置悬停事件
        component.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(
                net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                new Text(ChatColor.AQUA + "点击查看完整 TODO 列表")
        ));

        return component;
    }
}
