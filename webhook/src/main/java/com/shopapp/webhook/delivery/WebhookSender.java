package com.shopapp.webhook.delivery;

import com.shopapp.webhook.config.WebhookProperties;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.util.Timeout;

/**
 * POSTs one signed delivery (decisions.md 5-33). Apache HttpClient 5 with:
 * <ul>
 *   <li>a DNS resolver that is the {@link SsrfGuard}: the host is resolved once, every address checked, and the
 *       socket connects to exactly those addresses (the Host header and TLS name stay the URL's host), so DNS
 *       rebinding between the check and the connect is impossible; IP literals go through the same check;</li>
 *   <li>no redirects, no automatic retries, no cookies, no proxy;</li>
 *   <li>connect timeout 2 s, read (socket inactivity / response head) timeout 5 s, and an overall deadline that
 *       aborts a receiver trickling bytes; the response body is never read.</li>
 * </ul>
 */
public class WebhookSender implements AutoCloseable {

    public static final String DELIVERY_HEADER = "X-Shopapp-Delivery";
    public static final String EVENT_HEADER = "X-Shopapp-Event";
    public static final String TIMESTAMP_HEADER = "X-Shopapp-Timestamp";
    private static final int MAX_ERROR_LENGTH = 500;

    private final SsrfGuard guard;
    private final WebhookProperties.Delivery limits;
    private final CloseableHttpClient client;
    private final ScheduledExecutorService deadlines;

    public WebhookSender(SsrfGuard guard, WebhookProperties properties) {
        this.guard = guard;
        this.limits = properties.delivery();
        DnsResolver resolver = new DnsResolver() {
            @Override
            public InetAddress[] resolve(String host) throws UnknownHostException {
                return guard.resolveForConnect(host);
            }

            @Override
            public String resolveCanonicalHostname(String host) {
                return host;
            }
        };
        int connections = Math.max(2, limits.maxConcurrent());
        this.client = HttpClients.custom()
            .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                .setDnsResolver(resolver)
                .setDefaultConnectionConfig(ConnectionConfig.custom()
                    .setConnectTimeout(Timeout.ofMilliseconds(limits.connectTimeoutMs()))
                    .setSocketTimeout(Timeout.ofMilliseconds(limits.readTimeoutMs()))
                    .build())
                .setMaxConnTotal(connections)
                .setMaxConnPerRoute(connections)
                .build())
            .setDefaultRequestConfig(RequestConfig.custom()
                .setResponseTimeout(Timeout.ofMilliseconds(limits.readTimeoutMs()))
                .setConnectionRequestTimeout(Timeout.ofMilliseconds(limits.connectTimeoutMs()))
                .setRedirectsEnabled(false)
                .build())
            .disableRedirectHandling()
            .disableAutomaticRetries()
            .disableCookieManagement()
            .disableAuthCaching()
            .disableContentCompression()
            .setUserAgent("shopapp-webhook/1.0")
            .build();
        this.deadlines = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon()
            .name("webhook-deadline").factory());
    }

    /**
     * Sends {@code body} to {@code url}, signed with {@code secret}; never throws.
     */
    public SendResult send(String url, String secret, String eventName, UUID deliveryId, byte[] body) {
        if (body.length > limits.maxPayloadBytes()) {
            return SendResult.permanent(null, "Payload of " + body.length + " bytes exceeds the limit of "
                + limits.maxPayloadBytes());
        }
        URI uri;
        try {
            uri = guard.checkSyntax(url);
        } catch (UnsafeUrlException ex) {
            return SendResult.permanent(null, ex.getMessage());
        }
        HttpPost post = new HttpPost(uri);
        post.setEntity(new ByteArrayEntity(body, ContentType.APPLICATION_JSON));
        String timestamp = Long.toString(Instant.now().getEpochSecond());
        post.setHeader(HmacSigner.HEADER, HmacSigner.sign(timestamp, body, secret));
        post.setHeader(DELIVERY_HEADER, deliveryId.toString());
        post.setHeader(EVENT_HEADER, eventName);
        post.setHeader(TIMESTAMP_HEADER, timestamp);
        ScheduledFuture<?> deadline = deadlines.schedule(post::cancel, limits.totalTimeoutMs(),
            TimeUnit.MILLISECONDS);
        try (ClassicHttpResponse response = client.executeOpen(null, post, null)) {
            int status = response.getCode();
            // the body is not read: drop the connection instead of draining whatever the receiver sends
            post.cancel();
            return classify(status);
        } catch (IOException | RuntimeException ex) {
            if (isBlocked(ex)) {
                return SendResult.permanent(null, truncate(rootMessage(ex)));
            }
            if (deadline.isDone() && post.isCancelled()) {
                return SendResult.retryable(null, "Aborted after " + limits.totalTimeoutMs() + " ms");
            }
            return SendResult.retryable(null, truncate(ex.getClass().getSimpleName() + ": " + rootMessage(ex)));
        } finally {
            deadline.cancel(false);
        }
    }

    private static SendResult classify(int status) {
        if (status >= 200 && status < 300) {
            return SendResult.delivered(status);
        }
        if (status >= 500 || status == 408 || status == 429) {
            return SendResult.retryable(status, "HTTP " + status);
        }
        if (status >= 300 && status < 400) {
            return SendResult.permanent(status, "HTTP " + status + " (redirects are not followed)");
        }
        return SendResult.permanent(status, "HTTP " + status);
    }

    private static boolean isBlocked(Throwable ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause() == cause ? null : cause.getCause()) {
            if (cause instanceof BlockedAddressException) {
                return true;
            }
        }
        return false;
    }

    private static String rootMessage(Throwable ex) {
        Throwable root = ex;
        while (root.getCause() != null && root.getCause() != root) {
            if (root instanceof BlockedAddressException) {
                break;
            }
            root = root.getCause();
        }
        return root.getMessage() != null ? root.getMessage() : root.getClass().getSimpleName();
    }

    private static String truncate(String message) {
        return message.length() <= MAX_ERROR_LENGTH ? message : message.substring(0, MAX_ERROR_LENGTH);
    }

    @Override
    public void close() throws IOException {
        deadlines.shutdownNow();
        client.close();
    }
}
