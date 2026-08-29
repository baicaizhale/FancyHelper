package org.YanPl.manager;

import org.YanPl.FancyHelper;
import org.bukkit.Bukkit;

/**
 * FancyConsole 健康监控。
 *
 * <p>仅在 {@code config.yml} 的 {@code provider.ai: fancy} 时启用（其余 provider 完全不启动、零副作用）。
 * 启动后立即（约 1s 后）检查一次 {@code GET /health}，此后每 5 分钟复查一次：
 * <ul>
 *   <li>健康 → 宕机：控制台输出一次错误提示，记录「服务宕机」状态；</li>
 *   <li>仍宕机：静默，不重复刷屏；</li>
 *   <li>宕机 → 健康：清除「服务宕机」状态，输出恢复日志。</li>
 * </ul>
 *
 * <p>玩家 {@code /cli} 进入时若处于宕机状态，由 {@code CLICommand} 调用 {@link #checkOnce()} 再探测一次，
 * 恢复则直接放行、仍宕机则提示切换第三方提供商。
 */
public class FancyHealthMonitor {

    /** 首次检查延迟（tick）：约 1 秒，避开插件启动流程竞争。 */
    private static final long INITIAL_DELAY_TICKS = 20L;
    /** 复查周期（tick）：5 分钟 = 300s * 20tick。 */
    private static final long CHECK_PERIOD_TICKS = 6000L;

    private final FancyHelper plugin;
    private final FancyConsoleManager fancyConsoleManager;

    /** 服务当前是否处于「宕机」状态。volatile 保证 /cli 门控读到最新值。 */
    private volatile boolean down = false;
    private int taskId = -1;

    public FancyHealthMonitor(FancyHelper plugin, FancyConsoleManager fancyConsoleManager) {
        this.plugin = plugin;
        this.fancyConsoleManager = fancyConsoleManager;
    }

    /**
     * 启动周期探测。仅在 {@code provider.ai: fancy} 时生效，其余 provider 直接返回。
     */
    public void start() {
        if (!plugin.getConfigManager().isFancyConsoleAi()) {
            return;
        }
        if (taskId != -1) {
            return; // 已在运行，避免重复调度
        }
        taskId = Bukkit.getScheduler()
                .runTaskTimerAsynchronously(plugin, this::checkAndApply, INITIAL_DELAY_TICKS, CHECK_PERIOD_TICKS)
                .getTaskId();
    }

    /**
     * 周期任务入口：探测一次并应用状态切换（忽略返回值）。
     */
    public void checkAndApply() {
        probe();
    }

    /**
     * 单次探测并应用状态切换，返回「当前是否健康」。
     * 供 {@code /cli} 门控使用：恢复后直接放行。
     *
     * @return true 表示服务健康（HTTP 200）
     */
    public boolean checkOnce() {
        return probe();
    }

    /**
     * 当前是否处于「宕机」状态。
     */
    public boolean isDown() {
        return down;
    }

    /**
     * 停止周期任务（插件 disable 时调用）。
     */
    public void shutdown() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
    }

    private boolean probe() {
        boolean healthy = fancyConsoleManager.checkHealth();
        synchronized (this) {
            if (healthy) {
                if (down) {
                    down = false;
                    plugin.getLogger().info("[FancyHealth] FancyConsole 服务已恢复。");
                }
            } else {
                if (!down) {
                    down = true;
                    plugin.getLogger().warning("[FancyHealth] FancyConsole 服务不可用（/health 检查失败），"
                            + "AI 对话将暂时受限，正在每 5 分钟自动重试...");
                }
                // 仍宕机：静默，不重复输出
            }
        }
        return healthy;
    }
}
