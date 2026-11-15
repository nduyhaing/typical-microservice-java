package com.shopapp.webhook.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.ZonedDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One delivery of one event to one webhook, with its attempts: the delivery log, read-only here (rows are written
 * by {@code NotificationStore} with plain SQL).
 */
@Entity
@Table(name = "webhook_event_notification")
@Getter
@NoArgsConstructor
public class WebhookEventNotification {

    @Id
    private Long id;

    @Column(name = "webhook_id")
    private Long webhookId;

    @Column(name = "event_name")
    private String eventName;

    @Column(name = "delivery_id")
    private UUID deliveryId;

    @Column(name = "payload_sha256")
    private String payloadSha256;

    @Enumerated(EnumType.STRING)
    private NotificationStatus status;

    private int attempts;

    @Column(name = "response_code")
    private Integer responseCode;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "created_on")
    private ZonedDateTime createdOn;

    @Column(name = "next_attempt_on")
    private ZonedDateTime nextAttemptOn;

    @Column(name = "last_attempt_on")
    private ZonedDateTime lastAttemptOn;

    @Column(name = "delivered_on")
    private ZonedDateTime deliveredOn;
}
