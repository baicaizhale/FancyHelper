package org.YanPl.util;

import org.YanPl.FancyHelper;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;


/**
 * 统一错误处理工具类
 * 提供标准化的错误处理和恢复机制
 */
public class ErrorHandler {
    private final FancyHelper plugin;

    public ErrorHandler(FancyHelper plugin) {
        this.plugin = plugin;
    }

    /**
     * 处理异步任务中的异常，自动上报并执行回调
     * @param player 玩家（可为 null）
     * @param operation 操作名称
     * @param error 发生的异常
     * @param callback 错误处理后的回调（在主线程执行）
     */
    public void handleAsyncError(Player player, String operation, Throwable error, Runnable callback) {
        // 上报错误
        plugin.getCloudErrorReport().report(error);
        
        // 记录日志
        plugin.getLogger().warning("[" + operation + "] 发生错误: " + error.getMessage());
        
        // 在主线程执行回调
        if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (callback != null) {
                    callback.run();
                }
            });
        }
    }

    /**
     * 处理异步任务中的异常，向玩家显示错误消息
     * @param player 玩家
     * @param operation 操作名称
     * @param error 发生的异常
     * @param userMessage 显示给用户的消息（可选）
     */
    public void handleAsyncErrorWithMessage(Player player, String operation, Throwable error, String userMessage) {
        handleAsyncError(player, operation, error, () -> {
            if (player != null && player.isOnline()) {
                String message = userMessage != null ? userMessage :
                    ColorUtil.translateCustomColors("§zFancyHelper§b§r §7> §f" + operation + "失败: " + error.getMessage());
                player.sendMessage(message);
            }
        });
    }

    /**
     * 安全执行异步任务，捕获所有异常
     * @param player 玩家（可为 null）
     * @param operation 操作名称
     * @param task 要执行的任务
     * @param errorCallback 错误回调（可为 null）
     */
    public void safeAsyncExecute(Player player, String operation, Runnable task, Runnable errorCallback) {
        try {
            task.run();
        } catch (Throwable t) {
            handleAsyncError(player, operation, t, errorCallback);
        }
    }

    /**
     * 安全执行异步任务，捕获所有异常并显示默认错误消息
     * @param player 玩家
     * @param operation 操作名称
     * @param task 要执行的任务
     */
    public void safeAsyncExecuteWithMessage(Player player, String operation, Runnable task) {
        try {
            task.run();
        } catch (Throwable t) {
            handleAsyncErrorWithMessage(player, operation, t, null);
        }
    }

    /**
     * 创建错误恢复回调 - 显示重试按钮
     * @param player 玩家
     * @param retryCommand 重试命令
     * @return 回调函数
     */
    public Runnable createRetryCallback(Player player, String retryCommand) {
        return () -> {
            if (player == null || !player.isOnline()) return;
            
            player.sendMessage(ChatColor.YELLOW + "操作失败，点击 " + 
                ChatColor.GREEN + "[重试]" + ChatColor.YELLOW + " 重新尝试");
            // 注意：实际的点击按钮需要在调用处创建，因为需要 TextComponent
        };
    }

    /**
     * 检查插件是否仍然启用，避免在禁用后执行操作
     * @return 插件是否启用
     */
    public boolean isPluginEnabled() {
        return plugin.isEnabled();
    }

    /**
     * 在主线程安全执行任务
     * @param task 要执行的任务
     */
    public void runOnMainThread(Runnable task) {
        if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    /**
     * 在主线程延迟执行任务
     * @param task 要执行的任务
     * @param delay 延迟（tick）
     */
    public void runOnMainThreadLater(Runnable task, long delay) {
        if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTaskLater(plugin, task, delay);
        }
    }
}
