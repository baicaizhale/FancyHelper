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

            path.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);

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

                        handleFileChange(fileName);
                    }

                    if (!key.reset()) {
                        break;
                    }
                }
            }, "FancyHelper-FileWatcher");
            watchThread.setDaemon(true);
            watchThread.start();
            plugin.getLogger().info("配置文件实时监控已启动。");
        } catch (IOException e) {
            plugin.getLogger().warning("无法启动文件监控: " + e.getMessage());
        }
    }

    private void handleFileChange(String fileName) {
        long now = System.currentTimeMillis();
        if (now - lastProcessed.getOrDefault(fileName, 0L) < DEBOUNCE_TIME) {
            return;
        }
        lastProcessed.put(fileName, now);

        Bukkit.getScheduler().runTask(plugin, () -> {
            switch (fileName) {
                case "config.yml":
                    plugin.getConfigManager().loadConfig();
                    plugin.getLogger().info("检测到 config.yml 变动，已自动重载。");
                    break;
                case "playerdata.yml":
                    plugin.getConfigManager().loadPlayerData();
                    plugin.getLogger().info("检测到 playerdata.yml 变动，已自动重载。");
                    break;
                case "agreed_players.txt":
                    plugin.getCliManager().loadAgreedPlayers();
                    plugin.getLogger().info("检测到 agreed_players.txt 变动，已自动重载。");
                    break;
                case "yolo_agreed_players.txt":
                    plugin.getCliManager().loadYoloAgreedPlayers();
                    plugin.getLogger().info("检测到 yolo_agreed_players.txt 变动，已自动重载。");
                    break;
                case "yolo_mode_players.txt":
                    plugin.getCliManager().loadYoloModePlayers();
                    plugin.getLogger().info("检测到 yolo_mode_players.txt 变动，已自动重载。");
                    break;
            }
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
    }
}
