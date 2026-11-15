package com.shopapp.webhook.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.sql.SQLTransientConnectionException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.BackOffExecution;
import org.springframework.util.backoff.ExponentialBackOff;

class CdcErrorHandlingTest {

    private final CdcErrorHandling handling = new CdcErrorHandling(1_000, 2.0, 120_000, 2, 1_000);
    private final ConsumerRecord<String, String> record = new ConsumerRecord<>("t", 0, 0, "k", "v");

    private static RuntimeException wrapped(Throwable cause) {
        return new ListenerExecutionFailedException("listener failed", cause);
    }

    @Test
    void databaseUnavailable_isInfrastructure() {
        assertThat(CdcErrorHandling.isInfrastructure(wrapped(new DataAccessResourceFailureException("down"))))
            .isTrue();
        assertThat(CdcErrorHandling.isInfrastructure(wrapped(new CannotGetJdbcConnectionException("no pool"))))
            .isTrue();
        assertThat(CdcErrorHandling.isInfrastructure(wrapped(new CannotCreateTransactionException("tx",
            new SQLTransientConnectionException("timeout"))))).isTrue();
        assertThat(CdcErrorHandling.isInfrastructure(wrapped(new IOException("reset")))).isTrue();
    }

    @Test
    void otherFailures_areNotInfrastructure() {
        assertThat(CdcErrorHandling.isInfrastructure(wrapped(new IllegalStateException("bug")))).isFalse();
        assertThat(CdcErrorHandling.isInfrastructure(wrapped(new DataIntegrityViolationException("x")))).isFalse();
        assertThat(CdcErrorHandling.isInfrastructure(wrapped(new PoisonMessageException("bad")))).isFalse();
    }

    @Test
    void infrastructureBackOff_growsToTheCap_andNeverStops() {
        BackOff backOff = handling.backOffFor(record, wrapped(new DataAccessResourceFailureException("down")));
        assertThat(backOff).isInstanceOf(ExponentialBackOff.class);
        BackOffExecution execution = backOff.start();
        assertThat(execution.nextBackOff()).isEqualTo(1_000);
        assertThat(execution.nextBackOff()).isEqualTo(2_000);
        long last = 0;
        for (int i = 0; i < 10_000; i++) {
            last = execution.nextBackOff();
            assertThat(last).isNotEqualTo(BackOffExecution.STOP);
        }
        assertThat(last).isEqualTo(120_000);
    }

    @Test
    void otherFailures_areRetriedAFewTimes_thenDeadLettered() {
        BackOffExecution execution = handling.backOffFor(record, wrapped(new IllegalStateException("bug"))).start();
        assertThat(execution.nextBackOff()).isEqualTo(1_000);
        assertThat(execution.nextBackOff()).isEqualTo(1_000);
        assertThat(execution.nextBackOff()).isEqualTo(BackOffExecution.STOP);
    }

    @Test
    void deadLetterTopic_isOwnedByWebhook() {
        assertThat(CdcErrorHandling.deadLetterTopic("dborder.public.order")).isEqualTo("dborder.public.order-webhook-dlt");
    }
}
