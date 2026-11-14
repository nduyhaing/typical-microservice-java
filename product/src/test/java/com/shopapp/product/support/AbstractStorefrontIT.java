package com.shopapp.product.support;

import com.shopapp.product.PostgresContainerSupport;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Base for storefront endpoint tests: full application, real PostgreSQL, empty catalog before each test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresContainerSupport.class, CatalogFixtures.class})
public abstract class AbstractStorefrontIT {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected CatalogFixtures fixtures;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void emptyCatalog() {
        jdbc.execute("TRUNCATE product_related, product_option_combination, product_option_value, product_option, "
            + "product_attribute_value, product_attribute, product_attribute_group, product_image, "
            + "product_category, product, category, brand RESTART IDENTITY CASCADE");
    }
}
