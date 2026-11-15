package com.shopapp.recommendation.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.BooleanSupplier;
import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Full application against real Kafka and Postgres with pgvector (one container each per JVM) and the real local
 * embedding model (downloaded once into {@code ~/.cache/shopapp-onnx-model}), with product replaced by a
 * {@link ProductStub}. The table and the stub are emptied before each test. Retries are fast here
 * (infrastructure failures: 100 ms doubling to 400 ms, unbounded; other failures: 2 retries 100 ms apart).
 */
@SpringBootTest(properties = {
    "recommendation.cdc.retry.initial-interval-ms=100",
    "recommendation.cdc.retry.multiplier=2",
    "recommendation.cdc.retry.max-interval-ms=400",
    "recommendation.cdc.retry.other-attempts=2",
    "recommendation.cdc.retry.other-interval-ms=100",
    "recommendation.embedding.cache-directory=${user.home}/.cache/shopapp-onnx-model"})
@AutoConfigureMockMvc
@Import(AbstractRecommendationIT.Topics.class)
public abstract class AbstractRecommendationIT {

    public static final String TOPIC = "dbproduct.public.product";
    public static final String DLT = TOPIC + "-recommendation-dlt";
    protected static final ProductStub PRODUCT = new ProductStub();

    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.4.0");
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
        DockerImageName.parse("pgvector/pgvector:0.8.7-pg16-trixie").asCompatibleSubstituteFor("postgres"));

    static {
        KAFKA.start();
        POSTGRES.start();
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcClient jdbc;

    @Autowired
    protected KafkaTemplate<String, String> kafka;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("shopapp.services.product", PRODUCT::url);
    }

    protected static String bootstrapServers() {
        return KAFKA.getBootstrapServers();
    }

    @BeforeEach
    void cleanState() {
        jdbc.sql("DELETE FROM product_embedding").update();
        PRODUCT.reset();
    }

    /** True when the product has a recommendable (embedded, not removed) row. */
    protected boolean embedded(long id) {
        return jdbc.sql("SELECT count(*) FROM product_embedding WHERE product_id = :id AND NOT deleted "
                + "AND embedding IS NOT NULL")
            .param("id", id).query(Long.class).single() > 0;
    }

    protected String storedName(long id) {
        return jdbc.sql("SELECT name FROM product_embedding WHERE product_id = :id").param("id", id)
            .query(String.class).optional().orElse(null);
    }

    protected void phone(long id, String name, String brand, int modifiedSecond) {
        PRODUCT.product(id, name, brand, List.of("Phones"), brand + " smartphone with a large display",
            at(modifiedSecond));
    }

    protected void send(long id, String value) throws Exception {
        kafka.send(TOPIC, "{\"id\":" + id + "}", value).get();
    }

    protected static String event(String op, long id) {
        return "{\"before\":null,\"after\":{\"id\":" + id + "},\"op\":\"" + op + "\",\"source\":{}}";
    }

    protected static Instant at(int second) {
        return Instant.parse("2026-11-15T00:00:00Z").plusSeconds(second);
    }

    /** Polls until the condition holds (20 s at most). */
    protected static void await(String what, BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("Timed out waiting for: " + what);
            }
            Thread.sleep(100);
        }
    }

    protected static RequestPostProcessor admin() {
        return jwt().jwt(token -> token.tokenValue("admin-token").subject("admin-user"))
            .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    protected static RequestPostProcessor customer() {
        return jwt().jwt(token -> token.subject("customer-1"))
            .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
    }

    /** The CDC topic exists before the listener starts, as in compose (Debezium creates it). */
    @TestConfiguration(proxyBeanMethods = false)
    static class Topics {
        @Bean
        NewTopic productCdcTopic() {
            return new NewTopic(TOPIC, 1, (short) 1);
        }
    }
}
