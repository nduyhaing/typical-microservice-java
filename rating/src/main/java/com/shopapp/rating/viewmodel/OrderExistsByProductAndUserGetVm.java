package com.shopapp.rating.viewmodel;

/** Order GET /storefront/orders/completed?productId=: whether the caller has an order with the product. */
public record OrderExistsByProductAndUserGetVm(boolean isPresent) {
}
