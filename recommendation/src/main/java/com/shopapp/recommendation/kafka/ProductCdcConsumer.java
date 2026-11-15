package com.shopapp.recommendation.kafka;

import com.shopapp.recommendation.repository.ProductEmbeddingRepository;
import com.shopapp.recommendation.service.ProductVectorSyncService;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.ConsumerSeekAware;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Keeps the embeddings in line with the product CDC topic (YAS ProductSyncDataConsumer). Failures are retried in
 * place or dead-lettered by {@link CdcErrorHandling}. When the table is empty at the first partition assignment
 * (new database volume, committed offsets left from before) the topic is re-read from the start, as search does
 * for an empty index.
 */
@Slf4j
@Component
public class ProductCdcConsumer implements ConsumerSeekAware {

    private final ProductVectorSyncService syncService;
    private final ObjectMapper mapper;
    private final ProductEmbeddingRepository repository;
    private final AtomicBoolean firstAssignment = new AtomicBoolean(true);

    public ProductCdcConsumer(ProductVectorSyncService syncService, ObjectMapper mapper,
                              ProductEmbeddingRepository repository) {
        this.syncService = syncService;
        this.mapper = mapper;
        this.repository = repository;
    }

    @KafkaListener(id = "product-cdc-recommendation", topics = "${recommendation.cdc.topic:dbproduct.public.product}",
        groupId = "${spring.kafka.consumer.group-id:recommendation}")
    public void onMessage(ConsumerRecord<String, String> message) {
        ProductCdcEvent event = ProductCdcEvent.parse(mapper, message.key(), message.value());
        long id = event.productId();
        switch (event.action()) {
            case SYNC -> log.debug("Product {} synced: {}", id, syncService.sync(id, event.version()));
            case UNLIST -> log.debug("Product {} unlisted: {}", id, syncService.unlist(id, event.version()));
            case DELETE -> log.debug("Product {} deleted: {}", id, syncService.delete(id));
            case SKIP -> log.debug("Product {} update touches no stored column; skipped", id);
            case IGNORE -> log.info("Product {} CDC operation not handled; ignored", id);
            default -> throw new IllegalStateException("Unknown action " + event.action());
        }
    }

    @Override
    public void onPartitionsAssigned(Map<TopicPartition, Long> assignments, ConsumerSeekCallback callback) {
        if (!firstAssignment.getAndSet(false) || assignments.isEmpty()) {
            return;
        }
        try {
            if (repository.countRows() == 0) {
                log.info("No embeddings stored: reading the product CDC topic from the start");
                callback.seekToBeginning(assignments.keySet());
            }
        } catch (RuntimeException ex) {
            log.warn("Could not count the embeddings; keeping the committed offsets: {}", ex.getMessage());
        }
    }
}
