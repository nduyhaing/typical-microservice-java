package com.shopapp.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;
import javax.sql.DataSource;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(PostgresContainerSupport.class)
class OrderSchemaIT {

    /** Changesets up to and including shop_app's 0019 (one-order-per-checkout), i.e. before the money migration. */
    private static final int CHANGESETS_BEFORE_MONEY_MIGRATION = 19;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DataSource dataSource;

    @Test
    void schemaHasYasOrderTables() {
        assertThat(jdbc.queryForList(
            "select table_name from information_schema.tables where table_schema = 'public'", String.class))
            .contains("order", "order_item", "order_address", "checkout", "checkout_item");
    }

    @Test
    void moneyColumnsAreNumeric() {
        assertThat(numericType("order", "total_tax")).isEqualTo("numeric(19,2)");
        assertThat(numericType("order", "total_discount_amount")).isEqualTo("numeric(19,2)");
        assertThat(numericType("order_item", "tax_amount")).isEqualTo("numeric(19,2)");
        assertThat(numericType("order_item", "discount_amount")).isEqualTo("numeric(19,2)");
        assertThat(numericType("checkout", "total_tax")).isEqualTo("numeric(19,2)");
        assertThat(numericType("checkout_item", "tax")).isEqualTo("numeric(19,2)");
        // tax rates have up to 4 decimals (tax service numeric(7,4), 3c-10)
        assertThat(numericType("order_item", "tax_percent")).isEqualTo("numeric(7,4)");
        assertThat(numericType("checkout_item", "tax_percent")).isEqualTo("numeric(7,4)");
        assertThat(jdbc.queryForObject("select data_type from information_schema.columns where table_schema = "
            + "'public' and table_name = 'checkout_item' and column_name = 'tax_class_id'", String.class))
            .isEqualTo("bigint");
    }

    private String numericType(String table, String column) {
        Map<String, Object> row = jdbc.queryForMap("select data_type, numeric_precision, numeric_scale from "
            + "information_schema.columns where table_schema = 'public' and table_name = ? and column_name = ?",
            table, column);
        return row.get("data_type") + "(" + row.get("numeric_precision") + "," + row.get("numeric_scale") + ")";
    }

    /** Runs the changelog in a scratch schema: float values written before the migration survive it, rounded. */
    @Test
    void moneyMigrationKeepsExistingValues() throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("drop schema if exists money_migration cascade");
            statement.execute("create schema money_migration");
            statement.execute("set search_path to money_migration");
            try {
                migrateAndCheck(connection, statement);
            } finally {
                // pooled connection: give it back with the normal search path
                statement.execute("set search_path to public");
                statement.execute("drop schema money_migration cascade");
            }
        }
    }

    private void migrateAndCheck(Connection connection, Statement statement) throws Exception {
        Database database = DatabaseFactory.getInstance()
            .findCorrectDatabaseImplementation(new JdbcConnection(connection));
        database.setDefaultSchemaName("money_migration");
        Liquibase liquibase = new Liquibase("db/changelog/db.changelog-master.yaml",
            new ClassLoaderResourceAccessor(), database);
        liquibase.update(CHANGESETS_BEFORE_MONEY_MIGRATION, new Contexts(), new LabelExpression());
        statement.execute("insert into order_address (contact_name, phone, address_line1) values ('a', 'b', 'c')");
        statement.execute("insert into \"order\" (number_item, total_tax, total_discount_amount, "
            + "shipping_address_id, billing_address_id) values (1, 81.9, 0.125, 1, 1)");

        liquibase.update(new Contexts(), new LabelExpression());

        try (ResultSet rows = statement.executeQuery("select total_tax, total_discount_amount from \"order\"")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getBigDecimal("total_tax")).isEqualTo(new BigDecimal("81.90"));
            assertThat(rows.getBigDecimal("total_discount_amount")).isEqualTo(new BigDecimal("0.13"));
        }
    }
}
