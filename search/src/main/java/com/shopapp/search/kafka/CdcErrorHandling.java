package com.shopapp.search.kafka;

import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import com.shopapp.search.service.ProductUnavailableException;
import java.io.IOException;
import java.io.UncheckedIOException;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.data.elasticsearch.UncategorizedElasticsearchException;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.ExponentialBackOff;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Failure handling of the product CDC listener (decisions.md 4-70). The record is retried in place (blocking, the
 * partition waits, so no later event of the product overtakes it):
 * <ul>
 *   <li>infrastructure failures (product unavailable, Elasticsearch unreachable / 5xx / 429) — exponential back-off
 *       without an end (1 s doubling to 2 min): an outage delays search, it never loses an event;</li>
 *   <li>any other failure (a bug, an Elasticsearch 4xx) — a few retries, then the dead-letter topic;</li>
 *   <li>{@link PoisonMessageException} (not a Debezium product message) — the dead-letter topic at once.</li>
 * </ul>
 * The 2 min cap stays below the consumer's {@code max.poll.interval.ms} (5 min), so the back-off sleep never costs
 * the group membership. Replaces the bounded retry-topic chain, which dead-lettered events after ~1 min of outage.
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
public class CdcErrorHandling {

    private final long initialIntervalMs;
    private final double multiplier;
    private final long maxIntervalMs;
    private final long otherAttempts;
    private final long otherIntervalMs;

    public CdcErrorHandling(@Value("${search.cdc.retry.initial-interval-ms:1000}") long initialIntervalMs,
                            @Value("${search.cdc.retry.multiplier:2}") double multiplier,
                            @Value("${search.cdc.retry.max-interval-ms:120000}") long maxIntervalMs,
                            @Value("${search.cdc.retry.other-attempts:2}") long otherAttempts,
                            @Value("${search.cdc.retry.other-interval-ms:1000}") long otherIntervalMs) {
        this.initialIntervalMs = initialIntervalMs;
        this.multiplier = multiplier;
        this.maxIntervalMs = maxIntervalMs;
        this.otherAttempts = otherAttempts;
        this.otherIntervalMs = otherIntervalMs;
    }

    /** Boot's listener container factory picks this handler up. Dead letters go to {@code <topic>-dlt}. */
    @Bean
    DefaultErrorHandler cdcErrorHandler(KafkaOperations<?, ?> kafka) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafka,
            (record, ex) -> new TopicPartition(record.topic() + "-dlt", -1));
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer);
        handler.setBackOffFunction(this::backOffFor);
        handler.addNotRetryableExceptions(PoisonMessageException.class);
        handler.setRetryListeners((record, ex, attempt) -> log.warn(
            "Product CDC message (offset {}, key {}) failed, attempt {}; retrying: {}", record.offset(),
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
            if (cause instanceof ProductUnavailableException
                || cause instanceof DataAccessResourceFailureException
                || cause instanceof TransientDataAccessException
                || cause instanceof IOException
                || cause instanceof UncheckedIOException) {
                return true;
            }
            if (cause instanceof ElasticsearchException es && isUnavailable(es.status())) {
                return true;
            }
            if (cause instanceof UncategorizedElasticsearchException es && es.getStatusCode() != null
                && isUnavailable(es.getStatusCode())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isUnavailable(int status) {
        return status == 429 || status >= 500;
    }

    private static String rootMessage(Throwable ex) {
        Throwable root = ex;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getMessage();
    }
}
