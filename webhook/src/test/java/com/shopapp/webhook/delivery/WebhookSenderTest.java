package com.shopapp.webhook.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import com.shopapp.webhook.config.WebhookProperties;
import com.shopapp.webhook.support.FakeHostResolver;
import com.shopapp.webhook.support.ReceiverStub;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The sender against a JDK HttpServer on 127.0.0.1, reached as the allow-listed name {@code receiver.test}. */
class WebhookSenderTest {

    private static final String SECRET = "s3cr3t-s3cr3t-s3cr3t";

    private final FakeHostResolver resolver = new FakeHostResolver()
        .map("receiver.test", "127.0.0.1")
        .map("public.test", "93.184.216.34");
    private ReceiverStub receiver;
    private WebhookSender sender;

    @BeforeEach
    void setUp() throws Exception {
        receiver = ReceiverStub.start();
        WebhookProperties properties = new WebhookProperties(List.of("receiver.test"),
            new WebhookProperties.Delivery(500, 1000, 2000, 3, 100, 2, 4, 100, 60000, 1024));
        sender = new WebhookSender(new SsrfGuard(resolver, properties), properties);
    }

    @AfterEach
    void tearDown() throws Exception {
        sender.close();
        receiver.stop();
    }

    private String url(String host) {
        return "http://" + host + ":" + receiver.port() + "/hook";
    }

    @Test
    void delivers_withAnHmacSha256SignatureOfTheRawBody_andDeliveryHeaders() throws Exception {
        UUID deliveryId = UUID.randomUUID();
        byte[] body = "{\"event\":\"ON_PRODUCT_UPDATED\",\"data\":{\"name\":\"Café\"}}"
            .getBytes(StandardCharsets.UTF_8);

        SendResult result = sender.send(url("receiver.test"), SECRET, "ON_PRODUCT_UPDATED", deliveryId, body);

        assertThat(result.outcome()).isEqualTo(SendResult.Outcome.DELIVERED);
        assertThat(result.statusCode()).isEqualTo(200);
        ReceiverStub.Request request = receiver.requests().getFirst();
        assertThat(request.body()).isEqualTo(body);
        // signed: timestamp header + "." + the raw body bytes, so a captured delivery cannot be replayed later with
        // a fresh timestamp (5-69)
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        mac.update((request.header("X-Shopapp-Timestamp") + ".").getBytes(StandardCharsets.UTF_8));
        String expected = "sha256=" + HexFormat.of().formatHex(mac.doFinal(body));
        assertThat(request.header("X-Hub-Signature-256")).isEqualTo(expected);
        assertThat(request.header("X-Shopapp-Delivery")).isEqualTo(deliveryId.toString());
        assertThat(request.header("X-Shopapp-Event")).isEqualTo("ON_PRODUCT_UPDATED");
        assertThat(Long.parseLong(request.header("X-Shopapp-Timestamp")))
            .isCloseTo(System.currentTimeMillis() / 1000, org.assertj.core.data.Offset.offset(5L));
        assertThat(request.header("Content-Type")).startsWith("application/json");
        assertThat(request.header("Host")).isEqualTo("receiver.test:" + receiver.port());
    }

    @Test
    void serverErrors_areRetryable_clientErrorsAndRedirectsAreNot() throws Exception {
        receiver.respond(500);
        assertThat(send("receiver.test").outcome()).isEqualTo(SendResult.Outcome.RETRYABLE_FAILURE);
        receiver.respond(429);
        assertThat(send("receiver.test").outcome()).isEqualTo(SendResult.Outcome.RETRYABLE_FAILURE);
        receiver.respond(404);
        SendResult notFound = send("receiver.test");
        assertThat(notFound.outcome()).isEqualTo(SendResult.Outcome.PERMANENT_FAILURE);
        assertThat(notFound.statusCode()).isEqualTo(404);
    }

    @Test
    void redirects_areNotFollowed() throws Exception {
        receiver.redirectTo("http://169.254.169.254/latest/meta-data/");

        SendResult result = send("receiver.test");

        assertThat(result.statusCode()).isEqualTo(302);
        assertThat(result.outcome()).isEqualTo(SendResult.Outcome.PERMANENT_FAILURE);
        assertThat(receiver.requests()).hasSize(1);
    }

    @Test
    void loopbackLiteralOrNameNotOnTheAllowList_isNeverConnectedTo() throws Exception {
        resolver.map("rebound.test", "127.0.0.1");

        for (String host : new String[] {"127.0.0.1", "localhost", "rebound.test", "[::ffff:127.0.0.1]"}) {
            SendResult result = send(host);
            assertThat(result.outcome()).as(host).isEqualTo(SendResult.Outcome.PERMANENT_FAILURE);
            assertThat(result.error()).as(host).contains("non-public");
        }
        assertThat(receiver.requests()).isEmpty();
    }

    @Test
    void slowReceiver_timesOut_asARetryableFailure() throws Exception {
        receiver.delay(3000);
        long start = System.nanoTime();

        SendResult result = send("receiver.test");

        assertThat(result.outcome()).isEqualTo(SendResult.Outcome.RETRYABLE_FAILURE);
        assertThat((System.nanoTime() - start) / 1_000_000).isLessThan(2900);
    }

    @Test
    void connectionRefused_isRetryable() throws Exception {
        receiver.stop();
        assertThat(send("receiver.test").outcome()).isEqualTo(SendResult.Outcome.RETRYABLE_FAILURE);
    }

    @Test
    void oversizedPayload_isNotSent() throws Exception {
        SendResult result = sender.send(url("receiver.test"), SECRET, "ON_PRODUCT_UPDATED", UUID.randomUUID(),
            new byte[2048]);
        assertThat(result.outcome()).isEqualTo(SendResult.Outcome.PERMANENT_FAILURE);
        assertThat(receiver.requests()).isEmpty();
    }

    @Test
    void urlWithCredentials_isNotSent() throws Exception {
        SendResult result = sender.send("http://u:p@receiver.test:" + receiver.port() + "/", SECRET, "PING",
            UUID.randomUUID(), "{}".getBytes(StandardCharsets.UTF_8));
        assertThat(result.outcome()).isEqualTo(SendResult.Outcome.PERMANENT_FAILURE);
        assertThat(receiver.requests()).isEmpty();
    }

    private SendResult send(String host) {
        return sender.send(url(host), SECRET, "ON_ORDER_CREATED", UUID.randomUUID(),
            "{}".getBytes(StandardCharsets.UTF_8));
    }
}
