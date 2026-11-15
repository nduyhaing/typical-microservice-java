package com.shopapp.sampledata.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shopapp.sampledata.ProductDatabaseSupport;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
class SampleDataIT extends ProductDatabaseSupport {

    private static final String URL = "/backoffice/sampledata";

    private final JdbcTemplate admin = new JdbcTemplate(dataSource("product"));

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void emptyProductDatabase() {
        admin.execute("TRUNCATE brand, category, product, product_category, product_option, product_option_combination,"
            + " product_option_value, product_attribute_group, product_attribute, product_attribute_value,"
            + " product_related, product_image, stock_reservation, stock_adjustment RESTART IDENTITY CASCADE");
        admin.execute("DROP TRIGGER IF EXISTS fail_related ON product_related");
    }

    private static RequestPostProcessor adminToken() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private ResultActions load() throws Exception {
        return mockMvc.perform(post(URL).with(adminToken()));
    }

    private int count(String table) {
        return admin.queryForObject("select count(*) from " + table, Integer.class);
    }

    @Test
    void anonymous_isUnauthorized_customer_isForbidden_andNothingIsInserted() throws Exception {
        // CSRF stays on like in every resource server here: an anonymous POST without a token is refused (403)
        // before authentication, with one it is 401
        mockMvc.perform(post(URL)).andExpect(status().isForbidden());
        mockMvc.perform(post(URL).with(csrf())).andExpect(status().isUnauthorized());
        mockMvc.perform(post(URL).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
            .andExpect(status().isForbidden());

        assertThat(count("product")).isZero();
        assertThat(count("brand")).isZero();
    }

    @Test
    void firstRun_insertsTheCatalog_secondRun_insertsNothing() throws Exception {
        load().andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(79))
            .andExpect(jsonPath("$.republished").value(0))
            .andExpect(jsonPath("$.inserted.brand").value(4))
            .andExpect(jsonPath("$.inserted.category").value(5))
            .andExpect(jsonPath("$.inserted.product").value(25))
            .andExpect(jsonPath("$.inserted.product_category").value(22))
            .andExpect(jsonPath("$.inserted.product_option_combination").value(2))
            .andExpect(jsonPath("$.inserted.product_related").value(6))
            .andExpect(jsonPath("$.message").value("Sample data loaded: 79 rows inserted, 0 products republished."));
        List<String> afterFirst = catalogSnapshot(admin);

        load().andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(0))
            .andExpect(jsonPath("$.republished").value(0))
            .andExpect(jsonPath("$.inserted.product").value(0))
            .andExpect(jsonPath("$.message").value("Sample data is already loaded; nothing to do."));

