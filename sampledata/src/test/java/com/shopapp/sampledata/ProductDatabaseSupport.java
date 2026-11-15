package com.shopapp.sampledata;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import liquibase.integration.spring.SpringLiquibase;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * A PostgreSQL 16 "product" database built with product's own Liquibase changelog (schema only, like
 * {@code spring.liquibase.contexts=default}), the "sampledata" login with exactly the compose grants
 * (docker/postgres/sampledata-grants.sql), and the application connected as that login.
 */
public abstract class ProductDatabaseSupport {

    protected static final String APP_PASSWORD = "sampledata-test";

    protected static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16.16")
        .withDatabaseName("product");

    /** Catalog rows as JSON, ids replaced by the natural keys they point to, so two databases can be compared. */
    private static final Map<String, String> SNAPSHOT_QUERIES = new LinkedHashMap<>();

    static {
        SNAPSHOT_QUERIES.put("brand", "select to_jsonb(t) - 'id' from brand t order by slug");
        SNAPSHOT_QUERIES.put("category", "select to_jsonb(t) - 'id' from category t order by slug");
        SNAPSHOT_QUERIES.put("product", "select (to_jsonb(t) - 'id' - 'brand_id' - 'parent_id')"
            + " || jsonb_build_object('brand', b.slug, 'parent', p.slug) from product t"
            + " left join brand b on b.id = t.brand_id left join product p on p.id = t.parent_id order by t.slug");
        SNAPSHOT_QUERIES.put("product_category", "select (to_jsonb(t) - 'id' - 'product_id' - 'category_id')"
            + " || jsonb_build_object('product', p.slug, 'category', c.slug) from product_category t"
            + " join product p on p.id = t.product_id join category c on c.id = t.category_id order by 1");
        SNAPSHOT_QUERIES.put("product_option", "select to_jsonb(t) - 'id' from product_option t order by name");
        SNAPSHOT_QUERIES.put("product_option_combination",
            "select (to_jsonb(t) - 'id' - 'product_id' - 'product_option_id')"
                + " || jsonb_build_object('product', p.slug, 'option', o.name) from product_option_combination t"
                + " join product p on p.id = t.product_id join product_option o on o.id = t.product_option_id"
                + " order by 1");
        SNAPSHOT_QUERIES.put("product_option_value", "select (to_jsonb(t) - 'id' - 'product_id' - 'product_option_id')"
            + " || jsonb_build_object('product', p.slug, 'option', o.name) from product_option_value t"
            + " join product p on p.id = t.product_id join product_option o on o.id = t.product_option_id"
            + " order by 1");
        SNAPSHOT_QUERIES.put("product_attribute_group",
            "select to_jsonb(t) - 'id' from product_attribute_group t order by name");
        SNAPSHOT_QUERIES.put("product_attribute", "select (to_jsonb(t) - 'id' - 'product_attribute_group_id')"
            + " || jsonb_build_object('group', g.name) from product_attribute t"
            + " join product_attribute_group g on g.id = t.product_attribute_group_id order by 1");
        SNAPSHOT_QUERIES.put("product_attribute_value",
            "select (to_jsonb(t) - 'id' - 'product_id' - 'product_attribute_id')"
                + " || jsonb_build_object('product', p.slug, 'attribute', a.name) from product_attribute_value t"
                + " join product p on p.id = t.product_id join product_attribute a on a.id = t.product_attribute_id"
                + " order by 1");
        SNAPSHOT_QUERIES.put("product_related", "select jsonb_build_object('product', p.slug, 'related', r.slug)"
            + " from product_related t join product p on p.id = t.product_id"
            + " join product r on r.id = t.related_product_id order by 1");

        POSTGRES.start();
        DataSource admin = dataSource("product");
        migrate(admin, "default");
        JdbcTemplate jdbc = new JdbcTemplate(admin);
        jdbc.execute("CREATE ROLE sampledata LOGIN PASSWORD '" + APP_PASSWORD + "'");
        jdbc.execute("GRANT CONNECT ON DATABASE product TO sampledata");
        try {
            for (String statement : new ClassPathResource("db/grants/sampledata-grants.sql")
                .getContentAsString(StandardCharsets.UTF_8).replaceAll("(?m)^--.*$", "").split(";")) {
                if (!statement.isBlank()) {
                    jdbc.execute(statement);
                }
            }
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "sampledata");
        registry.add("spring.datasource.password", () -> APP_PASSWORD);
    }

    /** The superuser, for setting up and checking what the application must not do itself. */
    protected static DataSource dataSource(String database) {
        String url = POSTGRES.getJdbcUrl().replace("/product", "/" + database);
        return new DriverManagerDataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    /** Runs product's changelog with the given Liquibase contexts ("sample-data" also loads the dev seed). */
    protected static void migrate(DataSource dataSource, String contexts) {
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog("classpath:db/changelog/db.changelog-master.yaml");
        liquibase.setContexts(contexts);
        liquibase.setResourceLoader(new DefaultResourceLoader());
        try {
            liquibase.afterPropertiesSet();
        } catch (Exception e) {
            throw new IllegalStateException("Liquibase failed", e);
        }
    }

    protected static List<String> catalogSnapshot(JdbcTemplate jdbc) {
        List<String> rows = new ArrayList<>();
        SNAPSHOT_QUERIES.forEach((table, sql) ->
            jdbc.queryForList(sql, String.class).forEach(row -> rows.add(table + " " + row)));
        return rows;
    }
}
