package com.shopapp.search.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import com.shopapp.search.model.Product;
import java.time.Duration;
import java.time.Instant;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.kafka.KafkaContainer;

/**
 * Full application against real Kafka and Elasticsearch (one container each per JVM, small heaps) with product
 * replaced by a {@link ProductStub}; the index and the stub are emptied before each test. Retries are fast here
 * (infrastructure failures: 100 ms doubling to 400 ms, unbounded; other failures: 2 retries 100 ms apart).
 */
@SpringBootTest(properties = {
    "search.cdc.retry.initial-interval-ms=100",
    "search.cdc.retry.multiplier=2",
    "search.cdc.retry.max-interval-ms=400",
    "search.cdc.retry.other-attempts=2",
    "search.cdc.retry.other-interval-ms=100"})
@AutoConfigureMockMvc
public abstract class AbstractSearchIT {

    public static final String TOPIC = "dbproduct.public.product";
    public static final String DLT = TOPIC + "-dlt";
    protected static final ProductStub PRODUCT = new ProductStub();

    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.4.0");
    static final ElasticsearchContainer ELASTIC =
        new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch:9.4.8")
            .withEnv("xpack.security.enabled", "false")
            .withEnv("ES_JAVA_OPTS", "-Xms256m -Xmx256m")
            .withStartupTimeout(Duration.ofMinutes(3));

    static {
        KAFKA.start();
        ELASTIC.start();
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ElasticsearchOperations operations;

    @Autowired
    protected KafkaTemplate<String, String> kafka;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.elasticsearch.uris", () -> "http://" + ELASTIC.getHttpHostAddress());
        registry.add("shopapp.services.product", PRODUCT::url);
    }

    protected static String bootstrapServers() {
        return KAFKA.getBootstrapServers();
    }

    @BeforeEach
    void cleanState() {
        // a new index: deleting documents would keep their versions (index.gc_deletes) and refuse re-used ids
        operations.indexOps(Product.class).delete();
        operations.indexOps(Product.class).createWithMapping();
        PRODUCT.reset();
    }

    protected void refresh() {
        operations.indexOps(Product.class).refresh();
    }

    protected boolean indexed(long id) {
        refresh();
        return operations.get(String.valueOf(id), Product.class) != null;
    }

    protected Product document(long id) {
        refresh();
        return operations.get(String.valueOf(id), Product.class);
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

    /** Polls until the condition holds (15 s at most). */
    protected static void await(String what, BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
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
}
