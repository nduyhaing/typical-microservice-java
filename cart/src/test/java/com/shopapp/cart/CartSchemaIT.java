package com.shopapp.cart;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(PostgresContainerSupport.class)
class CartSchemaIT {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void cartItemTableHasCompositeKeyOfCustomerAndProduct() {
        assertThat(jdbc.queryForList("""
            select kcu.column_name from information_schema.table_constraints tc
            join information_schema.key_column_usage kcu on tc.constraint_name = kcu.constraint_name
            where tc.table_name = 'cart_item' and tc.constraint_type = 'PRIMARY KEY'""", String.class))
            .containsExactlyInAnyOrder("customer_id", "product_id");
    }
}
