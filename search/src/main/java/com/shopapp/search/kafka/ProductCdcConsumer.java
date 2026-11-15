package com.shopapp.search.kafka;

import com.shopapp.search.service.ProductSyncService;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.BackOff;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Keeps the index in line with the product CDC topic (YAS ProductSyncDataConsumer). Failures (product or
 * Elasticsearch unavailable) are retried on retry topics with a growing delay, then parked on
 * {@code dbproduct.public.product-dlt}; a {@link PoisonMessageException} goes to the dead-letter topic at once. Retries
 * run beside newer events of the same product, which is safe: each attempt re-reads product and versions the
 * document by product's modification time (decisions.md 4-12, 4-13).
 */
@Slf4j
@Component
public class ProductCdcConsumer {

    private final ProductSyncService syncService;
    private final ObjectMapper mapper;

    public ProductCdcConsumer(ProductSyncService syncService, ObjectMapper mapper) {
        this.syncService = syncService;
        this.mapper = mapper;
    }

    @RetryableTopic(
        attempts = "${search.cdc.retry.attempts:4}",
        backOff = @BackOff(delayString = "${search.cdc.retry.delay-ms:2000}",
            multiplierString = "${search.cdc.retry.multiplier:5}"),
        exclude = PoisonMessageException.class,
        autoCreateTopics = "true",
        numPartitions = "1")
    @KafkaListener(id = "product-cdc-search", topics = "${search.cdc.topic:dbproduct.public.product}",
        groupId = "${spring.kafka.consumer.group-id:search}")
    public void onMessage(ConsumerRecord<String, String> message) {
        ProductCdcEvent event = ProductCdcEvent.parse(mapper, message.key(), message.value());
        switch (event.action()) {
            case SYNC -> log.debug("Product {} synced: {}", event.productId(), syncService.sync(event.productId()));
            case DELETE -> log.debug("Product {} deleted: {}", event.productId(),
                syncService.delete(event.productId()));
            case SKIP -> log.debug("Product {} update touches no indexed column; skipped", event.productId());
            case IGNORE -> log.info("Product {} CDC operation not handled; ignored", event.productId());
            default -> throw new IllegalStateException("Unknown action " + event.action());
        }
    }

    /** The message stays on the dead-letter topic; Kafka UI shows it, {@code POST /backoffice/reindex} repairs. */
    @DltHandler
    public void onDeadLetter(ConsumerRecord<String, String> message,
                             @Header(name = KafkaHeaders.DLT_EXCEPTION_MESSAGE, required = false) String error) {
        log.error("Product CDC message dead-lettered (topic {}, offset {}, key {}): {}", message.topic(),
            message.offset(), message.key(), error);
    }
}
