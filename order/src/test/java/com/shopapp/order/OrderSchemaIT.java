package com.shopapp.order;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(PostgresContainerSupport.class)
class OrderSchemaIT {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void schemaHasYasOrderTables() {
        assertThat(jdbc.queryForList(
            "select table_name from information_schema.tables where table_schema = 'public'", String.class))
            .contains("order", "order_item", "order_address", "checkout", "checkout_item");
    }
}
