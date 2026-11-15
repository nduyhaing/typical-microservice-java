package com.shopapp.promotion.service;

import com.shopapp.commonlibrary.config.ServiceUrlConfig;
import com.shopapp.commonlibrary.exception.InternalServerErrorException;
import com.shopapp.commonlibrary.observability.ObservedRestClient;
import com.shopapp.promotion.viewmodel.product.ProductTargetVm;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * The product service as promotion needs it to decide which products a promotion applies to. YAS called ADMIN-only
 * {@code /backoffice/products/by-brands|by-categories|by-ids} with the customer's token (always 403); this makes one
 * call per check to product's public {@code GET /storefront/products/promotion-targets?ids=} (orderable products with
 * parent, brand and category ids and price; decisions.md 4-42, 4-74). 2 s connect, 5 s read; any failure is a 500
 * "Product service is unavailable".
 */
@Slf4j
@Service
public class ProductService {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);
    private static final ParameterizedTypeReference<List<ProductTargetVm>> TARGET_LIST =
        new ParameterizedTypeReference<>() { };

    private final RestClient restClient;

    public ProductService(ServiceUrlConfig urls) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        this.restClient = ObservedRestClient.builder().baseUrl(urls.product()).requestFactory(requestFactory).build();
    }

    /** The requested products that can be ordered (unknown, unpublished or not orderable ids are left out). */
    public List<ProductTargetVm> getOrderableProducts(Collection<Long> productIds) {
        try {
            List<ProductTargetVm> products = restClient.get()
                .uri(uri -> uri.path("/storefront/products/promotion-targets")
                    .queryParam("ids", productIds.stream().map(String::valueOf).collect(Collectors.joining(",")))
                    .build())
                .retrieve()
                .body(TARGET_LIST);
            return products == null ? List.of() : products;
        } catch (RestClientException ex) {
            log.error("Product service call failed: {}", ex.getMessage());
            throw new InternalServerErrorException("Product service is unavailable, please try again later");
        }
    }
}
