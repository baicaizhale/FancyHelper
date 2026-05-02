package org.YanPl.util;

import org.YanPl.FancyHelper;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 云端错误上报工具类
 */
public class CloudErrorReport {

    private final JavaPlugin plugin;
    private final String workerUrl = "https://report-fancy.baicaizhale.top/";

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

        // 异步执行请求
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

        // 2. 收集CLI会话日志（根据在线CLI人数智能收集）
        List<File> cliSessionLogs = collectCLISessionLogs(tempDir);
        logFiles.addAll(cliSessionLogs);

        // 3. 收集错误信息
        File messagesLog = new File(tempDir, "messages.log");
        collectErrorMessages(messagesLog, throwable);
        logFiles.add(messagesLog);

        // 4. 收集配置文件
        File configFile = collectConfigFile(tempDir);
        if (configFile != null) {
            logFiles.add(configFile);
        }

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
                byte[] content;
                try (RandomAccessFile raf = new RandomAccessFile(serverLog, "r")) {
                    long fileLength = raf.length();
                    long startPos = Math.max(0, fileLength - 1024 * 1024); // 读取最后1MB
                    raf.seek(startPos);
                    content = new byte[(int) (fileLength - startPos)];
                    raf.readFully(content);
                }

                // 显式用 UTF-8 解码（raf.readLine() 用的是 ISO-8859-1，会导致中文乱码）
                String text = new String(content, StandardCharsets.UTF_8);
                String[] lines = text.split("\n", -1);

                // 取最近200条
                int startIndex = Math.max(0, lines.length - 200);
                for (int i = startIndex; i < lines.length; i++) {
                    String line = lines[i];
                    if (line.endsWith("\r")) {
                        line = line.substring(0, line.length() - 1);
                    }
                    writer.write(line);
                    writer.newLine();
                }
            } else {
                writer.write("无法找到服务器日志文件\n");
            }
        }
    }

    /**
     * 收集CLI会话日志（智能收集）
     * 根据当前处于CLI模式的人数，收集对应数量的最新日志文件
     *
     * @param tempDir 临时目录
     * @return 收集的日志文件列表
     * @throws IOException IO异常
     */
    private List<File> collectCLISessionLogs(File tempDir) throws IOException {
        List<File> collectedLogs = new ArrayList<>();

        if (!(plugin instanceof FancyHelper)) {
            return collectedLogs;
        }

        FancyHelper fancyHelper = (FancyHelper) plugin;
        org.YanPl.manager.CLIManager cliManager = fancyHelper.getCliManager();

        // 获取当前处于CLI模式的人数
        int cliPlayerCount = cliManager.getActivePlayersCount();
        if (cliPlayerCount <= 0) {
            return collectedLogs;
        }

        // 获取logs目录
        File logsDir = new File(plugin.getDataFolder(), "logs");
        if (!logsDir.exists() || !logsDir.isDirectory()) {
            return collectedLogs;
        }

        // 获取所有日志文件并按修改时间排序
        File[] logFiles = logsDir.listFiles((dir, name) -> name.endsWith(".log"));
        if (logFiles == null || logFiles.length == 0) {
            return collectedLogs;
        }

        // 按最后修改时间降序排序
        java.util.Arrays.sort(logFiles, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));

        // 根据CLI人数收集对应数量的最新日志文件
        int countToCollect = Math.min(cliPlayerCount, logFiles.length);
        for (int i = 0; i < countToCollect; i++) {
            File logFile = logFiles[i];
            // 创建临时文件，使用原始文件名
            File tempLogFile = new File(tempDir, logFile.getName());

            // 复制日志内容
            try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(logFile, java.nio.charset.StandardCharsets.UTF_8));
                 java.io.BufferedWriter writer = new java.io.BufferedWriter(new java.io.FileWriter(tempLogFile, java.nio.charset.StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    writer.write(line);
                    writer.newLine();
                }
            }

            collectedLogs.add(tempLogFile);
        }

        return collectedLogs;
    }

    /**
     * 收集配置文件
     *
     * @param tempDir 临时目录
     * @return 配置文件
     * @throws IOException IO异常
     */
    private File collectConfigFile(File tempDir) throws IOException {
        // 获取插件的配置文件
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        if (configFile.exists() && configFile.isFile()) {
            // 创建临时配置文件
            File tempConfig = new File(tempDir, "config.yml");
            
            // 复制配置文件内容
            try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(configFile, java.nio.charset.StandardCharsets.UTF_8));
                 java.io.BufferedWriter writer = new java.io.BufferedWriter(new java.io.FileWriter(tempConfig, java.nio.charset.StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    writer.write(line);
                    writer.newLine();
                }
            }
            
            return tempConfig;
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
