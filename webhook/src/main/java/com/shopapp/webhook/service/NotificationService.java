package com.shopapp.webhook.service;

import com.shopapp.webhook.model.EventName;
import com.shopapp.webhook.model.Webhook;
import com.shopapp.webhook.repository.NotificationStore;
import com.shopapp.webhook.repository.WebhookRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Queues deliveries in the log (decisions.md 5-34): one row per active subscriber, sent later by the
 * {@link DeliveryWorker}. The body is fixed here, so every attempt sends (and signs) the same bytes:
 * {@code {"deliveryId":"...","event":"...","createdAt":"...","data":{...}}}.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    public static final String TEST_EVENT = "PING";

    private final WebhookRepository webhookRepository;
    private final NotificationStore store;
    private final ObjectMapper mapper;

    /**
     * Queues {@code event} for every active webhook subscribed to it.
     *
     * @param sourceKey identifies the source message; a second call with the same key queues nothing again
     * @return how many deliveries were queued
     */
    @Transactional
    public int enqueue(EventName event, ObjectNode data, String sourceKey) {
        int queued = 0;
        for (Webhook webhook : webhookRepository.findActiveSubscribers(event)) {
            if (insert(webhook.getId(), event.name(), data, sourceKey) != null) {
                queued++;
            }
        }
        return queued;
    }

    /** Queues a test delivery for one webhook, whatever its subscriptions. */
    @Transactional
    public UUID enqueueTest(long webhookId) {
        ObjectNode data = mapper.createObjectNode();
        data.put("webhookId", webhookId);
        data.put("message", "Test delivery from shop_app");
        return insert(webhookId, TEST_EVENT, data, "test:" + UUID.randomUUID());
    }

    private UUID insert(long webhookId, String eventName, ObjectNode data, String sourceKey) {
        UUID deliveryId = UUID.randomUUID();
        ObjectNode body = mapper.createObjectNode();
        body.put("deliveryId", deliveryId.toString());
        body.put("event", eventName);
        body.put("createdAt", Instant.now().truncatedTo(ChronoUnit.MILLIS).toString());
        body.set("data", data);
        String payload = mapper.writeValueAsString(body);
        boolean inserted = store.insert(webhookId, eventName, deliveryId, sourceKey, payload, sha256(payload));
        return inserted ? deliveryId : null;
    }

    private static String sha256(String payload) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
