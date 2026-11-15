package com.shopapp.webhook.kafka;

import com.shopapp.webhook.service.CdcEventService;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * The product and order CDC listeners (YAS ProductEventInbound / OrderEventInbound), consumer group
 * {@code webhook}. They only write the delivery log; failures are retried in place by {@link CdcErrorHandling}.
 * The order topic may not exist yet (its connector registers later): the listener waits for it
 * ({@code missing-topics-fatal=false}, no auto-creation by the consumer).
 */
@Component
@RequiredArgsConstructor
public class CdcConsumers {

    private final CdcEventService service;
    private final ObjectMapper mapper;

    @KafkaListener(id = "product-cdc-webhook", topics = "${shopapp.webhook.cdc.product-topic}",
        groupId = "${spring.kafka.consumer.group-id:webhook}")
    public void onProduct(ConsumerRecord<String, String> record) {
        service.onProduct(CdcMessage.parse(mapper, record.key(), record.value()), sourceKey(record));
    }

    @KafkaListener(id = "order-cdc-webhook", topics = "${shopapp.webhook.cdc.order-topic}",
        groupId = "${spring.kafka.consumer.group-id:webhook}")
    public void onOrder(ConsumerRecord<String, String> record) {
        service.onOrder(CdcMessage.parse(mapper, record.key(), record.value()), sourceKey(record));
    }

    /** Identifies the message: a redelivery after a crash queues no second delivery. */
    static String sourceKey(ConsumerRecord<?, ?> record) {
        return record.topic() + "-" + record.partition() + "@" + record.offset();
    }
}
