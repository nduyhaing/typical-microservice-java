package com.shopapp.order.service;

import com.shopapp.commonlibrary.exception.InternalServerErrorException;
import com.shopapp.commonlibrary.utils.AuthenticationUtils;
import com.shopapp.order.config.OrderServiceUrls;
import com.shopapp.order.viewmodel.external.ProductCheckoutListVm;
import com.shopapp.order.viewmodel.external.ProductGetCheckoutListVm;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Prices products at checkout through product GET /products (YAS), relaying the customer's token. Only orderable
 * products come back (decisions.md 2c-2), so a missing id means "cannot be ordered".
 */
@Slf4j
@Service
public class ProductService {

    private final RestClient restClient;

    public ProductService(OrderServiceUrls urls) {
        this.restClient = RestClients.withTimeouts(urls.product());
    }

    public Map<Long, ProductCheckoutListVm> getOrderableProducts(Collection<Long> ids) {
        try {
            ProductGetCheckoutListVm response = restClient.get()
                .uri(uri -> uri.path("/products").queryParam("ids", ids).queryParam("pageSize", ids.size()).build())
                .headers(headers -> headers.setBearerAuth(AuthenticationUtils.extractJwt()))
                .retrieve()
                .body(ProductGetCheckoutListVm.class);
            List<ProductCheckoutListVm> products = response == null || response.productCheckoutListVms() == null
                ? List.of() : response.productCheckoutListVms();
            return products.stream().collect(Collectors.toMap(ProductCheckoutListVm::id, Function.identity()));
        } catch (RestClientException ex) {
            log.error("Product service unavailable: {}", ex.getMessage());
            throw new InternalServerErrorException("Product service is unavailable, please try again later");
        }
    }
}
