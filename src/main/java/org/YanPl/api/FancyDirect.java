package org.YanPl.api;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Base64;

/**
 * FancyConsole API 直连通道。
 *
 * 国内访问 Cloudflare 隧道（api.fancy.baicaizhale.top）延迟高且丢包严重，
 * 而源站 114.66.51.188 的 8443 非标端口实测畅通（运营商备案过滤只拦 80/443）。
 * 本类封装"直连优先、失败冷却、自动回退 CF"的选择逻辑：
 *
 * - 直连端点使用自签证书，不走公共 CA；插件端通过 SPKI SHA-256（公钥哈希，
 *   与证书有效期无关）锁定信任，服务器端换证书只要不换密钥就无需发版。
 * - SSLContext 为组合信任：先走 JVM 默认信任链（覆盖 CF 的 Let's Encrypt 证书），
 *   默认链不通过且命中 SPKI 锁定时才放行。同一个 HttpClient 可同时服务两种端点，
 *   直连/回退切换不需要重建客户端。
 * - 直连出现链路级失败（连接被拒/重置/SSL 握手失败等）后进入冷却期，
 *   期间 baseUrl() 一律返回 CF 地址，避免每个请求都先撞一次墙。
 */
public final class FancyDirect {

    /** 源站直连端点（非标端口免备案；Host 由 nginx 强制改写为 API 域名，TLS 握手不含域名） */
    private static final String DIRECT_BASE = "https://114.66.51.188:8443";

    /** CF 隧道端点（回退用，也是浏览器用户的主入口） */
    private static final String CF_BASE = "https://api.fancy.baicaizhale.top";

    /** 源站自签证书公钥的 SPKI SHA-256（Base64）。服务器端换密钥时必须随版本更新此值。 */
    private static final String PINNED_SPKI_SHA256 = "9eh4mdHaAnZZ+T1DPf+jYrU4i+xUPET8ieDNiISu0wM=";

    /** 直连失败后的冷却时长：期间所有 FancyConsole 请求走 CF */
    private static final long FAILURE_COOLDOWN_MS = 10 * 60 * 1000L;

    private static volatile long directRetryAfterMs = 0L;
    private static volatile SSLContext cachedContext;

    private FancyDirect() {
    }

    /** 当前应使用的 FancyConsole API 基地址（直连优先，冷却期回退 CF） */
    public static String baseUrl() {
        return System.currentTimeMillis() >= directRetryAfterMs ? DIRECT_BASE : CF_BASE;
    }

    /** URL 是否指向直连端点 */
    public static boolean isDirect(String url) {
        return url != null && url.startsWith(DIRECT_BASE);
    }

    /** 直连 URL → CF URL（路径/查询保留）；非直连 URL 原样返回 */
    public static String toCfUrl(String url) {
        if (url == null) {
            return null;
        }
        return url.startsWith(DIRECT_BASE) ? CF_BASE + url.substring(DIRECT_BASE.length()) : url;
    }

    /** 直连链路失败后调用：进入冷却期 */
    public static void markDirectFailed() {
        directRetryAfterMs = System.currentTimeMillis() + FAILURE_COOLDOWN_MS;
    }

