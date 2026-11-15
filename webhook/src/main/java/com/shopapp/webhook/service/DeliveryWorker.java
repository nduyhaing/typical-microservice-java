package com.shopapp.webhook.service;

import com.shopapp.webhook.config.WebhookProperties;
import com.shopapp.webhook.delivery.SendResult;
import com.shopapp.webhook.delivery.WebhookSender;
import com.shopapp.webhook.model.NotificationStatus;
import com.shopapp.webhook.repository.NotificationStore;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Sends due deliveries from the log (decisions.md 5-34): every poll it claims as many due rows as it has free
 * slots ({@code max-concurrent}) and sends each on its own virtual thread, so a slow receiver holds one slot, never
 * the CDC consumer or the other deliveries. A failed attempt is retried after a back-off (10 s, then x6) up to
 * {@code max-attempts}; then, or at once for failures that will not heal, the row is FAILED.
 */
@Slf4j
@Component
public class DeliveryWorker implements DisposableBean {

    private final NotificationStore store;
    private final WebhookSender sender;
    private final WebhookProperties.Delivery limits;
    private final Semaphore slots;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public DeliveryWorker(NotificationStore store, WebhookSender sender, WebhookProperties properties) {
        this.store = store;
        this.sender = sender;
        this.limits = properties.delivery();
        this.slots = new Semaphore(Math.max(1, limits.maxConcurrent()));
    }

    @Scheduled(fixedDelayString = "${shopapp.webhook.delivery.poll-interval-ms:1000}",
        initialDelayString = "${shopapp.webhook.delivery.poll-interval-ms:1000}")
    public void dispatchDue() {
        int free = slots.availablePermits();
        if (free == 0) {
            return;
        }
        List<Long> claimed;
        try {
            claimed = store.claimDue(free, limits.leaseMs());
        } catch (DataAccessException ex) {
            log.warn("Could not claim due webhook deliveries: {}", ex.getMessage());
            return;
        }
        for (Long id : claimed) {
            slots.acquireUninterruptibly();
            executor.execute(() -> {
                try {
                    deliver(id);
                } catch (RuntimeException ex) {
                    // the lease expires and the delivery is claimed again
                    log.warn("Webhook delivery {} could not be completed: {}", id, ex.getMessage());
                } finally {
                    slots.release();
                }
            });
        }
    }

    void deliver(long id) {
        NotificationStore.Due due = store.loadClaimed(id).orElse(null);
        if (due == null) {
            return;
        }
        if (!due.active()) {
            store.cancel(id);
            return;
        }
        SendResult result = sender.send(due.url(), due.secret(), due.eventName(), due.deliveryId(),
            due.payload().getBytes(StandardCharsets.UTF_8));
        int attempts = due.attempts() + 1;
        switch (result.outcome()) {
            case DELIVERED -> store.recordAttempt(id, NotificationStatus.DELIVERED, attempts, result.statusCode(),
                null, null);
            case RETRYABLE_FAILURE -> {
                if (attempts < limits.maxAttempts()) {
                    store.recordAttempt(id, NotificationStatus.RETRYING, attempts, result.statusCode(),
                        result.error(), limits.backoffAfter(attempts));
                } else {
                    store.recordAttempt(id, NotificationStatus.FAILED, attempts, result.statusCode(),
                        result.error(), null);
                }
            }
            case PERMANENT_FAILURE -> store.recordAttempt(id, NotificationStatus.FAILED, attempts,
                result.statusCode(), result.error(), null);
            default -> throw new IllegalStateException("Unknown outcome " + result.outcome());
        }
        log.debug("Webhook delivery {} attempt {}: {}", id, attempts, result);
    }

    @Override
    public void destroy() {
        executor.shutdownNow();
    }
}
