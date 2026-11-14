package com.shopapp.order.viewmodel.external;

/** The fields the order service uses from product GET /products (YAS ProductCheckoutListVm). */
public record ProductCheckoutListVm(Long id, String name, Double price) {
}
