package com.shopapp.order.viewmodel.external;

/** Body item of cart POST /storefront/cart/items/remove (YAS CartItemDeleteVm). */
public record CartItemDeleteVm(Long productId, Integer quantity) {
}
