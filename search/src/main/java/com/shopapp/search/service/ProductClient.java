package com.shopapp.search.service;

import com.shopapp.search.config.SearchServiceUrls;
import com.shopapp.search.viewmodel.ProductEsDetailVm;
import com.shopapp.search.viewmodel.ProductIdPageVm;
import java.time.Duration;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** The product service (2 s connect, 5 s read). */
@Service
public class ProductClient {

    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    private final RestClient restClient;

    public ProductClient(SearchServiceUrls urls) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        this.restClient = RestClient.builder().baseUrl(urls.product()).requestFactory(requestFactory).build();
    }

    /**
     * The product as search indexes it; empty when product answers 404 (unknown, deleted, unpublished or not visible
     * individually). Any other failure throws {@link ProductUnavailableException} so the event is retried.
     */
    public Optional<ProductEsDetailVm> getEsDetail(long id) {
        try {
            return Optional.ofNullable(restClient.get()
                .uri("/storefront/products-es/{id}", id)
                .retrieve()
                .body(ProductEsDetailVm.class));
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
                return Optional.empty();
            }
            throw new ProductUnavailableException("Product refused products-es/" + id + ": " + ex.getStatusCode(), ex);
        } catch (RestClientException ex) {
            throw new ProductUnavailableException("Product unavailable for products-es/" + id + ": "
                + ex.getMessage(), ex);
        }
    }

    /** One page of the back office product list (ids only matter), with the admin's token. */
    public ProductIdPageVm listProducts(int pageNo, int pageSize, String bearerToken) {
        try {
            return restClient.get()
                .uri(uri -> uri.path("/backoffice/products")
                    .queryParam("pageNo", pageNo)
                    .queryParam("pageSize", pageSize)
                    .build())
                .headers(headers -> headers.setBearerAuth(bearerToken))
                .retrieve()
                .body(ProductIdPageVm.class);
        } catch (RestClientException ex) {
            throw new ProductUnavailableException("Product list unavailable: " + ex.getMessage(), ex);
        }
    }
}
