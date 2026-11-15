package com.shopapp.rating.service;

import com.shopapp.commonlibrary.exception.NotFoundException;
import com.shopapp.rating.config.RatingServiceUrls;
import com.shopapp.rating.utils.Constants;
import com.shopapp.rating.viewmodel.ProductThumbnailVm;
import java.util.List;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * The product service's public card lookup, for the name stored with a rating: the client's product name is never
 * trusted (YAS stores it as sent, decisions.md 4-31).
 */
@Service
public class ProductService {

    private static final ParameterizedTypeReference<List<ProductThumbnailVm>> PRODUCT_LIST =
        new ParameterizedTypeReference<>() { };

    private final RestClient restClient;

    public ProductService(RatingServiceUrls urls) {
        this.restClient = RemoteCalls.restClient(urls.product());
    }

    /** The name of a product shown in the storefront; unknown or hidden → 404 {@code PRODUCT_NOT_FOUND}. */
    public String getProductName(Long productId) {
        List<ProductThumbnailVm> products = RemoteCalls.call("Product", () -> restClient.get()
                .uri(uri -> uri.path("/storefront/products/list-featured").queryParam("productId", productId).build())
                .retrieve()
                .body(PRODUCT_LIST),
            () -> new NotFoundException(Constants.ErrorCode.PRODUCT_NOT_FOUND, productId));
        return (products == null ? List.<ProductThumbnailVm>of() : products).stream()
            .filter(product -> productId.equals(product.id()))
            .map(ProductThumbnailVm::name)
            .findFirst()
            .orElseThrow(() -> new NotFoundException(Constants.ErrorCode.PRODUCT_NOT_FOUND, productId));
    }
}
