package com.shopapp.promotion.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.shopapp.promotion.PostgresContainerSupport;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Full application, real PostgreSQL, product replaced by one {@link ProductStub} shared by every test class (one
 * cached Spring context); tables and stub emptied before each test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresContainerSupport.class)
public abstract class AbstractPromotionIT {

    protected static final String CUSTOMER_TOKEN = "customer-token";
    protected static final ProductStub PRODUCT = new ProductStub();

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbc;

    @DynamicPropertySource
    static void services(DynamicPropertyRegistry registry) {
        registry.add("shopapp.services.product", PRODUCT::url);
    }

    @BeforeEach
    void cleanState() {
        jdbc.execute("TRUNCATE promotion_usage, promotion_apply, promotion RESTART IDENTITY CASCADE");
        PRODUCT.reset();
    }

    protected static RequestPostProcessor admin() {
        return jwt().jwt(token -> token.subject("admin-user"))
            .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    protected static RequestPostProcessor customer() {
        return customer("customer-1");
    }

    protected static RequestPostProcessor customer(String subject) {
        return jwt().jwt(token -> token.tokenValue(CUSTOMER_TOKEN).subject(subject))
            .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
    }

    protected static Instant daysFromNow(long days) {
        return Instant.now().plus(days, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
    }

    /** A valid create body: active, running now, 10 % on product 1, unlimited; tweak with {@link String#replace}. */
    protected static String promotionJson(String code) {
        return promotionJson(code, daysFromNow(-1), daysFromNow(10));
    }

    protected static String promotionJson(String code, Instant start, Instant end) {
        return """
            {"name":"Promo %1$s","slug":"promo-%2$s","description":"Ten percent","couponCode":"%1$s",
             "discountType":"PERCENTAGE","discountPercentage":10,"discountAmount":null,
             "usageType":"UNLIMITED","usageLimit":null,"minimumOrderPurchaseAmount":0,"isActive":true,
             "startDate":"%3$s","endDate":"%4$s","applyTo":"PRODUCT","productIds":[1],"brandIds":[],
             "categoryIds":[]}
            """.formatted(code, code.toLowerCase().replace('_', '-'), start, end);
    }

    protected long createPromotion(String json) throws Exception {
        String body = mockMvc.perform(post("/backoffice/promotions").with(admin())
                .contentType(MediaType.APPLICATION_JSON).content(json))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    protected int usageCount(long promotionId) {
        return jdbc.queryForObject("select usage_count from promotion where id = ?", Integer.class, promotionId);
    }
}
