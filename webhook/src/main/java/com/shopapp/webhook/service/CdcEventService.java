package com.shopapp.webhook.service;

import com.shopapp.webhook.kafka.CdcMapping;
import com.shopapp.webhook.kafka.CdcMessage;
import com.shopapp.webhook.model.EventName;
import com.shopapp.webhook.repository.OrderStatusStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** CDC row events to queued deliveries, one transaction per message (decisions.md 5-35). */
@Slf4j
@Service
@RequiredArgsConstructor
public class CdcEventService {

    private final NotificationService notifications;
    private final OrderStatusStore orderStatuses;
    private final ObjectMapper mapper;

    @Transactional
    public void onProduct(CdcMessage message, String sourceKey) {
        CdcMapping.productUpdated(mapper, message).ifPresent(data -> log.debug("Product {} updated: {} deliveries",
            message.id(), notifications.enqueue(EventName.ON_PRODUCT_UPDATED, data, sourceKey)));
    }

    @Transactional
    public void onOrder(CdcMessage message, String sourceKey) {
        if ("d".equals(message.op())) {
            orderStatuses.delete(message.id());
            return;
        }
        CdcMapping.OrderChange change = CdcMapping.orderChange(message, orderStatuses.find(message.id()));
        if (change.status() != null) {
            orderStatuses.save(message.id(), change.status());
        }
        if (change.event() != null) {
            int queued = notifications.enqueue(change.event(), CdcMapping.orderData(mapper, message, change),
                sourceKey);
            log.debug("Order {} {}: {} deliveries", message.id(), change.event(), queued);
        }
    }
}
