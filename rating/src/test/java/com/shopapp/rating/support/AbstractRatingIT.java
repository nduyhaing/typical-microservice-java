package com.shopapp.rating.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import com.shopapp.rating.PostgresContainerSupport;
import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Full application, real PostgreSQL, order and product replaced by one {@link ServicesStub} shared by every test
 * class (one cached Spring context); the table and the stub are emptied before each test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresContainerSupport.class)
public abstract class AbstractRatingIT {

    protected static final ServicesStub STUB = new ServicesStub();

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbc;

    @DynamicPropertySource
    static void services(DynamicPropertyRegistry registry) {
        registry.add("shopapp.services.order", STUB::orderUrl);
        registry.add("shopapp.services.product", STUB::productUrl);
    }

    @BeforeEach
    void cleanState() {
        jdbc.execute("TRUNCATE rating RESTART IDENTITY");
        STUB.reset();
    }

    protected static RequestPostProcessor admin() {
        return jwt().jwt(token -> token.tokenValue("token-admin").subject("admin-user"))
            .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    /** A customer whose token is {@code token-<id>}, with Keycloak's name claims. */
    protected static RequestPostProcessor customer(String id, String givenName, String familyName) {
        return jwt().jwt(token -> {
            token.tokenValue("token-" + id).subject(id).claim("preferred_username", id);
            if (givenName != null) {
                token.claim("given_name", givenName);
            }
            if (familyName != null) {
                token.claim("family_name", familyName);
            }
        }).authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
    }

    protected static RequestPostProcessor customer(String id) {
        return customer(id, "Customer", id.toUpperCase());
    }

    protected static String ratingJson(long productId, int star, String content) {
        return "{\"productId\":" + productId + ",\"star\":" + star + ",\"content\":"
            + (content == null ? "null" : "\"" + content + "\"") + ",\"productName\":\"Sent by the client\"}";
    }

    /** Inserts a rating straight into the table; returns its id. */
    protected long insertRating(long productId, String productName, int star, String content, String createdBy,
                                String firstName, String lastName, Instant createdOn) {
        return jdbc.queryForObject("""
                insert into rating (product_id, product_name, rating_star, content, created_by, first_name, last_name,
                                    created_on, last_modified_on)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?) returning id""", Long.class,
            productId, productName, star, content, createdBy, firstName, lastName, Timestamp.from(createdOn),
            Timestamp.from(createdOn));
    }

    protected int ratingCount() {
        return jdbc.queryForObject("select count(*) from rating", Integer.class);
    }
}
