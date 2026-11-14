package com.shopapp.cart.service;

import com.shopapp.cart.viewmodel.ProductThumbnailVm;
import com.shopapp.commonlibrary.config.ServiceUrlConfig;
import com.shopapp.commonlibrary.exception.InternalServerErrorException;
import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Looks products up in the product service (YAS: GET /storefront/products/list-featured?productId=...).
 * The product service only returns published products, so "exists" means "may be put in a cart".
 * Retry/circuit breaker come with slice 3 (decisions.md 2a-11); timeouts keep a slow product service from
 * hanging cart requests.
 */
@Slf4j
@Service
public class ProductService {

    private static final ParameterizedTypeReference<List<ProductThumbnailVm>> THUMBNAILS =
        new ParameterizedTypeReference<>() {
        };

    private final RestClient restClient;

    public ProductService(ServiceUrlConfig serviceUrlConfig) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(2));
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = RestClient.builder()
            .baseUrl(serviceUrlConfig.product())
            .requestFactory(requestFactory)
            .build();
    }

    public List<ProductThumbnailVm> getProducts(List<Long> ids) {
        try {
            List<ProductThumbnailVm> products = restClient.get()
                .uri(uri -> uri.path("/storefront/products/list-featured").queryParam("productId", ids).build())
                .retrieve()
                .body(THUMBNAILS);
            return products == null ? List.of() : products;
        } catch (RestClientException ex) {
            log.error("Product service unavailable: {}", ex.getMessage());
            throw new InternalServerErrorException("Product service is unavailable, please try again later");
        }
    }

    public boolean existsById(Long id) {
        return getProducts(List.of(id)).stream().anyMatch(product -> product.id() == id);
    }
}
