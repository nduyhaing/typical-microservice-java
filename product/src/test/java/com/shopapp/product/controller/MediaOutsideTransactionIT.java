package com.shopapp.product.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shopapp.product.model.Brand;
import com.shopapp.product.model.Category;
import com.shopapp.product.model.Product;
import com.shopapp.product.model.ProductImage;
import com.shopapp.product.repository.ProductImageRepository;
import com.shopapp.product.service.MediaService;
import com.shopapp.product.support.AbstractStorefrontIT;
import com.shopapp.product.support.MediaStub;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * No media-service call is made while a database transaction (and its pooled connection) is held, so a slow media
 * service cannot exhaust the connection pool (decisions.md 3b-19). The spy records, for every media lookup, whether a
 * transaction was active on the calling thread.
 */
class MediaOutsideTransactionIT extends AbstractStorefrontIT {

    private static final MediaStub MEDIA = new MediaStub();

    @MockitoSpyBean
    private MediaService mediaService;

    @Autowired
    private ProductImageRepository productImageRepository;

    private final List<Boolean> transactionActiveDuringMediaCall = new CopyOnWriteArrayList<>();

    @DynamicPropertySource
    static void mediaUrl(DynamicPropertyRegistry registry) {
        registry.add("shopapp.services.media", MEDIA::baseUrl);
    }

    @AfterAll
    static void stopMedia() {
        MEDIA.close();
    }

    @BeforeEach
    void recordTransactionState() {
        MEDIA.reset();
        transactionActiveDuringMediaCall.clear();
        doAnswer(invocation -> {
            transactionActiveDuringMediaCall.add(TransactionSynchronizationManager.isActualTransactionActive());
            return invocation.callRealMethod();
        }).when(mediaService).getMediaUrls(any());
        doAnswer(invocation -> {
            transactionActiveDuringMediaCall.add(TransactionSynchronizationManager.isActualTransactionActive());
            return invocation.callRealMethod();
        }).when(mediaService).getMedia(any());
    }

    private static RequestPostProcessor admin() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private Product withImages(Product product, long thumbnailId, long imageId) {
        product.setThumbnailMediaId(thumbnailId);
        Product saved = fixtures.save(product);
        ProductImage image = new ProductImage();
        image.setProduct(saved);
        image.setImageId(imageId);
        productImageRepository.save(image);
        return saved;
    }

    private void assertMediaLookedUpOutsideTransaction(MockHttpServletRequestBuilder request, ResultMatcher url)
        throws Exception {
        transactionActiveDuringMediaCall.clear();
        mockMvc.perform(request).andExpect(status().isOk()).andExpect(url);
        assertThat(transactionActiveDuringMediaCall).as(request.toString()).isNotEmpty().containsOnly(false);
    }

    @Test
    void everyImageResolvingEndpoint_callsMediaOutsideTheTransaction() throws Exception {
        Category phones = fixtures.category("Phones", "phones");
        phones.setImageId(301L);
        fixtures.saveCategory(phones);
        Brand acme = fixtures.brand("Acme", "acme");
        Product main = fixtures.product("Main", "main", 10);
        main.setFeatured(true);
        main.setHasOptions(true);
        main = withImages(fixtures.ofBrand(main, acme), 101, 201);
        fixtures.inCategory(main, phones);
        Product other = fixtures.product("Other", "other", 10);
        other.setFeatured(true);
        other = withImages(other, 102, 202);
        fixtures.related(main, other);
        Product red = fixtures.variation(main, "Red", "red", 10);
        withImages(red, 103, 203);

        assertMediaLookedUpOutsideTransaction(get("/storefront/products/featured"),
            jsonPath("$.productList[0].thumbnailUrl").isNotEmpty());
        assertMediaLookedUpOutsideTransaction(get("/storefront/products"),
            jsonPath("$.productContent[0].thumbnailUrl").isNotEmpty());
        assertMediaLookedUpOutsideTransaction(get("/storefront/category/phones/products"),
            jsonPath("$.productContent[0].thumbnailUrl").value(MediaStub.urlOf(101)));
        assertMediaLookedUpOutsideTransaction(get("/storefront/brand/acme/products"),
            jsonPath("$[0].thumbnailUrl").value(MediaStub.urlOf(101)));
        assertMediaLookedUpOutsideTransaction(
            get("/storefront/products/list-featured").param("productId", String.valueOf(main.getId())),
            jsonPath("$[0].thumbnailUrl").value(MediaStub.urlOf(101)));
        assertMediaLookedUpOutsideTransaction(get("/storefront/products/related-products/{id}", main.getId()),
            jsonPath("$.productContent[0].thumbnailUrl").value(MediaStub.urlOf(102)));
        assertMediaLookedUpOutsideTransaction(get("/storefront/product/main"),
            jsonPath("$.productImageMediaUrls[0]").value(MediaStub.urlOf(201)));
        assertMediaLookedUpOutsideTransaction(get("/storefront/product-variations/{id}", main.getId()),
            jsonPath("$[0].thumbnail.url").value(MediaStub.urlOf(103)));
        assertMediaLookedUpOutsideTransaction(get("/storefront/categories"),
            jsonPath("$[0].categoryImage.url").value(MediaStub.urlOf(301)));
        assertMediaLookedUpOutsideTransaction(get("/backoffice/categories/{id}", phones.getId()).with(admin()),
            jsonPath("$.categoryImage.url").value(MediaStub.urlOf(301)));
        assertMediaLookedUpOutsideTransaction(get("/backoffice/products/{id}", main.getId()).with(admin()),
            jsonPath("$.productImageMedias[0].url").value(MediaStub.urlOf(201)));
        assertMediaLookedUpOutsideTransaction(get("/backoffice/product-variations/{id}", main.getId()).with(admin()),
            jsonPath("$[0].productImages[0].url").value(MediaStub.urlOf(203)));
    }
}
