package com.shopapp.webhook.repository;

import com.shopapp.webhook.model.EventName;
import com.shopapp.webhook.model.Webhook;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WebhookRepository extends JpaRepository<Webhook, Long> {

    /** Active webhooks subscribed to the event. */
    @Query("select distinct w from Webhook w join w.webhookEvents we "
        + "where w.isActive = true and we.event.name = :name order by w.id")
    List<Webhook> findActiveSubscribers(@Param("name") EventName name);
}
