package com.shopapp.order.viewmodel.external;

/** Body item of product PUT /backoffice/products/subtract-quantity (decisions.md 3c-29). */
public record ProductQuantityVm(Long productId, Long quantity) {
}
