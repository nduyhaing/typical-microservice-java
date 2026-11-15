package com.shopapp.webhook.config;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code shopapp.webhook.*}: delivery limits and the SSRF allow-list (decisions.md 5-33, 5-34).
 *
 * @param allowedHosts host names exempt from the private-address refusal (exact, case-insensitive), for a test
 *                     receiver on the compose network; empty in production
 * @param delivery     outbound request limits and the retry schedule
 */
@ConfigurationProperties(prefix = "shopapp.webhook")
public record WebhookProperties(@DefaultValue List<String> allowedHosts, @DefaultValue Delivery delivery) {

    /** The allow-list, trimmed and lower-cased. */
    public Set<String> allowedHostSet() {
        return allowedHosts.stream()
            .map(String::trim)
            .filter(host -> !host.isEmpty())
            .map(host -> host.toLowerCase(Locale.ROOT))
            .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Outbound delivery settings.
     *
     * @param connectTimeoutMs  TCP connect timeout
     * @param readTimeoutMs     socket inactivity timeout (and the wait for the response head)
     * @param totalTimeoutMs    the whole request is aborted after this, however slowly the receiver trickles
     * @param maxAttempts       attempts per delivery, the first one included
     * @param initialBackoffMs  wait before the second attempt
     * @param backoffMultiplier factor between later waits
     * @param maxConcurrent     deliveries in flight at once
     * @param pollIntervalMs    how often due deliveries are looked for
     * @param leaseMs           a claimed delivery whose worker died is retried after this
     * @param maxPayloadBytes   larger payloads are not sent (logged as failed)
     */
    public record Delivery(
        @DefaultValue("2000") long connectTimeoutMs,
        @DefaultValue("5000") long readTimeoutMs,
        @DefaultValue("10000") long totalTimeoutMs,
        @DefaultValue("3") int maxAttempts,
        @DefaultValue("10000") long initialBackoffMs,
        @DefaultValue("6") double backoffMultiplier,
        @DefaultValue("8") int maxConcurrent,
        @DefaultValue("1000") long pollIntervalMs,
        @DefaultValue("60000") long leaseMs,
        @DefaultValue("65536") int maxPayloadBytes) {

        /** Wait after the given number of failed attempts (1 = after the first). */
        public long backoffAfter(int failedAttempts) {
            double delay = initialBackoffMs * Math.pow(backoffMultiplier, Math.max(0, failedAttempts - 1));
            return (long) Math.min(delay, 24d * 60 * 60 * 1000);
        }
    }
}
