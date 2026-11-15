package com.shopapp.product.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shopapp.product.model.Brand;
import com.shopapp.product.model.Category;
import com.shopapp.product.model.Product;
import com.shopapp.product.model.ProductImage;
import com.shopapp.product.repository.ProductImageRepository;
import com.shopapp.product.support.AbstractStorefrontIT;
import com.shopapp.product.support.MediaStub;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Image URLs come from the media service (here a JDK HttpServer stub); every list page resolves its images with one
 * {@code GET /medias?ids=} call (decisions.md 3b-10), and unknown media give an empty URL (2a-11).
 */
class ProductMediaIT extends AbstractStorefrontIT {

    private static final MediaStub MEDIA = new MediaStub();

    @Autowired
    private ProductImageRepository productImageRepository;

    @DynamicPropertySource
    static void mediaUrl(DynamicPropertyRegistry registry) {
        registry.add("shopapp.services.media", MEDIA::baseUrl);
    }

    @AfterAll
    static void stopMedia() {
        MEDIA.close();
    }

    @BeforeEach
    void resetMedia() {
        MEDIA.reset();
    }

    private static RequestPostProcessor admin() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private Product withThumbnail(String name, long mediaId) {
        Product product = fixtures.product(name, name.toLowerCase(), 10);
        product.setFeatured(true);
        product.setThumbnailMediaId(mediaId);
        return fixtures.save(product);
    }

    private void image(Product product, long mediaId) {
        ProductImage image = new ProductImage();
        image.setProduct(product);
        image.setImageId(mediaId);
        productImageRepository.save(image);
    }

    private void assertOneListCall(Long... ids) {
        assertThat(MEDIA.requests()).hasSize(1);
        assertThat(MEDIA.listedIds()).containsExactlyInAnyOrder(ids);
    }

