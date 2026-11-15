package com.shopapp.webhook.repository;

import com.shopapp.webhook.model.WebhookEventNotification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookEventNotificationRepository extends JpaRepository<WebhookEventNotification, Long> {

    Page<WebhookEventNotification> findByWebhookId(Long webhookId, Pageable pageable);
}