    /**
     * 组合信任 SSLContext：JVM 默认信任链 ∪ SPKI 锁定。
     * 初始化失败时返回 null（调用方保持默认信任：直连不可用，CF 不受影响）。
     */
    public static SSLContext sslContext() {
        SSLContext ctx = cachedContext;
        if (ctx != null) {
            return ctx;
        }
        synchronized (FancyDirect.class) {
            if (cachedContext == null) {
                try {
                    TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                    tmf.init((KeyStore) null);
                    X509TrustManager system = null;
                    for (TrustManager tm : tmf.getTrustManagers()) {
                        if (tm instanceof X509TrustManager) {
                            system = (X509TrustManager) tm;
                            break;
                        }
                    }
                    final X509TrustManager systemTm = system;
                    X509TrustManager composite = new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                            if (systemTm != null) {
                                systemTm.checkClientTrusted(chain, authType);
                            }
                        }

                        @Override
                        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                            try {
                                if (systemTm != null) {
                                    systemTm.checkServerTrusted(chain, authType);
                                    return;
                                }
                            } catch (CertificateException ignored) {
                                // 默认链不通过 → 尝试 SPKI 锁定
                            }
                            if (chain == null || chain.length == 0) {
                                throw new CertificateException("证书链为空");
                            }
                            if (!PINNED_SPKI_SHA256.equals(spkiSha256(chain[0]))) {
                                throw new CertificateException("证书既非公共 CA 签发，也未命中直连 SPKI 锁定");
                            }
                        }

                        @Override
                        public X509Certificate[] getAcceptedIssuers() {
                            return systemTm != null ? systemTm.getAcceptedIssuers() : new X509Certificate[0];
                        }
                    };
                    SSLContext sc = SSLContext.getInstance("TLS");
                    sc.init(null, new TrustManager[]{composite}, new SecureRandom());
                    cachedContext = sc;
                } catch (Exception e) {
                    return null;
                }
            }
            return cachedContext;
        }
    }

    /** 给 HttpClient.Builder 附加组合信任（初始化失败时原样返回） */
    public static HttpClient.Builder trust(HttpClient.Builder builder) {
        SSLContext sc = sslContext();
        return sc != null ? builder.sslContext(sc) : builder;
    }

    /**
     * 是否"链路级"失败（尚未取得响应即失败，此时重试/回退不会造成重复投递）：
     * 连接类与 SSL 握手类错误。读超时（响应已开始）不算，避免重复计费。
     */
    public static boolean isLinkFailure(IOException e) {
        if (e instanceof ConnectException || e instanceof HttpConnectTimeoutException || e instanceof SSLException) {
            return true;
        }
        String m = e.getMessage();
        if (m == null) {
            return false;
        }
        String lm = m.toLowerCase();
        return lm.contains("connection reset") || lm.contains("connection refused")
                || lm.contains("no route to host") || lm.contains("unreachable")
                || lm.contains("header parser received no bytes");
    }

    /**
     * 若请求指向直连端点且失败为链路级：标记冷却并返回改写为 CF 地址的等价请求；
     * 否则返回 null（调用方原样抛出异常）。
     * 注意：不能用 HttpRequest.newBuilder(HttpRequest)（JDK 21 才有），此处逐字段重建；
     * BodyPublisher 复用原对象——仅链路级失败（请求体尚未发出）时才会走到这里，与
     * LLMClient 既有重试的复用方式一致。
     */
    public static HttpRequest fallbackRequest(HttpRequest failed, IOException cause) {
        if (!isDirect(failed.uri().toString()) || !isLinkFailure(cause)) {
            return null;
        }
        markDirectFailed();
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(toCfUrl(failed.uri().toString())));
        failed.headers().map().forEach((name, values) -> values.forEach(v -> builder.header(name, v)));
        if (failed.timeout().isPresent()) {
            builder.timeout(failed.timeout().get());
        }
        if (failed.expectContinue()) {
            builder.expectContinue(true);
        }
        if (failed.bodyPublisher().isPresent()) {
            builder.method(failed.method(), failed.bodyPublisher().get());
        } else {
            builder.method(failed.method(), HttpRequest.BodyPublishers.noBody());
        }
        return builder.build();
    }

    /**
     * 单次发送 + 直连→CF 自动回退（供没有重试基础设施的调用点使用）。
     */
    public static <T> HttpResponse<T> sendWithFallback(HttpClient client, HttpRequest request,
                                                       HttpResponse.BodyHandler<T> handler)
            throws IOException, InterruptedException {
        try {
            return client.send(request, handler);
        } catch (IOException e) {
            HttpRequest cf = fallbackRequest(request, e);
            if (cf == null) {
                throw e;
            }
            return client.send(cf, handler);
        }
    }

    private static String spkiSha256(X509Certificate cert) throws CertificateException {
        try {
            byte[] spki = cert.getPublicKey().getEncoded(); // X.509 SubjectPublicKeyInfo DER
            return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(spki));
        } catch (Exception e) {
            throw new CertificateException("SPKI 摘要计算失败", e);
        }
    }
}
