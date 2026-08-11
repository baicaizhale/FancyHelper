package org.YanPl.manager;

import org.YanPl.FancyHelper;
import org.bukkit.Bukkit;

import java.io.IOException;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 文件监听管理器：负责监控插件目录下的配置文件变动并自动重载。
 */
public class FileWatcherManager {
    private final FancyHelper plugin;
    private WatchService watchService;
    private Thread watchThread;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final Map<String, Long> lastProcessed = new HashMap<>();
    private static final long DEBOUNCE_TIME = 3000; // 3秒防抖间隔
    // mtime 轮询兜底：Windows 上 Java WatchService 对"替换式保存"（写临时文件后 rename，
    // 如 Git Bash 的 sed -i、部分编辑器）产生的 ENTRY_CREATE/DELETE 事件不可靠甚至不报，
    // 定时比对文件最后修改时间确保任何保存方式都能触发重载。
    private long lastConfigMtime = -1;
    private long lastPlayerDataMtime = -1;
    private org.bukkit.scheduler.BukkitTask pollTask;

    public FileWatcherManager(FancyHelper plugin) {
        this.plugin = plugin;
        startMonitoring();
    }

    private void startMonitoring() {
        try {
            this.watchService = FileSystems.getDefault().newWatchService();
            Path path = plugin.getDataFolder().toPath();
            
            // 确保目录存在
            if (!Files.exists(path)) {
                Files.createDirectories(path);
            }

            path.register(watchService, StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY);

            // 玩家列表 JSON 存放在 runtime 子目录，需单独注册监听
            Path runtimePath = path.resolve("runtime");
            if (!Files.exists(runtimePath)) {
                Files.createDirectories(runtimePath);
            }
            runtimePath.register(watchService, StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY);

            watchThread = new Thread(() -> {
                while (running.get()) {
                    WatchKey key;
                    try {
                        key = watchService.take();
                    } catch (InterruptedException | ClosedWatchServiceException e) {
                        break;
                    }

                    for (WatchEvent<?> event : key.pollEvents()) {
                        if (event.kind() == StandardWatchEventKinds.OVERFLOW) continue;

                        Path eventPath = (Path) event.context();
                        String fileName = eventPath.getFileName().toString();

                        // 稍微延迟一下以防文件被占用
                        try { Thread.sleep(500); } catch (InterruptedException ignored) {}

                        handleFileChange(fileName, event.kind());
                    }

                    if (!key.reset()) {
                        break;
                    }
                }
            }, "FancyHelper-FileWatcher");
            watchThread.setDaemon(true);
            watchThread.start();
            plugin.getLogger().info("配置文件实时监控已启动。");

            // 记录初始 mtime，供轮询兜底对比
            lastConfigMtime = mtimeOf(plugin.getDataFolder().toPath().resolve("config.yml"));
            lastPlayerDataMtime = mtimeOf(plugin.getDataFolder().toPath().resolve("playerdata.yml"));
            // 每 5 秒轮询一次（mtime 变化即重载），与 WatchService 事件互补，防漏
            pollTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::pollMtimeChanges, 100L, 100L);
        } catch (IOException e) {
            plugin.getLogger().warning("无法启动文件监控: " + e.getMessage());
        }
    }

    private static long mtimeOf(Path file) {
        try {
            return Files.getLastModifiedTime(file).toMillis();
        } catch (IOException e) {
            return -1;
        }
    }

    private void pollMtimeChanges() {
        if (!plugin.isEnabled()) return;
        long configMtime = mtimeOf(plugin.getDataFolder().toPath().resolve("config.yml"));
        if (configMtime != lastConfigMtime && configMtime > 0) {
            lastConfigMtime = configMtime;
            reloadWithDebounce("config.yml");
        }
        long playerDataMtime = mtimeOf(plugin.getDataFolder().toPath().resolve("playerdata.yml"));
        if (playerDataMtime != lastPlayerDataMtime && playerDataMtime > 0) {
            lastPlayerDataMtime = playerDataMtime;
            reloadWithDebounce("playerdata.yml");
        }
    }

    /** 带 3 秒防抖的配置重载（与 WatchService 路径共用，避免同一改动重复加载） */
    private void reloadWithDebounce(String fileName) {
        long now = System.currentTimeMillis();
        if (now - lastProcessed.getOrDefault(fileName, 0L) < DEBOUNCE_TIME) {
            return;
        }
        lastProcessed.put(fileName, now);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!plugin.isEnabled()) return;
            applyReload(fileName);
        });
    }

    private void applyReload(String fileName) {
        switch (fileName) {
            case "config.yml":
                try {
                    plugin.getConfigManager().loadConfig();
                    plugin.getLogger().info("检测到 config.yml 变动，已自动重载。");
                } catch (Exception e) {
                    plugin.getLogger().severe("重载 config.yml 时出错: " + e.getMessage());
                }
                break;
            case "playerdata.yml":
                plugin.getConfigManager().loadPlayerData();
                plugin.getLogger().info("检测到 playerdata.yml 变动，已自动重载。");
                break;
            case "agreed_players.json":
                plugin.getCliManager().loadAgreedPlayers();
                break;
            case "yolo_agreed_players.json":
                plugin.getCliManager().loadYoloAgreedPlayers();
                break;
            case "yolo_mode_players.json":
                plugin.getCliManager().loadYoloModePlayers();
                break;
            case "smart_mode_players.json":
                plugin.getCliManager().loadSmartModePlayers();
                break;
            case "plan_mode_players.json":
                plugin.getCliManager().loadPlanModePlayers();
                break;
            default:
                break;
        }
    }

    private void handleFileChange(String fileName, WatchEvent.Kind<?> kind) {
        long now = System.currentTimeMillis();
        if (now - lastProcessed.getOrDefault(fileName, 0L) < DEBOUNCE_TIME) {
            return;
        }
        lastProcessed.put(fileName, now);

        // 文件被删除时：config.yml 不存在了没有重载意义（loadConfig 会重新生成默认
        // 配置导致用户改动被重置），等 CREATE/MODIFY 事件再加载；仅内存状态类 json
        // 文件（players 等）删除时仍按原逻辑处理。
        if (kind == StandardWatchEventKinds.ENTRY_DELETE && "config.yml".equals(fileName)) {
            return;
        }

        if (!plugin.isEnabled()) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!plugin.isEnabled()) return;
            applyReload(fileName);
        });
    }

    public void shutdown() {
        running.set(false);
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException ignored) {}
        }
        if (watchThread != null) {
            watchThread.interrupt();
        }
        // 取消 mtime 轮询定时任务，避免插件重载后残留任务重复重载
        if (pollTask != null) {
            pollTask.cancel();
            pollTask = null;
        }
    }
}
