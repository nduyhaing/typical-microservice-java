package com.shopapp.webhook.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.shopapp.webhook.delivery.HostResolver;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * The whole service against one Postgres and one Kafka container per JVM, with DNS replaced by a
 * {@link FakeHostResolver} ({@code receiver.test} -> 127.0.0.1, allow-listed; {@code public.test} -> a public
 * address) and a {@link ReceiverStub} as the webhook receiver. Every IT class shares this one Spring context (no
 * per-class properties), so a single set of CDC listeners consumes the topics. Deliveries are fast here: polled every
 * 100 ms, read timeout 1.5 s, retries after 200 ms then 400 ms.
 */
@SpringBootTest(properties = {
    "shopapp.webhook.allowed-hosts=receiver.test",
    "shopapp.webhook.delivery.poll-interval-ms=100",
    "shopapp.webhook.delivery.connect-timeout-ms=500",
    "shopapp.webhook.delivery.read-timeout-ms=1500",
    "shopapp.webhook.delivery.total-timeout-ms=3000",
    "shopapp.webhook.delivery.initial-backoff-ms=200",
    "shopapp.webhook.delivery.backoff-multiplier=2",
    "shopapp.webhook.delivery.max-attempts=3",
    "shopapp.webhook.delivery.lease-ms=10000",
    "shopapp.webhook.cdc.retry.initial-interval-ms=100",
    "shopapp.webhook.cdc.retry.max-interval-ms=400",
    "shopapp.webhook.cdc.retry.other-interval-ms=100",
    "spring.kafka.consumer.auto-offset-reset=earliest"})
@AutoConfigureMockMvc
@Import(AbstractWebhookIT.TestDns.class)
public abstract class AbstractWebhookIT {

    public static final String PRODUCT_TOPIC = "dbproduct.public.product";
    public static final String ORDER_TOPIC = "dborder.public.order";
    public static final String SECRET = "it-secret-0123456789abcdef";
    protected static final FakeHostResolver DNS = new FakeHostResolver();
    protected static final ReceiverStub RECEIVER;

    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16.16");
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.4.0");

    static {
        POSTGRES.start();
        KAFKA.start();
        try (Admin admin = Admin.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG,
            KAFKA.getBootstrapServers()))) {
            admin.createTopics(List.of(new NewTopic(PRODUCT_TOPIC, 1, (short) 1),
                new NewTopic(ORDER_TOPIC, 1, (short) 1))).all().get();
            RECEIVER = ReceiverStub.start();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** Test DNS instead of the system resolver. */
    @TestConfiguration(proxyBeanMethods = false)
    static class TestDns {

        @Bean
        @Primary
        HostResolver testHostResolver() {
            return DNS;
        }
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected KafkaTemplate<String, String> kafka;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    protected static String bootstrapServers() {
        return KAFKA.getBootstrapServers();
    }

    @BeforeEach
    void cleanState() {
        jdbc.execute("TRUNCATE webhook, webhook_event, webhook_event_notification, order_status_seen RESTART IDENTITY");
        RECEIVER.reset();
        DNS.map("receiver.test", "127.0.0.1").map("public.test", "93.184.216.34");
    }

    protected static String receiverUrl() {
        return "http://receiver.test:" + RECEIVER.port() + "/hook";
    }

    protected static RequestPostProcessor admin() {
        return jwt().jwt(token -> token.subject("admin-user")).authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    protected static RequestPostProcessor customer() {
        return jwt().jwt(token -> token.subject("customer-1"))
            .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
    }

    /** Registers an active webhook for the given event ids (1 product updated, 2 order created, 3 status). */
    protected long createWebhook(String url, long... eventIds) throws Exception {
        String events = java.util.Arrays.stream(eventIds).mapToObj(id -> "{\"id\":" + id + "}")
            .collect(Collectors.joining(","));
        String body = mockMvc.perform(post("/backoffice/webhooks").with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"payloadUrl\":\"" + url + "\",\"secret\":\"" + SECRET + "\",\"isActive\":true,"
                    + "\"contentType\":\"application/json\",\"events\":[" + events + "]}"))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    /** The expected signature of a received delivery: HMAC-SHA256 of timestamp + "." + raw body (5-69). */
    protected static String hmac(ReceiverStub.Request request, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        mac.update((request.header("X-Shopapp-Timestamp") + ".").getBytes(StandardCharsets.UTF_8));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(request.body()));
    }

    /** A delivery log row as a map (status, attempts, response_code, last_error, event_name, ...). */
    protected Map<String, Object> notification(long id) {
        return jdbc.queryForMap("SELECT * FROM webhook_event_notification WHERE id = ?", id);
    }

    protected List<Map<String, Object>> notificationsOf(long webhookId) {
        return jdbc.queryForList("SELECT * FROM webhook_event_notification WHERE webhook_id = ? ORDER BY id",
            webhookId);
    }

    protected void send(String topic, long id, String value) throws Exception {
        kafka.send(topic, "{\"id\":" + id + "}", value).get();
    }

    /** Polls until the condition holds (15 s at most). */
    protected static void await(String what, BooleanSupplier condition) throws InterruptedException {
        await(what, Duration.ofSeconds(15), condition);
    }

    protected static void await(String what, Duration timeout, BooleanSupplier condition)
        throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("Timed out waiting for: " + what);
            }
            Thread.sleep(50);
        }
    }
}
