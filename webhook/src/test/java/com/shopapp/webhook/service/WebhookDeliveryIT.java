package com.shopapp.webhook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.shopapp.webhook.support.AbstractWebhookIT;
import com.shopapp.webhook.support.ReceiverStub;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class WebhookDeliveryIT extends AbstractWebhookIT {

    private String sendTest(long webhookId) throws Exception {
        String body = mockMvc.perform(post("/backoffice/webhooks/" + webhookId + "/test").with(admin()))
            .andExpect(status().isAccepted())
            .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.deliveryId");
    }

    private String statusOf(long webhookId) {
        List<Map<String, Object>> rows = notificationsOf(webhookId);
        return rows.isEmpty() ? null : (String) rows.getLast().get("status");
    }

    @Test
    void testDelivery_isSignedWithTheWebhookSecret_andLogged() throws Exception {
        long id = createWebhook(receiverUrl(), 1);

        String deliveryId = sendTest(id);

        await("delivered", () -> "DELIVERED".equals(statusOf(id)));
        ReceiverStub.Request request = RECEIVER.requests().getFirst();
        assertThat(request.header("X-Hub-Signature-256")).isEqualTo(hmac(request, SECRET));
        assertThat(request.header("X-Shopapp-Delivery")).isEqualTo(deliveryId);
        assertThat(request.header("X-Shopapp-Event")).isEqualTo("PING");
        assertThat(request.header("X-Shopapp-Timestamp")).isNotBlank();
        assertThat(request.bodyText()).contains("\"deliveryId\":\"" + deliveryId + "\"", "\"event\":\"PING\"");
        assertThat(request.body().length).isPositive();

        mockMvc.perform(get("/backoffice/webhooks/" + id + "/notifications?pageNo=0&pageSize=10").with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.notifications[0].deliveryId").value(deliveryId))
            .andExpect(jsonPath("$.notifications[0].status").value("DELIVERED"))
            .andExpect(jsonPath("$.notifications[0].attempts").value(1))
            .andExpect(jsonPath("$.notifications[0].responseCode").value(200))
            .andExpect(jsonPath("$.notifications[0].payloadSha256").isNotEmpty())
            .andExpect(jsonPath("$.notifications[0].deliveredOn").isNotEmpty())
            .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/backoffice/webhooks/" + id + "/notifications?pageSize=101").with(admin()))
            .andExpect(status().isBadRequest());
    }

    @Test
    void serverErrors_areRetriedWithBackOff_untilDelivered() throws Exception {
        RECEIVER.respond(500, 503, 200);
        long id = createWebhook(receiverUrl(), 1);

        sendTest(id);

        await("delivered on the third attempt", () -> "DELIVERED".equals(statusOf(id)));
        Map<String, Object> row = notificationsOf(id).getFirst();
        assertThat(row.get("attempts")).isEqualTo(3);
        assertThat(row.get("response_code")).isEqualTo(200);
        List<ReceiverStub.Request> requests = RECEIVER.requests();
        assertThat(requests).hasSize(3);
        // every attempt sends the same delivery, byte for byte
        assertThat(requests.get(2).body()).isEqualTo(requests.get(0).body());
        assertThat(requests.get(2).header("X-Shopapp-Delivery")).isEqualTo(requests.get(0).header("X-Shopapp-Delivery"));
    }

    @Test
    void failingReceiver_isGivenUpAfterTheLastAttempt() throws Exception {
        RECEIVER.respond(500);
        long id = createWebhook(receiverUrl(), 1);

        sendTest(id);

        await("failed", () -> "FAILED".equals(statusOf(id)));
        Map<String, Object> row = notificationsOf(id).getFirst();
        assertThat(row.get("attempts")).isEqualTo(3);
        assertThat(row.get("response_code")).isEqualTo(500);
        assertThat((String) row.get("last_error")).contains("500");
        Thread.sleep(800);
        assertThat(RECEIVER.requests()).hasSize(3);
    }

    @Test
    void clientError_isNotRetried() throws Exception {
        RECEIVER.respond(410);
        long id = createWebhook(receiverUrl(), 1);

        sendTest(id);

        await("failed", () -> "FAILED".equals(statusOf(id)));
        assertThat(notificationsOf(id).getFirst().get("attempts")).isEqualTo(1);
    }

    @Test
    void hostThatRebindsToAPrivateAddressAfterRegistration_isNeverConnectedTo() throws Exception {
        DNS.map("rebind.test", "93.184.216.34");
        long id = createWebhook("http://rebind.test:" + RECEIVER.port() + "/hook", 1);
        DNS.map("rebind.test", "127.0.0.1"); // now points at the receiver on loopback

        sendTest(id);

        await("refused", () -> "FAILED".equals(statusOf(id)));
        Map<String, Object> row = notificationsOf(id).getFirst();
        assertThat(row.get("attempts")).isEqualTo(1);
        assertThat(row.get("response_code")).isNull();
        assertThat((String) row.get("last_error")).contains("non-public");
        assertThat(RECEIVER.requests()).isEmpty();
    }

    @Test
    void redirectToTheMetadataService_isNotFollowed() throws Exception {
        RECEIVER.redirectTo("http://169.254.169.254/latest/meta-data/");
        long id = createWebhook(receiverUrl(), 1);

        sendTest(id);

        await("failed", () -> "FAILED".equals(statusOf(id)));
        assertThat(notificationsOf(id).getFirst().get("response_code")).isEqualTo(302);
        assertThat(RECEIVER.requests()).hasSize(1);
    }

    @Test
    void slowReceiver_doesNotHoldBackOtherDeliveriesOrLaterEvents() throws Exception {
        ReceiverStub slow = ReceiverStub.start();
        try {
            slow.delay(5_000);
            long slowId = createWebhook("http://receiver.test:" + slow.port() + "/slow", 1);
            long fastId = createWebhook(receiverUrl(), 1);

            long start = System.nanoTime();
            send(PRODUCT_TOPIC, 501, productUpdate(501, "First"));
            send(PRODUCT_TOPIC, 502, productUpdate(502, "Second"));

            await("both events delivered to the fast receiver", Duration.ofSeconds(5),
                () -> RECEIVER.requests().size() == 2);
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            // the slow receiver holds each request for 5 s (read timeout 1.5 s): both fast deliveries happened while
            // the slow ones were still in flight, so neither the consumer nor the worker waited for it
            assertThat(notificationsOf(slowId)).hasSize(2)
                .extracting(row -> row.get("status")).allMatch(state -> "SENDING".equals(state)
                    || "PENDING".equals(state));
            assertThat(elapsedMs).isLessThan(4_000);
            await("fast deliveries logged", () -> notificationsOf(fastId).stream()
                .allMatch(row -> "DELIVERED".equals(row.get("status"))));
        } finally {
            slow.stop();
        }
    }

    @Test
    void inactiveWebhook_getsNothing_andCannotBeTested() throws Exception {
        long id = createWebhook(receiverUrl(), 1);
        mockMvc.perform(put("/backoffice/webhooks/" + id).with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"payloadUrl\":\"" + receiverUrl() + "\",\"isActive\":false,\"events\":[{\"id\":1}]}"))
            .andExpect(status().isNoContent());

        send(PRODUCT_TOPIC, 503, productUpdate(503, "Ignored"));
        mockMvc.perform(post("/backoffice/webhooks/" + id + "/test").with(admin()))
            .andExpect(status().isBadRequest());

        // a marker webhook proves the event was consumed
        long marker = createWebhook(receiverUrl(), 1);
        send(PRODUCT_TOPIC, 504, productUpdate(504, "Marker"));
        await("marker delivered", () -> "DELIVERED".equals(statusOf(marker)));
        assertThat(notificationsOf(id)).isEmpty();
    }

    @Test
    void deactivatedAfterQueueing_isCancelled() throws Exception {
        long id = createWebhook(receiverUrl(), 1);
        jdbc.update("UPDATE webhook SET is_active = false WHERE id = ?", id);
        jdbc.update("""
            INSERT INTO webhook_event_notification (webhook_id, event_name, delivery_id, source_key, payload,
                payload_sha256, status, attempts, created_on, next_attempt_on)
            VALUES (?, 'PING', gen_random_uuid(), 'manual', '{}', 'x', 'PENDING', 0, now(), now())""", id);

        await("cancelled", () -> "CANCELLED".equals(statusOf(id)));
        assertThat(RECEIVER.requests()).isEmpty();
    }

    static String productUpdate(long id, String name) {
        return "{\"before\":null,\"after\":{\"id\":" + id + ",\"name\":\"" + name + "\",\"slug\":\"p-" + id
            + "\",\"price\":\"10.0\",\"is_published\":true},\"op\":\"u\",\"source\":{}}";
    }
}
