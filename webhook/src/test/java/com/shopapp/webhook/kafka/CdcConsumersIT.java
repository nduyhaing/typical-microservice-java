package com.shopapp.webhook.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.shopapp.webhook.service.CdcEventService;
import com.shopapp.webhook.support.AbstractWebhookIT;
import com.shopapp.webhook.support.ReceiverStub;
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
import tools.jackson.databind.ObjectMapper;

class CdcConsumersIT extends AbstractWebhookIT {

    @Autowired
    private CdcEventService cdcEventService;

    @Autowired
    private ObjectMapper mapper;

    private List<ReceiverStub.Request> receivedBy(String path) {
        return RECEIVER.requests().stream().filter(request -> request.path().equals(path)).toList();
    }

    private static String order(String op, long id, String beforeStatus, String afterStatus) {
        String before = beforeStatus == null ? "null" : "{\"id\":" + id + ",\"status\":\"" + beforeStatus + "\"}";
        return "{\"before\":" + before + ",\"after\":{\"id\":" + id + ",\"status\":\"" + afterStatus + "\","
            + "\"checkout_id\":\"chk-" + id + "\",\"email\":\"jane@example.com\",\"customer_id\":\"u-1\","
            + "\"total_amount\":\"42.00\",\"number_item\":1},\"op\":\"" + op + "\",\"source\":{}}";
    }

    @Test
    void productUpdate_isDeliveredAsOnProductUpdated_toSubscribersOnly() throws Exception {
        String subscribed = "http://receiver.test:" + RECEIVER.port() + "/products";
        String orders = "http://receiver.test:" + RECEIVER.port() + "/orders";
        createWebhook(subscribed, 1);
        createWebhook(orders, 2, 3);

        send(PRODUCT_TOPIC, 11, "{\"before\":null,\"after\":{\"id\":11},\"op\":\"c\",\"source\":{}}");
        send(PRODUCT_TOPIC, 11, "{\"before\":null,\"after\":{\"id\":11,\"name\":\"Galaxy S24\",\"slug\":\"galaxy\","
            + "\"price\":\"799.0\",\"is_published\":true,\"description\":\"x\"},\"op\":\"u\",\"source\":{}}");

        await("product update delivered", () -> receivedBy("/products").size() == 1);
        ReceiverStub.Request request = receivedBy("/products").getFirst();
        assertThat(request.header("X-Hub-Signature-256")).isEqualTo(hmac(request.body(), SECRET));
        assertThat(request.header("X-Shopapp-Event")).isEqualTo("ON_PRODUCT_UPDATED");
        String body = request.bodyText();
        assertThat((String) JsonPath.read(body, "$.event")).isEqualTo("ON_PRODUCT_UPDATED");
        assertThat(((Number) JsonPath.read(body, "$.data.productId")).longValue()).isEqualTo(11);
        assertThat((String) JsonPath.read(body, "$.data.name")).isEqualTo("Galaxy S24");
        assertThat(body).doesNotContain("description");
        Thread.sleep(500);
        assertThat(receivedBy("/products")).hasSize(1); // the create sent nothing
        assertThat(receivedBy("/orders")).isEmpty();
    }

    @Test
    void orderCreateAndStatusChanges_areDelivered() throws Exception {
        long webhookId = createWebhook(receiverUrl(), 2, 3);

        send(ORDER_TOPIC, 21, order("c", 21, null, "PENDING"));
        send(ORDER_TOPIC, 21, order("u", 21, "PENDING", "PENDING_PAYMENT"));
        send(ORDER_TOPIC, 21, order("u", 21, null, "PENDING_PAYMENT")); // no before image, status unchanged
        send(ORDER_TOPIC, 21, order("u", 21, null, "PAID"));             // no before image, status changed

        await("three deliveries", () -> RECEIVER.requests().size() == 3);
        Thread.sleep(500);
        // deliveries run in parallel: put the bodies in queueing (log id) order
        List<String> deliveryIds = notificationsOf(webhookId).stream()
            .map(row -> row.get("delivery_id").toString()).toList();
        List<String> bodies = RECEIVER.requests().stream()
            .sorted(java.util.Comparator.comparing(request -> deliveryIds.indexOf(
                request.header("X-Shopapp-Delivery"))))
            .map(ReceiverStub.Request::bodyText).toList();
        assertThat(bodies).hasSize(3);
        assertThat((String) JsonPath.read(bodies.get(0), "$.event")).isEqualTo("ON_ORDER_CREATED");
        assertThat((String) JsonPath.read(bodies.get(0), "$.data.status")).isEqualTo("PENDING");
        assertThat((String) JsonPath.read(bodies.get(0), "$.data.checkoutId")).isEqualTo("chk-21");
        assertThat((String) JsonPath.read(bodies.get(1), "$.event")).isEqualTo("ON_ORDER_STATUS_UPDATED");
        assertThat((String) JsonPath.read(bodies.get(1), "$.data.previousStatus")).isEqualTo("PENDING");
        assertThat((String) JsonPath.read(bodies.get(1), "$.data.status")).isEqualTo("PENDING_PAYMENT");
        assertThat((String) JsonPath.read(bodies.get(2), "$.data.previousStatus")).isEqualTo("PENDING_PAYMENT");
        assertThat((String) JsonPath.read(bodies.get(2), "$.data.status")).isEqualTo("PAID");
        assertThat(String.join("", bodies)).doesNotContain("jane@example.com", "customer");
        RECEIVER.requests().forEach(request -> {
            try {
                assertThat(request.header("X-Hub-Signature-256")).isEqualTo(hmac(request.body(), SECRET));
            } catch (Exception ex) {
                throw new AssertionError(ex);
            }
        });
    }

    @Test
    void redeliveredMessage_queuesNoSecondDelivery() throws Exception {
        long id = createWebhook(receiverUrl(), 2);
        CdcMessage message = CdcMessage.parse(mapper, "{\"id\":31}", order("c", 31, null, "PENDING"));

        cdcEventService.onOrder(message, "dborder.public.order-0@77");
        cdcEventService.onOrder(message, "dborder.public.order-0@77");

        assertThat(notificationsOf(id)).hasSize(1);
    }

    @Test
    void poisonMessage_goesToTheWebhookDltAtOnce_andTheNextMessageIsProcessed() throws Exception {
        createWebhook(receiverUrl(), 1);
        kafka.send(PRODUCT_TOPIC, "{\"id\":41}", "this is not json").get();
        send(PRODUCT_TOPIC, 42, "{\"before\":null,\"after\":{\"id\":42,\"name\":\"Next\"},\"op\":\"u\"}");

        await("next message delivered", () -> RECEIVER.requests().size() == 1);
        List<ConsumerRecord<String, String>> dead = deadLetters(PRODUCT_TOPIC + "-webhook-dlt", "{\"id\":41}");
        assertThat(dead).hasSize(1);
        assertThat(dead.getFirst().value()).isEqualTo("this is not json");
    }

    /** Reads the DLT from the start until a record with that key is seen (15 s at most). */
    private static List<ConsumerRecord<String, String>> deadLetters(String topic, String key) {
        Map<String, Object> config = Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers(),
            ConsumerConfig.GROUP_ID_CONFIG, "dlt-reader-" + UUID.randomUUID(),
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        List<ConsumerRecord<String, String>> found = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(config)) {
            TopicPartition partition = new TopicPartition(topic, 0);
            consumer.assign(List.of(partition));
            consumer.seekToBeginning(List.of(partition));
            long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
            while (found.isEmpty() && System.nanoTime() < deadline) {
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
