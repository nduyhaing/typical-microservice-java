package com.shopapp.webhook.kafka;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.sql.SQLRecoverableException;
import java.sql.SQLTransientException;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.ExponentialBackOff;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Failure handling of the CDC listeners, search's design (decisions.md 4-70, 5-32). The record is retried in place
 * (blocking: the partition waits, so no later change of the row overtakes it):
 * <ul>
 *   <li>infrastructure failures (webhook's database unavailable) — exponential back-off without an end (1 s
 *       doubling to 2 min): an outage delays webhooks, it never loses an event;</li>
 *   <li>any other failure (a bug) — a few retries, then the dead-letter topic {@code <topic>-webhook-dlt};</li>
 *   <li>{@link PoisonMessageException} (not a Debezium row event) — the dead-letter topic at once.</li>
 * </ul>
 * The listeners never call a receiver (they only write the delivery log), so a slow or failing receiver cannot hold
 * a partition. The 2 min cap stays below {@code max.poll.interval.ms} (5 min).
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
public class CdcErrorHandling {

    private final long initialIntervalMs;
    private final double multiplier;
    private final long maxIntervalMs;
    private final long otherAttempts;
    private final long otherIntervalMs;

    public CdcErrorHandling(@Value("${shopapp.webhook.cdc.retry.initial-interval-ms:1000}") long initialIntervalMs,
                            @Value("${shopapp.webhook.cdc.retry.multiplier:2}") double multiplier,
                            @Value("${shopapp.webhook.cdc.retry.max-interval-ms:120000}") long maxIntervalMs,
                            @Value("${shopapp.webhook.cdc.retry.other-attempts:2}") long otherAttempts,
                            @Value("${shopapp.webhook.cdc.retry.other-interval-ms:1000}") long otherIntervalMs) {
        this.initialIntervalMs = initialIntervalMs;
        this.multiplier = multiplier;
        this.maxIntervalMs = maxIntervalMs;
        this.otherAttempts = otherAttempts;
        this.otherIntervalMs = otherIntervalMs;
    }

    /** webhook's own dead-letter topic for a CDC topic (search uses {@code <topic>-dlt}). */
    public static String deadLetterTopic(String topic) {
        return topic + "-webhook-dlt";
    }

    /** Boot's listener container factory picks this handler up. */
    @Bean
    DefaultErrorHandler cdcErrorHandler(KafkaOperations<?, ?> kafka) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafka,
            (record, ex) -> new TopicPartition(deadLetterTopic(record.topic()), -1));
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer);
        handler.setBackOffFunction(this::backOffFor);
        handler.addNotRetryableExceptions(PoisonMessageException.class);
        handler.setRetryListeners((record, ex, attempt) -> log.warn(
            "CDC message ({} offset {}, key {}) failed, attempt {}; retrying: {}", record.topic(), record.offset(),
            record.key(), attempt, rootMessage(ex)));
        return handler;
    }

    BackOff backOffFor(ConsumerRecord<?, ?> record, Exception ex) {
        if (isInfrastructure(ex)) {
            ExponentialBackOff backOff = new ExponentialBackOff(initialIntervalMs, multiplier);
            backOff.setMaxInterval(maxIntervalMs);
            return backOff;
        }
        return new FixedBackOff(otherIntervalMs, otherAttempts);
    }

    /** True when something in the cause chain says "a dependency is down" rather than "this message is bad". */
    static boolean isInfrastructure(Throwable ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause() == cause ? null : cause.getCause()) {
            if (cause instanceof PoisonMessageException) {
                return false;
            }
            if (cause instanceof DataAccessResourceFailureException
                || cause instanceof TransientDataAccessException
                || cause instanceof CannotCreateTransactionException
                || cause instanceof SQLTransientException
                || cause instanceof SQLRecoverableException
                || cause instanceof IOException
                || cause instanceof UncheckedIOException) {
                return true;
            }
        }
        return false;
    }

    private static String rootMessage(Throwable ex) {
        Throwable root = ex;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getMessage();
    }
}
