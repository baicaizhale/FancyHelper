package org.YanPl.util;

import org.YanPl.FancyHelper;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 云端错误上报工具类
 */
public class CloudErrorReport {

    private final JavaPlugin plugin;
    private final String workerUrl = "https://report-fancy.baicaizhale.top/";
    private final Set<Integer> reportedHashes = ConcurrentHashMap.newKeySet();

    /**
     * 构造函数
     *
     * @param plugin 插件实例
     */
    public CloudErrorReport(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 手动上报异常
     *
     * @param throwable 抛出的异常
     */
    public void report(Throwable throwable) {
        if (throwable == null) return;

        // 检查配置是否启用自动上报
        if (plugin instanceof FancyHelper) {
            if (!((FancyHelper) plugin).getConfigManager().isAutoReportEnabled()) {
                return;
            }
        }

        // 插件已禁用时，Bukkit 调度器将拒绝注册任务；此时跳过上报以避免二次异常
        if (!plugin.isEnabled()) {
            return;
        }

        // 1. 获取堆栈第一行计算哈希，防止重复上报
        int errorHash = throwable.getStackTrace().length > 0
                ? throwable.getStackTrace()[0].hashCode() + throwable.toString().hashCode()
                : throwable.toString().hashCode();

        if (!reportedHashes.add(errorHash)) return;

        // 2. 异步执行请求
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // 创建临时目录
                File tempDir = new File(plugin.getDataFolder(), "temp");
                if (!tempDir.exists()) {
                    tempDir.mkdirs();
                }

                // 收集日志文件
                List<File> logFiles = collectLogFiles(tempDir, throwable);

                // 上传文件
                uploadFiles(logFiles);

                // 清理临时文件
                for (File file : logFiles) {
                    file.delete();
                }

            } catch (Exception e) {
                plugin.getLogger().warning("错误上报失败: " + e.getMessage());
            }
        });
    }

    /**
     * 收集日志文件
     *
     * @param tempDir 临时目录
     * @param throwable 异常
     * @return 收集的日志文件列表
     * @throws IOException IO异常
     */
    private List<File> collectLogFiles(File tempDir, Throwable throwable) throws IOException {
        List<File> logFiles = new ArrayList<>();

        // 1. 收集最近200条终端消息
        File consoleLog = new File(tempDir, "console.log");
        collectConsoleMessages(consoleLog);
        logFiles.add(consoleLog);

        // 2. 收集最近活跃的会话日志
        File sessionLog = collectSessionLog(tempDir);
        if (sessionLog != null) {
            logFiles.add(sessionLog);
        }

        // 3. 收集错误信息
        File messagesLog = new File(tempDir, "messages.log");
        collectErrorMessages(messagesLog, throwable);
        logFiles.add(messagesLog);

        return logFiles;
    }

    /**
     * 收集最近200条终端消息
     *
     * @param file 目标文件
     * @throws IOException IO异常
     */
    private void collectConsoleMessages(File file) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, StandardCharsets.UTF_8))) {
            writer.write("最近200条终端消息:\n");
            
            // 尝试获取服务器日志文件
            File serverLog = new File("logs/latest.log");
            if (serverLog.exists() && serverLog.isFile()) {
                // 读取日志文件的最后部分
                try (RandomAccessFile raf = new RandomAccessFile(serverLog, "r")) {
                    long fileLength = raf.length();
                    long startPos = Math.max(0, fileLength - 1024 * 1024); // 读取最后1MB
                    raf.seek(startPos);
                    
                    List<String> lines = new ArrayList<>();
                    String line;
                    while ((line = raf.readLine()) != null) {
                        lines.add(line);
                    }
                    
                    // 取最近200条
                    int startIndex = Math.max(0, lines.size() - 200);
                    for (int i = startIndex; i < lines.size(); i++) {
                        writer.write(lines.get(i));
                        writer.newLine();
                    }
                }
            } else {
                writer.write("无法找到服务器日志文件\n");
            }
        }
    }

    /**
     * 收集最近活跃的会话日志
     *
     * @param tempDir 临时目录
     * @return 会话日志文件
     * @throws IOException IO异常
     */
    private File collectSessionLog(File tempDir) throws IOException {
        // 检查最近活跃的会话
        if (plugin instanceof FancyHelper) {
            FancyHelper fancyHelper = (FancyHelper) plugin;
            org.YanPl.manager.CLIManager cliManager = fancyHelper.getCliManager();
            
            // 获取所有活跃的会话
            java.util.Map<java.util.UUID, org.YanPl.model.DialogueSession> sessions = cliManager.getSessions();
            if (!sessions.isEmpty()) {
                // 找到最近活跃的会话
                org.YanPl.model.DialogueSession mostRecentSession = null;
                long mostRecentTime = 0;
                
                for (org.YanPl.model.DialogueSession session : sessions.values()) {
                    long lastActivityTime = session.getLastActivityTime();
                    if (lastActivityTime > mostRecentTime) {
                        mostRecentTime = lastActivityTime;
                        mostRecentSession = session;
                    }
                }
                
                // 如果找到最近活跃的会话，读取其日志文件
                if (mostRecentSession != null) {
                    String logFilePath = mostRecentSession.getLogFilePath();
                    if (logFilePath != null) {
                        java.io.File logFile = new java.io.File(logFilePath);
                        if (logFile.exists() && logFile.isFile()) {
                            // 创建临时文件
                            String timestamp = new java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new java.util.Date());
                            java.io.File sessionLog = new java.io.File(tempDir, timestamp + ".log");
                            
                            // 复制日志文件内容
                            try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(logFile, java.nio.charset.StandardCharsets.UTF_8));
                                 java.io.BufferedWriter writer = new java.io.BufferedWriter(new java.io.FileWriter(sessionLog, java.nio.charset.StandardCharsets.UTF_8))) {
                                String line;
                                while ((line = reader.readLine()) != null) {
                                    writer.write(line);
                                    writer.newLine();
                                }
                            }
                            
                            return sessionLog;
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * 收集错误信息
     *
     * @param file 目标文件
     * @param throwable 异常
     * @throws IOException IO异常
     */
    private void collectErrorMessages(File file, Throwable throwable) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, StandardCharsets.UTF_8))) {
            writer.write(getStackTraceString(throwable));
        }
    }

    /**
     * 上传文件到Cloudflare Worker
     *
     * @param files 文件列表
     * @throws IOException IO异常
     */
    private void uploadFiles(List<File> files) throws IOException {
        String boundary = "----WebKitFormBoundary" + UUID.randomUUID().toString();
        HttpURLConnection conn = (HttpURLConnection) new URL(workerUrl).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        conn.setDoOutput(true);
        conn.setConnectTimeout(10000);

        try (OutputStream os = conn.getOutputStream()) {
            for (File file : files) {
                if (file.length() > 500 * 1024) { // 检查文件大小是否超过500KB
                    plugin.getLogger().warning("文件 " + file.getName() + " 大小超过500KB，跳过上传");
                    continue;
                }

                // 写入文件部分
                os.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
                os.write(("Content-Disposition: form-data; name=\"files\"; filename=\"" + file.getName() + "\"\r\n").getBytes(StandardCharsets.UTF_8));
                os.write(("Content-Type: text/plain\r\n").getBytes(StandardCharsets.UTF_8));
                os.write(("\r\n").getBytes(StandardCharsets.UTF_8));

                // 写入文件内容
                try (FileInputStream fis = new FileInputStream(file)) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = fis.read(buffer)) != -1) {
                        os.write(buffer, 0, bytesRead);
                    }
                }

                os.write("\r\n".getBytes(StandardCharsets.UTF_8));
            }

            // 写入结束边界
            os.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        }

        // 读取响应
        int responseCode = conn.getResponseCode();
        if (responseCode == 200) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String response = reader.lines().collect(Collectors.joining("\n"));
                plugin.getLogger().info("已自动向开发者提交崩溃报告，感谢支持。报告编号: " + response);
            }
        } else {
            plugin.getLogger().warning("错误上报失败，响应码: " + responseCode);
        }
    }

    /**
     * 获取异常堆栈字符串
     *
     * @param t 异常
     * @return 堆栈字符串
     */
    private String getStackTraceString(Throwable t) {
        StringBuilder sb = new StringBuilder(t.toString()).append("\n");
        for (StackTraceElement ste : t.getStackTrace()) {
            sb.append("\tat ").append(ste.toString()).append("\n");
        }
        if (t.getCause() != null) {
            sb.append("Caused by: " ).append(getStackTraceString(t.getCause()));
        }
        return sb.toString();
    }
}
