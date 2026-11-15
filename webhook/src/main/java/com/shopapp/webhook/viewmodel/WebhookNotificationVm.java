package com.shopapp.webhook.viewmodel;

import com.shopapp.webhook.model.NotificationStatus;
import com.shopapp.webhook.model.WebhookEventNotification;
import java.time.ZonedDateTime;
import java.util.UUID;

/** A delivery log row (the payload itself is not returned; its SHA-256 identifies it). */
public record WebhookNotificationVm(Long id, UUID deliveryId, String eventName, NotificationStatus status,
                                    int attempts, Integer responseCode, String lastError, String payloadSha256,
                                    ZonedDateTime createdOn, ZonedDateTime lastAttemptOn,
                                    ZonedDateTime nextAttemptOn, ZonedDateTime deliveredOn) {

    public static WebhookNotificationVm fromModel(WebhookEventNotification n) {
        return new WebhookNotificationVm(n.getId(), n.getDeliveryId(), n.getEventName(), n.getStatus(),
            n.getAttempts(), n.getResponseCode(), n.getLastError(), n.getPayloadSha256(), n.getCreatedOn(),
            n.getLastAttemptOn(), n.getNextAttemptOn(), n.getDeliveredOn());
    }
}
