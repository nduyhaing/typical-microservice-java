package com.shopapp.product;

import static org.assertj.core.api.Assertions.assertThat;

import com.shopapp.product.service.ProductDetailService;
import com.shopapp.product.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The dev-only catalog loaded with the sample-data Liquibase context, as docker compose runs it.
 */
@SpringBootTest(properties = "spring.liquibase.contexts=sample-data")
@Import(PostgresContainerSupport.class)
class SampleDataIT {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductDetailService productDetailService;

    @Test
    void seedsCategoriesBrandsAndEnoughProducts() {
        assertThat(jdbc.queryForList("select slug from category", String.class))
            .contains("phones", "laptops", "tablets", "audio", "accessories");
        assertThat(jdbc.queryForList("select slug from brand", String.class))
            .contains("apple", "samsung", "sony", "dell");
        assertThat(jdbc.queryForObject(
            "select count(*) from product where is_published and is_visible_individually", Long.class))
            .isGreaterThanOrEqualTo(20);
    }

    @Test
    void galaxyS24IsFeaturedWithTwoVariationsAttributesAndRelatedProducts() {
        var detail = productDetailService.getProductDetail("galaxy-s24");
        assertThat(detail.price()).isEqualTo(799.0);
        assertThat(detail.isFeatured()).isTrue();
        assertThat(detail.brandName()).isEqualTo("Samsung");
        assertThat(detail.productAttributeGroups()).extracting("name").contains("Display", "Battery");

        assertThat(productDetailService.getProductVariationsByParentId(detail.id()))
            .extracting("price").containsExactlyInAnyOrder(799.0, 819.0);
        assertThat(productDetailService.getOptionValues(detail.id()))
            .extracting("productOptionValue").containsExactlyInAnyOrder("Black", "Violet");
        assertThat(productService.getRelatedProductsStorefront(detail.id(), 0, 10).productContent()).hasSizeGreaterThanOrEqualTo(2);
        assertThat(productService.getListFeaturedProducts(0, 50).productList()).extracting("slug").contains("galaxy-s24");
    }

    @Test
    void bareAndUnpublishedProductsExist() {
        var cable = productDetailService.getProductDetail("usb-c-cable");
        assertThat(cable.productAttributeGroups()).isEmpty();
        assertThat(cable.hasOptions()).isFalse();
        assertThat(jdbc.queryForObject("select is_published from product where slug = 'prototype-phone'", Boolean.class))
            .isFalse();
    }
}
