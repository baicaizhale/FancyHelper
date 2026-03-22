package org.YanPl.manager;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

public class WebReaderTest {

    @Test
    public void testWebReaderTool() {
        // 直接测试网页访问功能
        try {
            System.out.println("测试网页阅读工具...");
            
            // 尝试访问一个简单的网页
            String testUrl = "https://example.com";
            System.out.println("尝试访问: " + testUrl);
            
            // 创建HTTP客户端
            HttpClient httpClient = HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(15))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();

            // 构造真实用户的请求头
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(testUrl))
                    .timeout(java.time.Duration.ofSeconds(20))
                    // 基础头信息
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                    .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                    .header("Accept-Encoding", "gzip, deflate, br")
                    // 安全相关头
                    .header("Sec-Ch-Ua", "\"Not_A Brand\";v=\"8\", \"Chromium\";v=\"120\", \"Google Chrome\";v=\"120\"")
                    .header("Sec-Ch-Ua-Mobile", "?0")
                    .header("Sec-Ch-Ua-Platform", "\"Windows\"")
                    // 浏览行为头
                    .header("Upgrade-Insecure-Requests", "1")
                    .header("Sec-Fetch-Dest", "document")
                    .header("Sec-Fetch-Mode", "navigate")
                    .header("Sec-Fetch-Site", "none")
                    .header("Sec-Fetch-User", "?1")
                    // 缓存和引用头
                    .header("Cache-Control", "max-age=0")
                    .header("Referer", "https://www.google.com/")
                    .header("DNT", "1")
                    .GET()
                    .build();

            // 发送请求
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() != 200) {
                System.out.println("HTTP请求失败，状态码: " + response.statusCode());
                return;
            }

            // 等待5秒，确保网页完全加载
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // 处理响应内容，自动检测编码和压缩
            byte[] bodyBytes = response.body();
            String contentType = response.headers().firstValue("Content-Type").orElse("text/html");
            String contentEncoding = response.headers().firstValue("Content-Encoding").orElse("identity");
            
            // 处理压缩内容
            byte[] decompressedBytes = bodyBytes;
            try {
                if (contentEncoding.contains("gzip")) {
                    java.io.ByteArrayInputStream bis = new java.io.ByteArrayInputStream(bodyBytes);
                    java.util.zip.GZIPInputStream gis = new java.util.zip.GZIPInputStream(bis);
                    java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                    byte[] buffer = new byte[1024];
                    int len;
                    while ((len = gis.read(buffer)) > 0) {
                        bos.write(buffer, 0, len);
                    }
                    gis.close();
                    bos.close();
                    decompressedBytes = bos.toByteArray();
                } else if (contentEncoding.contains("deflate")) {
                    java.io.ByteArrayInputStream bis = new java.io.ByteArrayInputStream(bodyBytes);
                    java.util.zip.InflaterInputStream iis = new java.util.zip.InflaterInputStream(bis);
                    java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                    byte[] buffer = new byte[1024];
                    int len;
                    while ((len = iis.read(buffer)) > 0) {
                        bos.write(buffer, 0, len);
                    }
                    iis.close();
                    bos.close();
                    decompressedBytes = bos.toByteArray();
                }
            } catch (Exception e) {
                // 解压失败，使用原始字节
                decompressedBytes = bodyBytes;
            }
            
            // 尝试从Content-Type中提取编码
            String charset = "UTF-8"; // 默认编码
            if (contentType.contains("charset=")) {
                int charsetIndex = contentType.indexOf("charset=");
                charset = contentType.substring(charsetIndex + 8).trim();
                // 移除可能的引号
                if (charset.startsWith("\"")) {
                    charset = charset.substring(1, charset.length() - 1);
                }
            }
            
            // 将字节数组转换为字符串
            String htmlContent;
            try {
                htmlContent = new String(decompressedBytes, charset);
            } catch (java.io.UnsupportedEncodingException e) {
                // 如果编码不支持，回退到UTF-8
                htmlContent = new String(decompressedBytes, StandardCharsets.UTF_8);
            }

            // 解析HTML内容
            org.jsoup.nodes.Document doc = org.jsoup.Jsoup.parse(htmlContent);

            // 提取标题
            String title = doc.title();

            // 提取正文内容，去除脚本和样式
            doc.select("script, style").remove();
            String bodyText = doc.body().text();

            // 限制内容长度
            final int MAX_CONTENT_LENGTH = 5000;
            if (bodyText.length() > MAX_CONTENT_LENGTH) {
                bodyText = bodyText.substring(0, MAX_CONTENT_LENGTH) + "... (内容过长，已截断)";
            }

            // 构建结果
            StringBuilder result = new StringBuilder();
            result.append("网页标题: ").append(title).append("\n");
            result.append("网页URL: " + testUrl + "\n");
            result.append("\n正文内容:\n");
            result.append(bodyText);

            // 打印结果
            System.out.println("访问成功！");
            System.out.println("网页标题: " + title);
            System.out.println("结果长度: " + result.length());
            System.out.println("前200个字符: " + result.substring(0, Math.min(200, result.length())) + "...");
            
        } catch (Exception e) {
            System.out.println("访问失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
