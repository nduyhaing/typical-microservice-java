package com.shopapp.order.service;

import com.shopapp.commonlibrary.utils.AuthenticationUtils;
import com.shopapp.order.config.OrderServiceUrls;
import com.shopapp.order.viewmodel.external.CartItemDeleteVm;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Removes ordered items from the customer's cart (YAS CartService.deleteCartItems), with the customer's token.
 * Best effort: the order already exists, so a cart failure is logged, not raised (decisions.md 2c-5).
 */
@Slf4j
@Service
public class CartService {

    private final RestClient restClient;

    public CartService(OrderServiceUrls urls) {
        this.restClient = RestClients.withTimeouts(urls.cart());
    }

    public void removeOrderedItems(List<CartItemDeleteVm> items) {
        try {
            restClient.post()
                .uri("/storefront/cart/items/remove")
                .headers(headers -> headers.setBearerAuth(AuthenticationUtils.extractJwt()))
                .body(items)
                .retrieve()
                .toBodilessEntity();
        } catch (RestClientException ex) {
            log.warn("Could not remove ordered items from the cart: {}", ex.getMessage());
        }
    }
}
