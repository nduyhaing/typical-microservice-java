package com.shopapp.rating.service;

import com.shopapp.commonlibrary.utils.AuthenticationUtils;
import com.shopapp.rating.config.RatingServiceUrls;
import com.shopapp.rating.viewmodel.OrderExistsByProductAndUserGetVm;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * The order service, called with the customer's own token (YAS OrderService). YAS falls back to "not bought" when
 * order is down; here that is a 500, so the customer sees "try again later" rather than "you did not buy it"
 * (decisions.md 4-35).
 */
@Service
public class OrderService {

    private final RestClient restClient;

    public OrderService(RatingServiceUrls urls) {
        this.restClient = RemoteCalls.restClient(urls.order());
    }

    /** Whether the caller has an order containing the product or one of its variations. */
    public boolean hasBought(Long productId) {
        OrderExistsByProductAndUserGetVm result = RemoteCalls.call("Order", () -> restClient.get()
                .uri(uri -> uri.path("/storefront/orders/completed").queryParam("productId", productId).build())
                .headers(headers -> headers.setBearerAuth(AuthenticationUtils.extractJwt()))
                .retrieve()
                .body(OrderExistsByProductAndUserGetVm.class),
            null);
        return result != null && result.isPresent();
    }
}
