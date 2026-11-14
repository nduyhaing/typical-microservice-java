package com.shopapp.order.viewmodel.checkout;

import com.shopapp.order.model.CheckoutItem;
import java.math.BigDecimal;

public record CheckoutItemVm(Long id, Long productId, String productName, int quantity, BigDecimal productPrice) {

    public static CheckoutItemVm fromModel(CheckoutItem item) {
        return new CheckoutItemVm(item.getId(), item.getProductId(), item.getProductName(), item.getQuantity(),
            item.getProductPrice());
    }
}