        assertThat(catalogSnapshot(admin)).isEqualTo(afterFirst);
        assertThat(count("product")).isEqualTo(25);
    }

    @Test
    void theLoadedCatalog_isExactlyTheDevSeed() throws Exception {
        load().andExpect(status().isOk());

        admin.execute("DROP DATABASE IF EXISTS seeded");
        admin.execute("CREATE DATABASE seeded");
        migrate(dataSource("seeded"), "sample-data");

        List<String> seeded = catalogSnapshot(new JdbcTemplate(dataSource("seeded")));
        assertThat(seeded).hasSize(79);
        assertThat(catalogSnapshot(admin)).containsExactlyElementsOf(seeded);
    }

    @Test
    void softDeletedAndHardDeletedSampleProducts_areRestored() throws Exception {
        load().andExpect(status().isOk());
        // product's admin delete only unpublishes (3a-17); for a variation it also drops its option combination
        admin.update("update product set is_published = false, last_modified_on = now() - interval '1 day'"
            + " where slug in ('iphone-16', 'galaxy-s24-black')");
        admin.update("delete from product_option_combination where product_id ="
            + " (select id from product where slug = 'galaxy-s24-black')");
        // gone entirely (e.g. removed in the database)
        admin.update("delete from product_category where product_id = (select id from product where slug = 'usb-c-cable')");
        admin.update("delete from product_related where related_product_id ="
            + " (select id from product where slug = 'usb-c-cable')");
        admin.update("delete from product where slug = 'usb-c-cable'");

        load().andExpect(status().isOk())
            .andExpect(jsonPath("$.republished").value(2))
            .andExpect(jsonPath("$.inserted.product").value(1))
            .andExpect(jsonPath("$.inserted.product_category").value(1))
            .andExpect(jsonPath("$.inserted.product_related").value(1))
            .andExpect(jsonPath("$.inserted.product_option_combination").value(1))
            .andExpect(jsonPath("$.total").value(4));

        assertThat(admin.queryForList("select slug from product where not is_published", String.class))
            .containsExactly("prototype-phone");
        // a republish is a new version of the product: recommendation and search see it as newer than the removal
        // (decisions.md 5-70)
        assertThat(admin.queryForObject("select count(*) from product where slug in ('iphone-16', 'galaxy-s24-black')"
            + " and last_modified_on > now() - interval '1 minute'", Integer.class)).isEqualTo(2);
        assertThat(admin.queryForObject("select count(*) from product_category pc join product p on p.id ="
            + " pc.product_id where p.slug = 'usb-c-cable'", Integer.class)).isEqualTo(1);
        assertThat(count("product")).isEqualTo(25);
    }

    @Test
    void adminCreatedProductsAndAdminEdits_areLeftAlone() throws Exception {
        load().andExpect(status().isOk());
        admin.update("insert into product (name, slug, price, is_published, brand_id) values ('My own', 'my-own', 5,"
            + " false, (select id from brand where slug = 'apple'))");
        admin.update("update product set price = 1, name = 'Galaxy S24 (sale)', stock_quantity = 3"
            + " where slug = 'galaxy-s24'");
        admin.update("update brand set name = 'Apple Inc.' where slug = 'apple'");
        // the seed keeps its prototype unpublished; an admin who published it is not overruled either
        admin.update("update product set is_published = true where slug = 'prototype-phone'");

        load().andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0))
            .andExpect(jsonPath("$.republished").value(0));

        Map<String, Object> galaxy = admin.queryForMap(
            "select name, price, stock_quantity from product where slug = 'galaxy-s24'");
        assertThat(galaxy).containsEntry("name", "Galaxy S24 (sale)").containsEntry("price", 1.0)
            .containsEntry("stock_quantity", 3L);
        assertThat(admin.queryForObject("select name from brand where slug = 'apple'", String.class))
            .isEqualTo("Apple Inc.");
        assertThat(admin.queryForObject("select is_published from product where slug = 'my-own'", Boolean.class))
            .isFalse();
        assertThat(admin.queryForObject("select is_published from product where slug = 'prototype-phone'",
            Boolean.class)).isTrue();
        assertThat(count("product")).isEqualTo(26);
    }

    @Test
    void stockReservationsAreUntouched_andTheLoginCannotReachThem() throws Exception {
        load().andExpect(status().isOk());
        admin.update("insert into stock_reservation (reference, product_id, quantity, owner, created_on)"
            + " values ('order-1', (select id from product where slug = 'iphone-16'), 2, 'c-1', now())");
        admin.update("insert into stock_adjustment (reference, created_on) values ('adj-1', now())");
        List<Map<String, Object>> reservations = admin.queryForList("select * from stock_reservation");

        load().andExpect(status().isOk());

        assertThat(admin.queryForList("select * from stock_reservation")).isEqualTo(reservations);
        assertThat(count("stock_adjustment")).isEqualTo(1);
        for (String privilege : List.of("SELECT", "INSERT", "UPDATE", "DELETE")) {
            assertThat(hasTablePrivilege("stock_reservation", privilege)).as(privilege).isFalse();
            assertThat(hasTablePrivilege("stock_adjustment", privilege)).as(privilege).isFalse();
        }
        assertThat(hasTablePrivilege("product", "DELETE")).isFalse();
        assertThat(hasTablePrivilege("product", "TRUNCATE")).isFalse();
        assertThat(admin.queryForObject(
            "select has_column_privilege('sampledata', 'product', 'price', 'UPDATE')", Boolean.class)).isFalse();
    }

    @Test
    void aFailingStatement_rollsBackTheWholeLoad_andIsReported() throws Exception {
        admin.execute("CREATE OR REPLACE FUNCTION fail_insert() RETURNS trigger LANGUAGE plpgsql AS"
            + " $$ BEGIN RAISE EXCEPTION 'boom'; END $$");
        admin.execute("CREATE TRIGGER fail_related BEFORE INSERT ON product_related"
            + " FOR EACH ROW EXECUTE FUNCTION fail_insert()");

        load().andExpect(status().is5xxServerError())
            .andExpect(jsonPath("$.detail").exists());

        assertThat(count("brand")).isZero();
        assertThat(count("product")).isZero();
    }

    private boolean hasTablePrivilege(String table, String privilege) {
        return admin.queryForObject("select has_table_privilege('sampledata', ?, ?)", Boolean.class, table, privilege);
    }
}
