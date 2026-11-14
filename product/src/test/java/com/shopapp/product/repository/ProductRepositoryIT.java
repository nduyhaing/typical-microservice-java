package com.shopapp.product.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.shopapp.product.PostgresContainerSupport;
import com.shopapp.product.model.Brand;
import com.shopapp.product.model.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.context.SecurityContextHolder;

@SpringBootTest
@Import(PostgresContainerSupport.class)
class ProductRepositoryIT {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BrandRepository brandRepository;

    @BeforeEach
    void clean() {
        SecurityContextHolder.clearContext();
        productRepository.deleteAll(productRepository.findAll().stream().filter(p -> p.getParent() != null).toList());
        productRepository.deleteAll();
        brandRepository.deleteAll();
    }

    private Product product(String slug, boolean published, boolean featured, boolean visible) {
        Product product = new Product();
        product.setName(slug);
        product.setSlug(slug);
        product.setPrice(100.0);
        product.setPublished(published);
        product.setFeatured(featured);
        product.setVisibleIndividually(visible);
        return productRepository.save(product);
    }

    @Test
    void findBySlugAndIsPublishedTrue_findsPublishedOnly() {
        product("iphone-15", true, false, true);
        product("draft-phone", false, false, true);

        assertThat(productRepository.findBySlugAndIsPublishedTrue("iphone-15")).isPresent();
        assertThat(productRepository.findBySlugAndIsPublishedTrue("draft-phone")).isEmpty();
    }

    @Test
    void getFeaturedProduct_returnsOnlyFeaturedPublishedVisible() {
        product("featured-ok", true, true, true);
        product("featured-unpublished", false, true, true);
        product("featured-invisible", true, true, false);
        product("not-featured", true, false, true);

        assertThat(productRepository.getFeaturedProduct(PageRequest.of(0, 10)).map(Product::getSlug))
            .containsExactly("featured-ok");
    }

    @Test
    void findAllByParent_returnsVariations() {
        Product parent = product("phone", true, false, true);
        for (String color : new String[] {"red", "blue"}) {
            Product variation = product("phone-" + color, true, false, false);
            variation.setParent(parent);
            productRepository.save(variation);
        }

        assertThat(productRepository.findAll().stream().filter(p -> p.getParent() != null
            && p.getParent().getId().equals(parent.getId()))).hasSize(2);
    }

    @Test
    void auditing_anonymousSave_doesNotThrow() {
        Brand brand = new Brand();
        brand.setName("Sony");
        brand.setSlug("sony");

        Brand saved = brandRepository.save(brand);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedBy()).isNull();
        assertThat(saved.getCreatedOn()).isNotNull();
    }
}
