package com.shopapp.search.kafka;

import com.shopapp.search.model.Product;
import com.shopapp.search.service.ProductSyncService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.ConsumerSeekAware;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Keeps the index in line with the product CDC topic (YAS ProductSyncDataConsumer). An after image that is
 * unpublished or hidden removes the document without asking product; other row events re-read product and version
 * the document by the row's modification time (decisions.md 4-12, 4-13, 4-70, 4-71). Failures are retried in place by
 * {@link CdcErrorHandling} (infrastructure: without an end; poison: dead-letter topic at once).
 *
 * <p>The topic is compacted (4-72): at start-up an empty index (new volume, deleted index) is refilled by reading
 * the topic from the start, which holds the latest row image of every product.
 */
@Slf4j
@Component
public class ProductCdcConsumer implements ConsumerSeekAware {

    private final ProductSyncService syncService;
    private final ObjectMapper mapper;
    private final ElasticsearchOperations operations;
    private final AtomicBoolean refillChecked = new AtomicBoolean();

    public ProductCdcConsumer(ProductSyncService syncService, ObjectMapper mapper, ElasticsearchOperations operations) {
        this.syncService = syncService;
        this.mapper = mapper;
        this.operations = operations;
    }

    @KafkaListener(id = "product-cdc-search", topics = "${search.cdc.topic:dbproduct.public.product}",
        groupId = "${spring.kafka.consumer.group-id:search}")
    public void onMessage(ConsumerRecord<String, String> message) {
        ProductCdcEvent event = ProductCdcEvent.parse(mapper, message.key(), message.value());
        long id = event.productId();
        switch (event.action()) {
            case SYNC -> log.debug("Product {} synced: {}", id, syncService.sync(id, event.version()));
            case UNLIST -> log.debug("Product {} unpublished or hidden: {}", id,
                syncService.delete(id, event.version() != null ? event.version() : nowMicros()));
            case DELETE -> log.debug("Product {} deleted: {}", id, syncService.delete(id));
            case SKIP -> log.debug("Product {} update touches no indexed column; skipped", id);
            case IGNORE -> log.info("Product {} CDC operation not handled; ignored", id);
            default -> throw new IllegalStateException("Unknown action " + event.action());
        }
    }

    /** The first assignment after start-up reads the topic from the start when the index is empty. */
    @Override
    public void onPartitionsAssigned(Map<TopicPartition, Long> assignments, ConsumerSeekCallback callback) {
        if (assignments.isEmpty() || !refillChecked.compareAndSet(false, true)) {
            return;
        }
        try {
            if (operations.count(Query.findAll(), Product.class) == 0) {
                log.info("Product index is empty: reading {} from the start to refill it", assignments.keySet());
                callback.seekToBeginning(assignments.keySet());
            }
        } catch (RuntimeException ex) {
            log.warn("Could not count the product index ({}); keeping the committed offsets. "
                + "POST /search/backoffice/reindex refills it", ex.getMessage());
        }
    }

    private static long nowMicros() {
        return ChronoUnit.MICROS.between(Instant.EPOCH, Instant.now());
    }
}
