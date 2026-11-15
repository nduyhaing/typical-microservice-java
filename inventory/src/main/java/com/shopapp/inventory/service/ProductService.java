package com.shopapp.inventory.service;

import com.shopapp.commonlibrary.exception.NotFoundException;
import com.shopapp.commonlibrary.utils.AuthenticationUtils;
import com.shopapp.inventory.config.InventoryServiceUrls;
import com.shopapp.inventory.model.enumeration.FilterExistInWhSelection;
import com.shopapp.inventory.utils.Constants;
import com.shopapp.inventory.viewmodel.product.ProductInfoVm;
import com.shopapp.inventory.viewmodel.product.ProductQuantityPostVm;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/** The product service, called with the admin's relayed token (YAS ProductService, without resilience4j). */
@Service
public class ProductService {

    private static final String SERVICE = "Product";
    private static final ParameterizedTypeReference<List<ProductInfoVm>> PRODUCT_LIST =
        new ParameterizedTypeReference<>() { };

    private final RestClient restClient;

    public ProductService(InventoryServiceUrls urls) {
        this.restClient = RemoteCalls.restClient(urls.product());
    }

    /** One product; unknown → 404 {@code PRODUCT_NOT_FOUND}. */
    public ProductInfoVm getProduct(Long id) {
        return RemoteCalls.call(SERVICE, () -> restClient.get()
                .uri("/backoffice/products/{id}", id)
                .headers(headers -> headers.setBearerAuth(AuthenticationUtils.extractJwt()))
                .retrieve()
                .body(ProductInfoVm.class),
            () -> new NotFoundException(Constants.ErrorCode.PRODUCT_NOT_FOUND, id));
    }

    /** Products matching name/sku (empty = any), in / not in {@code productIds} (YAS for-warehouse). */
    public List<ProductInfoVm> filterProducts(String productName, String productSku, Collection<Long> productIds,
                                              FilterExistInWhSelection selection) {
        String ids = productIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        List<ProductInfoVm> products = RemoteCalls.call(SERVICE, () -> restClient.get()
                .uri(uri -> uri.path("/backoffice/products/for-warehouse")
                    .queryParam("name", productName)
                    .queryParam("sku", productSku)
                    .queryParam("productIds", ids)
                    .queryParam("selection", selection.name())
                    .build())
                .headers(headers -> headers.setBearerAuth(AuthenticationUtils.extractJwt()))
                .retrieve()
                .body(PRODUCT_LIST),
            null);
        return products == null ? List.of() : products;
    }

    /** Sets the products' stock quantity (their totals over all warehouses). */
    public void updateProductQuantity(List<ProductQuantityPostVm> quantities) {
        RemoteCalls.call(SERVICE, () -> restClient.put()
                .uri("/backoffice/products/update-quantity")
                .headers(headers -> headers.setBearerAuth(AuthenticationUtils.extractJwt()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(quantities)
                .retrieve()
                .toBodilessEntity(),
            null);
    }
}
