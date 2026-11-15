package com.shopapp.recommendation.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import com.shopapp.recommendation.service.ProductVectorSyncService;
import com.shopapp.recommendation.service.ProductVectorSyncService.Outcome;
import com.shopapp.recommendation.support.AbstractRecommendationIT;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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

class ProductCdcConsumerIT extends AbstractRecommendationIT {

    @Autowired
    private ProductVectorSyncService syncService;

    @Test
    void createEvent_embedsTheProductFromProduct() throws Exception {
        phone(1, "Galaxy S24", "Samsung", 1);

        send(1, event("c", 1));

        await("product 1 embedded", () -> embedded(1));
        Map<String, Object> row = jdbc.sql("SELECT name, slug, price, brand, thumbnail_url, content, "
            + "vector_dims(embedding) AS dims FROM product_embedding WHERE product_id = 1").query().singleRow();
        assertThat(row).containsEntry("name", "Galaxy S24").containsEntry("slug", "galaxy-s24")
            .containsEntry("price", 101.0).containsEntry("brand", "Samsung")
            .containsEntry("thumbnail_url", "http://media/1.png").containsEntry("dims", 384);
        assertThat((String) row.get("content")).isEqualTo("Phones. Galaxy S24. Brand: Samsung. Categories: Phones. "
            + "Samsung smartphone with a large display. Phones");
    }

    @Test
    void snapshotReadEvent_embeds() throws Exception {
        phone(2, "Galaxy A55", "Samsung", 1);

        send(2, event("r", 2));

        await("product 2 embedded", () -> embedded(2));
    }

    @Test
    void updateEvent_reReadsAndReEmbeds() throws Exception {
        phone(3, "Galaxy S24", "Samsung", 1);
        send(3, event("c", 3));
        await("embedded", () -> embedded(3));
        String before = embedding(3);

        PRODUCT.product(3, "Galaxy Buds", "Samsung", List.of("Headphones"), "Wireless earbuds", at(2));
        send(3, event("u", 3));

        await("renamed", () -> "Galaxy Buds".equals(storedName(3)));
        assertThat(embedding(3)).isNotEqualTo(before);
    }

    @Test
    void unpublishedOrHiddenProduct_isRemoved() throws Exception {
        phone(4, "Galaxy S24", "Samsung", 1);
        send(4, event("c", 4));
        await("embedded", () -> embedded(4));

        PRODUCT.remove(4); // product's public endpoints answer 404 for unpublished / hidden products
        send(4, event("u", 4));

        await("removed", () -> !embedded(4));
    }

    @Test
    void unpublishedAfterImage_removesWithoutCallingProduct_evenWhenProductIsDown() throws Exception {
        phone(5, "Galaxy S24", "Samsung", 1);
        send(5, event("c", 5));
        await("embedded", () -> embedded(5));
        PRODUCT.down(5);

        send(5, image(5, false, true, at(2)));

        await("removed although product is down", () -> !embedded(5));
        assertThat(PRODUCT.summaryRequests(5)).isEqualTo(1);
    }

    @Test
    void deleteEventAndTombstone_remove() throws Exception {
        phone(6, "Galaxy S24", "Samsung", 1);
        phone(7, "Galaxy S23", "Samsung", 1);
        send(6, event("c", 6));
        send(7, event("c", 7));
        await("embedded", () -> embedded(6) && embedded(7));

        send(6, "{\"before\":{\"id\":6},\"after\":null,\"op\":\"d\"}");
        send(7, null);

        await("both removed", () -> !embedded(6) && !embedded(7));
    }

    @Test
    void olderReadOfTheProduct_neverOverwritesANewerRow() {
        phone(8, "Galaxy S24 Ultra", "Samsung", 20);
        assertThat(syncService.sync(8, null)).isEqualTo(Outcome.EMBEDDED);

        phone(8, "Galaxy S24", "Samsung", 10); // a slow read that started before the rename
        assertThat(syncService.sync(8, null)).isEqualTo(Outcome.STALE);
        assertThat(storedName(8)).isEqualTo("Galaxy S24 Ultra");

        phone(8, "Galaxy S24 Ultra", "Samsung", 20); // the same version again (reindex): accepted
        assertThat(syncService.sync(8, null)).isEqualTo(Outcome.EMBEDDED);
    }

    @Test
    void lateUnpublishEvent_neverRemovesALaterRepublish() throws Exception {
        phone(9, "Galaxy S24", "Samsung", 20);
        send(9, image(9, true, true, at(20)));
        await("embedded", () -> embedded(9));

        send(9, image(9, false, true, at(10))); // the unpublish at second 10, delivered late
        phone(10, "Marker", "Other", 1);
        send(10, event("c", 10));

        await("marker after the late event", () -> embedded(10));
        assertThat(embedded(9)).isTrue();
    }

    @Test
    void productDown_isRetriedUntilItAnswers_andNeverDeadLettered() throws Exception {
        phone(11, "Galaxy S24", "Samsung", 1);
        PRODUCT.down(11);

        send(11, event("c", 11));
        await("product keeps being retried", () -> PRODUCT.summaryRequests(11) >= 6);
        assertThat(embedded(11)).isFalse();
        PRODUCT.up(11);

        await("embedded once product answers", () -> embedded(11));
        assertThat(deadLetters("{\"id\":11}", 1, Duration.ofSeconds(2))).isEmpty();
    }

    @Test
    void poisonMessage_goesToTheRecommendationDltAtOnce_andTheNextMessageIsProcessed() throws Exception {
        String key = "{\"id\":12}";
        kafka.send(TOPIC, key, "this is not json").get();
        phone(13, "Galaxy S24", "Samsung", 1);
        send(13, event("c", 13));

        await("next message processed", () -> embedded(13));
        List<ConsumerRecord<String, String>> dead = deadLetters(key, 1, Duration.ofSeconds(15));
        assertThat(dead).hasSize(1);
        assertThat(dead.getFirst().value()).isEqualTo("this is not json");
        assertThat(PRODUCT.summaryRequests(12)).isZero();
    }

    private String embedding(long id) {
        return jdbc.sql("SELECT embedding::text FROM product_embedding WHERE product_id = :id").param("id", id)
            .query(String.class).single();
    }

    private static long micros(Instant instant) {
        return ChronoUnit.MICROS.between(Instant.EPOCH, instant);
    }

    /** An update whose after image carries the flags and the modification time as Debezium sends them. */
    private static String image(long id, boolean published, boolean visible, Instant modified) {
        return "{\"before\":null,\"after\":{\"id\":" + id + ",\"is_published\":" + published
            + ",\"is_visible_individually\":" + visible + ",\"last_modified_on\":" + micros(modified)
            + "},\"op\":\"u\",\"source\":{}}";
    }

    /** Reads the DLT from the start until {@code expected} records with that key are seen ({@code wait} at most). */
    private static List<ConsumerRecord<String, String>> deadLetters(String key, int expected, Duration wait) {
        Map<String, Object> config = Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers(),
            ConsumerConfig.GROUP_ID_CONFIG, "dlt-reader-" + UUID.randomUUID(),
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        List<ConsumerRecord<String, String>> found = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(config)) {
            long deadline = System.nanoTime() + wait.toNanos();
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
