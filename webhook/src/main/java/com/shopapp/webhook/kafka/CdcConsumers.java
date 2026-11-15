package com.shopapp.webhook.kafka;

import com.shopapp.webhook.service.CdcEventService;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
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
        CdcMessage message = CdcMessage.parse(mapper, record.key(), record.value());
        service.onProduct(message, sourceKey(record, message));
    }

    @KafkaListener(id = "order-cdc-webhook", topics = "${shopapp.webhook.cdc.order-topic}",
        groupId = "${spring.kafka.consumer.group-id:webhook}")
    public void onOrder(ConsumerRecord<String, String> record) {
        CdcMessage message = CdcMessage.parse(mapper, record.key(), record.value());
        service.onOrder(message, sourceKey(record, message));
    }

    /**
     * Identifies the database change behind the message, so a redelivery (after a crash, or the same change read
     * again from another offset) queues no second delivery, while a new change is never mistaken for an old one
     * (decisions.md 5-64): Debezium's {@code source.lsn} + {@code txId} + schema.table + row id. Offsets alone were
     * reused after a topic reset and silently dropped new events. Without a source block (never for row events):
     * the record timestamp + topic-partition@offset.
     */
    static String sourceKey(ConsumerRecord<?, ?> record, CdcMessage message) {
        JsonNode source = message.source();
        if (source != null && source.hasNonNull("lsn")) {
            return "lsn:" + scalar(source.get("lsn")) + ":tx:" + scalar(source.get("txId")) + ":"
                + scalar(source.get("schema")) + "." + scalar(source.get("table")) + ":" + message.id();
        }
        return "ts:" + record.timestamp() + ":" + record.topic() + "-" + record.partition() + "@" + record.offset();
    }

    private static String scalar(JsonNode node) {
        if (node == null || node.isNull()) {
            return "-";
        }
        return node.isNumber() ? node.numberValue().toString() : node.asString();
    }
}
