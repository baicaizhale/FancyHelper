package org.YanPl.manager;

import org.YanPl.FancyHelper;
import org.YanPl.util.I18n;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * 验证管理器：处理 ls, read, edit 工具的首次使用验证逻辑。
 */
public class VerificationManager {
    private final FancyHelper plugin;
    private final File verifyDir;
    private final Map<UUID, VerificationSession> activeSessions = new HashMap<>();
    private final Map<UUID, Long> frozenPlayers = new HashMap<>(); // 冻结的玩家及解冻时间

    public VerificationManager(FancyHelper plugin) {
        this.plugin = plugin;
        this.verifyDir = new File(plugin.getDataFolder(), "verify");
        if (!verifyDir.exists()) {
            verifyDir.mkdirs();
        }
    }

    private static class VerificationSession {
        String type; // "ls", "read" or "edit"
        String password;
        long expiry;
        int attempts;
        Runnable onVerify;

        VerificationSession(String type, String password, Runnable onVerify) {
            this.type = type;
            this.password = password;
            this.onVerify = onVerify;
            this.expiry = System.currentTimeMillis() + 10 * 60 * 1000; // 10 minutes
            this.attempts = 0;
        }
    }

    /**
     * 开始验证过程
     * @param player 玩家
     * @param type "read" (ls/read) 或 "edit"
     * @param onVerify 验证成功后的回调
     */
    public void startVerification(Player player, String type, Runnable onVerify) {
        UUID uuid = player.getUniqueId();
        String password = String.format("%06d", new Random().nextInt(1000000));
        VerificationSession session = new VerificationSession(type, password, onVerify);
        activeSessions.put(uuid, session);

        File verifyFile = new File(verifyDir, player.getName() + "-" + type + ".txt");
        try {
            if (verifyFile.exists()) verifyFile.delete();
            verifyFile.createNewFile();

            if (type.equals("read") || type.equals("ls")) {
                Files.write(verifyFile.toPath(), password.getBytes());
                player.sendMessage(I18n.t("verify.file.generated.read", verifyFile.getName()));
                player.sendMessage(I18n.t("verify.read.prompt"));
            } else {
                player.sendMessage(I18n.t("verify.file.generated.edit", verifyFile.getName()));
                player.sendMessage(I18n.t("verify.write.prompt", password));
                player.sendMessage(I18n.t("verify.trigger.prompt"));
            }
        } catch (IOException e) {
            player.sendMessage(I18n.t("verify.generate.fail", e.getMessage()));
            plugin.getCloudErrorReport().report(e);
        }

        // 10分钟后清理
        if (!plugin.isEnabled()) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!plugin.isEnabled()) return;
            if (activeSessions.containsKey(uuid) && activeSessions.get(uuid) == session) {
                activeSessions.remove(uuid);
                if (verifyFile.exists()) verifyFile.delete();
                player.sendMessage(I18n.t("verify.timeout.deleted"));
            }
        }, 10 * 60 * 20L);
    }

    /**
     * 处理玩家发送的消息进行验证
     * @return 如果正在验证中且已处理，返回 true
     */
    public boolean handleVerification(Player player, String message) {
        UUID uuid = player.getUniqueId();
        
        // 检查是否被冻结
        if (frozenPlayers.containsKey(uuid)) {
            long unfreezeTime = frozenPlayers.get(uuid);
            if (System.currentTimeMillis() < unfreezeTime) {
                long remainingSeconds = (unfreezeTime - System.currentTimeMillis()) / 1000;
                player.sendMessage(I18n.t("verify.frozen", remainingSeconds));
                return true;
            } else {
                frozenPlayers.remove(uuid);
            }
        }
        
        if (!activeSessions.containsKey(uuid)) return false;

        VerificationSession session = activeSessions.get(uuid);
        if (System.currentTimeMillis() > session.expiry) {
            activeSessions.remove(uuid);
            new File(verifyDir, player.getName() + "-" + session.type + ".txt").delete();
            player.sendMessage(I18n.t("verify.timeout"));
            return true;
        }

        boolean success = false;
        if (session.type.equals("read") || session.type.equals("ls")) {
            if (message.trim().equals(session.password)) {
                success = true;
            }
        } else {
            // edit 模式：检查文件内容
            File verifyFile = new File(verifyDir, player.getName() + "-" + session.type + ".txt");
            try {
                if (verifyFile.exists()) {
                    String content = new String(Files.readAllBytes(verifyFile.toPath())).trim();
                    if (content.equals(session.password)) {
                        success = true;
                    }
                }
            } catch (IOException e) {
                plugin.getCloudErrorReport().report(e);
            }
        }

        if (success) {
            activeSessions.remove(uuid);
            new File(verifyDir, player.getName() + "-" + session.type + ".txt").delete();
            player.sendMessage(I18n.t("verify.success"));
            if (session.onVerify != null) {
                session.onVerify.run();
            }
        } else {
            session.attempts++;
            if (session.attempts >= 3) {
                activeSessions.remove(uuid);
                new File(verifyDir, player.getName() + "-" + session.type + ".txt").delete();
                
                // 冻结玩家 1 分钟
                frozenPlayers.put(uuid, System.currentTimeMillis() + 60 * 1000);
                
                player.sendMessage(I18n.t("verify.too.many"));
            } else {
                if (session.type.equals("read") || session.type.equals("ls")) {
                    player.sendMessage(I18n.t("verify.wrong.password", 3 - session.attempts));
                } else {
                    player.sendMessage(I18n.t("verify.wrong.content", 3 - session.attempts));
                }
            }
        }
        return true;
    }

    public boolean isVerifying(Player player) {
        return activeSessions.containsKey(player.getUniqueId());
    }
    
    /**
     * 获取玩家验证冻结剩余时间（秒），如果未冻结则返回 0
     * 
     * @param player 玩家
     * @return 剩余冻结时间（秒），0 表示未冻结
     */
    public long getPlayerFreezeRemaining(Player player) {
        UUID uuid = player.getUniqueId();
        if (frozenPlayers.containsKey(uuid)) {
            long unfreezeTime = frozenPlayers.get(uuid);
            if (System.currentTimeMillis() < unfreezeTime) {
                return (unfreezeTime - System.currentTimeMillis()) / 1000;
            } else {
                frozenPlayers.remove(uuid);
            }
        }
        return 0;
    }
}
