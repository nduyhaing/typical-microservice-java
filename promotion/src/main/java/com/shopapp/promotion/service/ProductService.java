package com.shopapp.promotion.service;

import com.shopapp.commonlibrary.config.ServiceUrlConfig;
import com.shopapp.commonlibrary.exception.InternalServerErrorException;
import com.shopapp.commonlibrary.utils.AuthenticationUtils;
import com.shopapp.promotion.utils.Constants;
import com.shopapp.promotion.viewmodel.product.CategoryProductsPageVm;
import com.shopapp.promotion.viewmodel.product.ProductCheckoutPageVm;
import com.shopapp.promotion.viewmodel.product.ProductCheckoutVm;
import com.shopapp.promotion.viewmodel.product.ProductIdVm;
import com.shopapp.promotion.viewmodel.product.SlugVm;
import java.time.Duration;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * The product service as promotion needs it to decide which products a promotion applies to. YAS called ADMIN-only
 * {@code /backoffice/products/by-brands|by-categories|by-ids} with the customer's token (always 403); this uses
 * endpoints a customer may call (decisions.md 4-42): {@code GET /products?ids=} (orderable products with parent and
 * brand id and price, relayed token) and the public storefront brand / category lists and product lists. 2 s connect,
 * 5 s read; any failure is a 500 "Product service is unavailable".
 */
@Slf4j
@Service
public class ProductService {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);
    private static final int PAGE_SIZE = 100;
    private static final ParameterizedTypeReference<List<SlugVm>> SLUG_LIST = new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<List<ProductIdVm>> ID_LIST =
        new ParameterizedTypeReference<>() { };

    private final RestClient restClient;

    public ProductService(ServiceUrlConfig urls) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        this.restClient = RestClient.builder().baseUrl(urls.product()).requestFactory(requestFactory).build();
    }

    /** The requested products that can be ordered (unknown, unpublished or not orderable ids are left out). */
    public List<ProductCheckoutVm> getOrderableProducts(Collection<Long> productIds) {
        String jwt = AuthenticationUtils.extractJwt();
        ProductCheckoutPageVm page = call(() -> restClient.get()
            .uri(uri -> uri.path("/products")
                .queryParam("ids", productIds.stream().map(String::valueOf).collect(Collectors.joining(",")))
                .queryParam("pageSize", PAGE_SIZE)
                .build())
            .headers(headers -> headers.setBearerAuth(jwt))
            .retrieve()
            .body(ProductCheckoutPageVm.class));
        return page == null || page.productCheckoutListVms() == null ? List.of() : page.productCheckoutListVms();
    }

    /** Ids of the published, individually visible products of these brands (unknown brands contribute none). */
    public Set<Long> getProductIdsOfBrands(Collection<Long> brandIds) {
        Set<Long> ids = new HashSet<>();
        for (String slug : slugs("/storefront/brands", brandIds)) {
            List<ProductIdVm> products = call(() -> restClient.get()
                .uri("/storefront/brand/{slug}/products", slug)
                .retrieve()
                .body(ID_LIST));
            if (products != null) {
                products.stream().map(ProductIdVm::id).filter(Objects::nonNull).forEach(ids::add);
            }
        }
        return ids;
    }

    /**
     * Ids of the published, individually visible products directly in these categories (YAS: no sub-categories), at
     * most {@link Constants#MAX_CATEGORY_PAGES} pages of 100 per category.
     */
    public Set<Long> getProductIdsOfCategories(Collection<Long> categoryIds) {
        Set<Long> ids = new HashSet<>();
        for (String slug : slugs("/storefront/categories", categoryIds)) {
            for (int pageNo = 0; pageNo < Constants.MAX_CATEGORY_PAGES; pageNo++) {
                int current = pageNo;
                CategoryProductsPageVm page = call(() -> restClient.get()
                    .uri(uri -> uri.path("/storefront/category/{slug}/products")
                        .queryParam("pageNo", current)
                        .queryParam("pageSize", PAGE_SIZE)
                        .build(slug))
                    .retrieve()
                    .body(CategoryProductsPageVm.class));
                if (page == null || page.productContent() == null) {
                    break;
                }
                page.productContent().stream().map(ProductIdVm::id).filter(Objects::nonNull).forEach(ids::add);
                if (current + 1 >= page.totalPages()) {
                    break;
                }
            }
        }
        return ids;
    }

    private List<String> slugs(String listPath, Collection<Long> wanted) {
        List<SlugVm> all = call(() -> restClient.get().uri(listPath).retrieve().body(SLUG_LIST));
        if (all == null) {
            return List.of();
        }
        Map<Long, String> byId = all.stream()
            .filter(item -> item.id() != null && item.slug() != null)
            .collect(Collectors.toMap(SlugVm::id, SlugVm::slug, (first, second) -> first));
        return wanted.stream().distinct().map(byId::get).filter(Objects::nonNull).toList();
    }

    private static <T> T call(Supplier<T> request) {
        try {
            return request.get();
        } catch (RestClientException ex) {
            log.error("Product service call failed: {}", ex.getMessage());
            throw new InternalServerErrorException("Product service is unavailable, please try again later");
        }
    }
}
