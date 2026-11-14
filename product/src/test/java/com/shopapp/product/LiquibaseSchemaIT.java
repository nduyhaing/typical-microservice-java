package com.shopapp.product;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(PostgresContainerSupport.class)
class LiquibaseSchemaIT {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void schemaHasYasProductTables() {
        assertThat(jdbc.queryForList(
            "select table_name from information_schema.tables where table_schema = 'public'", String.class))
            .contains("product", "brand", "category", "product_category", "product_image", "product_option",
                "product_option_value", "product_option_combination", "product_related", "product_attribute",
                "product_attribute_group", "product_attribute_value", "product_template",
                "product_attribute_template");
    }

    @Test
    void noSampleDataWithoutContext() {
        assertThat(jdbc.queryForObject("select count(*) from product", Long.class)).isZero();
    }
}
