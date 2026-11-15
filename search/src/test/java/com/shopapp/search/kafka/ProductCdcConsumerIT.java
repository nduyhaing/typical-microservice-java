package com.shopapp.search.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import com.shopapp.search.model.Product;
import com.shopapp.search.service.ProductSyncService;
import com.shopapp.search.support.AbstractSearchIT;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ProductCdcConsumerIT extends AbstractSearchIT {

    @Autowired
    private ProductSyncService syncService;

    private void galaxy(long id, String name, int modifiedSecond) {
        PRODUCT.product(id, name, 799, "Samsung", List.of("Phones"), List.of("Color"), at(modifiedSecond));
    }

    @Test
    void createEvent_indexesTheProductFromProduct() throws Exception {
        galaxy(1, "Galaxy S24", 1);

        send(1, event("c", 1));

        await("product 1 indexed", () -> indexed(1));
        Product doc = document(1);
        assertThat(doc.getName()).isEqualTo("Galaxy S24");
        assertThat(doc.getPrice()).isEqualTo(799.0);
        assertThat(doc.getBrand()).isEqualTo("Samsung");
        assertThat(doc.getCategories()).containsExactly("Phones");
        assertThat(doc.getAttributes()).containsExactly("Color");
        assertThat(doc.getIsPublished()).isTrue();
        assertThat(doc.getIsVisibleIndividually()).isTrue();
        assertThat(doc.getCreatedOn()).isNotNull();
    }

    @Test
    void snapshotReadEvent_indexes() throws Exception {
        galaxy(2, "Galaxy A55", 1);

        send(2, event("r", 2));

        await("product 2 indexed", () -> indexed(2));
    }

    @Test
    void updateEvent_reReadsTheProduct() throws Exception {
        galaxy(3, "Galaxy S24", 1);
        send(3, event("c", 3));
        await("indexed", () -> indexed(3));

        galaxy(3, "Galaxy S24 Ultra", 2);
        send(3, event("u", 3));

        await("renamed", () -> "Galaxy S24 Ultra".equals(document(3).getName()));
    }

    @Test
    void unpublishedOrHiddenProduct_isRemoved() throws Exception {
        galaxy(4, "Galaxy S24", 1);
        send(4, event("c", 4));
        await("indexed", () -> indexed(4));

        PRODUCT.remove(4); // product's public endpoint answers 404 for unpublished / hidden products
        send(4, event("u", 4));

        await("removed", () -> !indexed(4));
    }

    @Test
    void deleteEventAndTombstone_removeTheDocument() throws Exception {
        galaxy(5, "Galaxy S24", 1);
        galaxy(6, "Galaxy S23", 1);
        send(5, event("c", 5));
        send(6, event("c", 6));
        await("indexed", () -> indexed(5) && indexed(6));

        send(5, "{\"before\":{\"id\":5},\"after\":null,\"op\":\"d\"}");
        send(6, null);

        await("both removed", () -> !indexed(5) && !indexed(6));
    }

    @Test
    void duplicateEvents_areIdempotent() throws Exception {
        galaxy(7, "Galaxy S24", 1);

        for (int i = 0; i < 3; i++) {
            send(7, event("u", 7));
        }
        galaxy(8, "Marker", 1);
        send(8, event("c", 8));

        await("marker after the duplicates", () -> indexed(8));
        assertThat(PRODUCT.detailRequests(7)).isEqualTo(3);
        assertThat(document(7).getName()).isEqualTo("Galaxy S24");
        refresh();
        assertThat(operations.count(org.springframework.data.elasticsearch.core.query.Query.findAll(),
            Product.class)).isEqualTo(2);
    }

    @Test
    void olderReadOfTheProduct_neverOverwritesANewerDocument() {
        galaxy(9, "Galaxy S24 Ultra", 20);
        assertThat(syncService.sync(9)).isEqualTo(ProductSyncService.Outcome.INDEXED);

        galaxy(9, "Galaxy S24", 10); // a slow read that started before the rename
        assertThat(syncService.sync(9)).isEqualTo(ProductSyncService.Outcome.STALE);
        assertThat(document(9).getName()).isEqualTo("Galaxy S24 Ultra");

        galaxy(9, "Galaxy S24 Ultra", 20); // the same version again (reindex): accepted
        assertThat(syncService.sync(9)).isEqualTo(ProductSyncService.Outcome.INDEXED);
    }

    @Test
    void stockOnlyUpdate_isSkippedWithoutCallingProduct() throws Exception {
        galaxy(10, "Galaxy S24", 1);
        send(10, "{\"before\":{\"id\":10,\"name\":\"Galaxy S24\",\"stock_quantity\":5},"
            + "\"after\":{\"id\":10,\"name\":\"Galaxy S24\",\"stock_quantity\":4},\"op\":\"u\"}");
        galaxy(11, "Marker", 1);
        send(11, event("c", 11));

        await("marker", () -> indexed(11));
        assertThat(PRODUCT.detailRequests(10)).isZero();
        assertThat(indexed(10)).isFalse();
    }

    @Test
    void poisonMessage_goesToTheDltAtOnce_andTheNextMessageIsProcessed() throws Exception {
        String key = "{\"id\":12}";
        kafka.send(TOPIC, key, "this is not json").get();
        galaxy(13, "Galaxy S24", 1);
        send(13, event("c", 13));

        await("next message processed", () -> indexed(13));
        List<ConsumerRecord<String, String>> dead = deadLetters(key, 1);
        assertThat(dead).hasSize(1);
        assertThat(dead.getFirst().value()).isEqualTo("this is not json");
        assertThat(PRODUCT.detailRequests(12)).isZero();
    }

    @Test
    void productDownForAWhile_isRetriedUntilItAnswers() throws Exception {
        galaxy(14, "Galaxy S24", 1);
        PRODUCT.down(14);

        send(14, event("c", 14));
        await("first attempt failed", () -> PRODUCT.detailRequests(14) >= 1);
        PRODUCT.up(14);

        await("indexed after a retry", () -> indexed(14));
        assertThat(PRODUCT.detailRequests(14)).isGreaterThanOrEqualTo(2);
    }

    @Test
    void productDownForGood_endsOnTheDltAfterAllAttempts() throws Exception {
        galaxy(15, "Galaxy S24", 1);
        PRODUCT.down(15);

        send(15, event("c", 15));

        List<ConsumerRecord<String, String>> dead = deadLetters("{\"id\":15}", 1);
        assertThat(dead).hasSize(1);
        assertThat(PRODUCT.detailRequests(15)).isEqualTo(3);
        assertThat(indexed(15)).isFalse();
    }

    /** Reads the DLT from the start until {@code expected} records with that key are seen (15 s at most). */
    private static List<ConsumerRecord<String, String>> deadLetters(String key, int expected) {
        Map<String, Object> config = Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers(),
            ConsumerConfig.GROUP_ID_CONFIG, "dlt-reader-" + UUID.randomUUID(),
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        List<ConsumerRecord<String, String>> found = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(config)) {
            long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
            boolean assigned = false;
            while (found.size() < expected && System.nanoTime() < deadline) {
                if (!assigned) {
                    if (consumer.partitionsFor(DLT, Duration.ofSeconds(2)).isEmpty()) {
                        continue;
                    }
                    TopicPartition partition = new TopicPartition(DLT, 0);
                    consumer.assign(List.of(partition));
                    consumer.seekToBeginning(List.of(partition));
                    assigned = true;
                }
                consumer.poll(Duration.ofMillis(500)).forEach(record -> {
                    if (key.equals(record.key())) {
                        found.add(record);
                    }
                });
            }
        }
        return found;
    }
}
