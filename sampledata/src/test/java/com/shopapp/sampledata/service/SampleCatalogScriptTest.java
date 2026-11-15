package com.shopapp.sampledata.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class SampleCatalogScriptTest {

    @Test
    void insert_becomesInsertIfMissing_keyedByTheNaturalKey() {
        SampleCatalogScript script = SampleCatalogScript.parse("""
            --liquibase formatted sql

            --changeset shopapp:sample-data-1 context:sample-data
            -- a comment
            insert into brand (name, slug, is_published) values ('Apple', 'apple', true);
            """);

        assertThat(script.statements()).singleElement().satisfies(statement -> {
            assertThat(statement.table()).isEqualTo("brand");
            assertThat(statement.insertSql()).isEqualTo(
                "insert into brand (name, slug, is_published) select 'Apple', 'apple', true"
                    + " where not exists (select 1 from brand existing where existing.slug = 'apple')");
            assertThat(statement.restoreSql()).isNull();
        });
    }

    @Test
    void compositeKeys_useTheSubselectExpressions() {
        SampleCatalogScript script = SampleCatalogScript.parse(
            "insert into product_category (display_order, is_featured_product, category_id, product_id) values (0,"
                + " false, (select id from category where slug = 'phones'), (select id from product where slug ="
                + " 'galaxy-s24'));");

        assertThat(script.statements().getFirst().insertSql()).isEqualTo(
            "insert into product_category (display_order, is_featured_product, category_id, product_id) select 0,"
                + " false, (select id from category where slug = 'phones'), (select id from product where slug ="
                + " 'galaxy-s24') where not exists (select 1 from product_category existing where existing.product_id"
                + " = (select id from product where slug = 'galaxy-s24') and existing.category_id = (select id from"
                + " category where slug = 'phones'))");
    }

    @Test
    void publishedProduct_isRepublishedWhenSoftDeleted_unpublishedOneIsNot() {
        SampleCatalogScript script = SampleCatalogScript.parse("""
            insert into product (name, slug, is_published, brand_id) values ('Galaxy S24', 'galaxy-s24', true, NULL);
            insert into product (name, slug, is_published, brand_id) values ('Proto', 'proto', false, NULL);
            """);

        assertThat(script.statements().get(0).restoreSql()).isEqualTo(
            "update product set is_published = true where slug = 'galaxy-s24' and is_published is distinct from true");
        assertThat(script.statements().get(1).restoreSql()).isNull();
    }

    @Test
    void commasAndParenthesesInsideStrings_doNotSplitValues() {
        SampleCatalogScript script = SampleCatalogScript.parse(
            "insert into category (name, slug) values ('Phones, (and) ''more''', 'phones');");

        assertThat(script.statements().getFirst().insertSql()).endsWith(
            "select 'Phones, (and) ''more''', 'phones' where not exists (select 1 from category existing where"
                + " existing.slug = 'phones')");
    }

    @Test
    void unknownTable_isRejected_soNothingOutsideTheCatalogCanBeWritten() {
        assertThatThrownBy(() -> SampleCatalogScript.parse(
            "insert into stock_reservation (reference, product_id) values ('r', 1);"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("stock_reservation");
    }

    @Test
    void anythingButASingleLineInsert_isRejected() {
        for (String sql : List.of(
            "delete from product;",
            "update product set price = 0;",
            "insert into brand (name, slug) values ('a', 'a'); delete from product;",
            "insert into brand (name, slug) values ('a');",
            "insert into brand (name) values ('a');")) {
            assertThatThrownBy(() -> SampleCatalogScript.parse(sql))
                .as(sql)
                .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void theBundledProductSeed_parses() throws Exception {
        String seed = new ClassPathResource(SampleCatalogScript.LOCATION).getContentAsString(
            java.nio.charset.StandardCharsets.UTF_8);

        SampleCatalogScript script = SampleCatalogScript.parse(seed);

        assertThat(script.statements()).hasSize(79);
        assertThat(script.statements()).extracting(SampleCatalogScript.Statement::table).doesNotContain(
            "stock_reservation", "stock_adjustment");
    }
}