    @Test
    void featured_thumbnailUrlsFromOneMediaCall() throws Exception {
        withThumbnail("A", 101);
        withThumbnail("B", 102);
        withThumbnail("C", 103);

        mockMvc.perform(get("/storefront/products/featured"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.productList[*].thumbnailUrl").value(containsInAnyOrder(
                MediaStub.urlOf(101), MediaStub.urlOf(102), MediaStub.urlOf(103))));
        assertOneListCall(101L, 102L, 103L);
    }

    @Test
    void unknownMedia_emptyUrl() throws Exception {
        withThumbnail("A", 101);
        MEDIA.missing(101L);

        mockMvc.perform(get("/storefront/products/featured"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.productList[0].thumbnailUrl").value(""));
    }

    @Test
    void productWithoutThumbnail_noMediaCall() throws Exception {
        Product product = fixtures.product("A", "a", 10);
        product.setFeatured(true);
        fixtures.save(product);

        mockMvc.perform(get("/storefront/products/featured"))
            .andExpect(jsonPath("$.productList[0].thumbnailUrl").value(""));
        assertThat(MEDIA.requests()).isEmpty();
    }

    @Test
    void multiQueryCategoryBrandAndListFeatured_oneMediaCallEach() throws Exception {
        Category phones = fixtures.category("Phones", "phones");
        Brand acme = fixtures.brand("Acme", "acme");
        Product a = fixtures.ofBrand(withThumbnail("A", 101), acme);
        Product b = fixtures.ofBrand(withThumbnail("B", 102), acme);
        fixtures.inCategory(a, phones);
        fixtures.inCategory(b, phones);

        mockMvc.perform(get("/storefront/products"))
            .andExpect(jsonPath("$.productContent[*].thumbnailUrl").value(containsInAnyOrder(
                MediaStub.urlOf(101), MediaStub.urlOf(102))));
        assertOneListCall(101L, 102L);

        MEDIA.reset();
        mockMvc.perform(get("/storefront/category/phones/products"))
            .andExpect(jsonPath("$.productContent[*].thumbnailUrl").value(containsInAnyOrder(
                MediaStub.urlOf(101), MediaStub.urlOf(102))));
        assertOneListCall(101L, 102L);

        MEDIA.reset();
        mockMvc.perform(get("/storefront/brand/acme/products"))
            .andExpect(jsonPath("$[*].thumbnailUrl").value(containsInAnyOrder(
                MediaStub.urlOf(101), MediaStub.urlOf(102))));
        assertOneListCall(101L, 102L);

        MEDIA.reset();
        mockMvc.perform(get("/storefront/products/list-featured")
                .param("productId", String.valueOf(a.getId()), String.valueOf(b.getId())))
            .andExpect(jsonPath("$[*].thumbnailUrl").value(containsInAnyOrder(
                MediaStub.urlOf(101), MediaStub.urlOf(102))));
        assertOneListCall(101L, 102L);

    }

    /** The order service's price lookup needs no images: it never waits for the media service (3b-18). */
    @Test
    void checkoutList_makesNoMediaCall() throws Exception {
        Product a = withThumbnail("A", 101);
        Product b = withThumbnail("B", 102);

        mockMvc.perform(get("/products").param("ids", a.getId() + "," + b.getId()).with(jwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.productCheckoutListVms[*].price").value(containsInAnyOrder(10.0, 10.0)))
            .andExpect(jsonPath("$.productCheckoutListVms[*].thumbnailUrl").value(containsInAnyOrder("", "")));
        assertThat(MEDIA.requests()).isEmpty();
    }

    /** A hanging media service costs at most its short read timeout (1.5 s), well below callers' 5 s (3b-18). */
    @Test
    void slowMedia_listAnswersWithinTwoSecondsWithEmptyUrl() throws Exception {
        withThumbnail("A", 101);
        MEDIA.delay(Duration.ofSeconds(4));

        long start = System.nanoTime();
        mockMvc.perform(get("/storefront/products/featured"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.productList[0].thumbnailUrl").value(""));
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(2000));
    }

    @Test
    void related_oneMediaCall() throws Exception {
        Product main = withThumbnail("Main", 100);
        fixtures.related(main, withThumbnail("A", 101));
        fixtures.related(main, withThumbnail("B", 102));

        mockMvc.perform(get("/storefront/products/related-products/{id}", main.getId()))
            .andExpect(jsonPath("$.productContent[*].thumbnailUrl").value(containsInAnyOrder(
                MediaStub.urlOf(101), MediaStub.urlOf(102))));
        assertOneListCall(101L, 102L);
    }

    @Test
    void detail_thumbnailAndGalleryFromOneMediaCall() throws Exception {
        Product product = withThumbnail("A", 101);
        image(product, 201);
        image(product, 202);

        mockMvc.perform(get("/storefront/product/a"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.thumbnailMediaUrl").value(MediaStub.urlOf(101)))
            .andExpect(jsonPath("$.productImageMediaUrls").value(containsInAnyOrder(
                MediaStub.urlOf(201), MediaStub.urlOf(202))));
        assertOneListCall(101L, 201L, 202L);
    }

    @Test
    void adminDetail_returnsImageIdsAndUrls() throws Exception {
        Product product = withThumbnail("A", 101);
        image(product, 201);

        mockMvc.perform(get("/backoffice/products/{id}", product.getId()).with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.thumbnailMedia.id").value(101))
            .andExpect(jsonPath("$.thumbnailMedia.url").value(MediaStub.urlOf(101)))
            .andExpect(jsonPath("$.productImageMedias[0].id").value(201))
            .andExpect(jsonPath("$.productImageMedias[0].url").value(MediaStub.urlOf(201)));
        assertOneListCall(101L, 201L);
    }

    @Test
    void variations_oneMediaCall() throws Exception {
        Product parent = withThumbnail("Parent", 100);
        parent.setHasOptions(true);
        fixtures.save(parent);
        Product red = fixtures.variation(parent, "Red", "red", 10);
        red.setThumbnailMediaId(101L);
        fixtures.save(red);
        Product blue = fixtures.variation(parent, "Blue", "blue", 10);
        blue.setThumbnailMediaId(102L);
        fixtures.save(blue);

        mockMvc.perform(get("/storefront/product-variations/{id}", parent.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].thumbnail.url").value(containsInAnyOrder(
                MediaStub.urlOf(101), MediaStub.urlOf(102))));
        assertOneListCall(101L, 102L);
    }

    @Test
    void categories_imageUrlsFromOneMediaCall() throws Exception {
        Category phones = fixtures.category("Phones", "phones");
        phones.setImageId(301L);
        fixtures.saveCategory(phones);
        Category laptops = fixtures.category("Laptops", "laptops");
        laptops.setImageId(302L);
        fixtures.saveCategory(laptops);

        mockMvc.perform(get("/storefront/categories"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].categoryImage.url").value(containsInAnyOrder(
                MediaStub.urlOf(301), MediaStub.urlOf(302))));
        assertOneListCall(301L, 302L);
    }
}
