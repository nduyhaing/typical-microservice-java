package com.shopapp.order.viewmodel.checkout;

import com.shopapp.order.model.Checkout;
import java.math.BigDecimal;
import java.util.List;

public record CheckoutVm(
    String id,
    String email,
    String note,
    String checkoutState,
    BigDecimal totalAmount,
    List<CheckoutItemVm> checkoutItemVms
) {

    public static CheckoutVm fromModel(Checkout checkout) {
        return new CheckoutVm(checkout.getId(), checkout.getEmail(), checkout.getNote(),
            checkout.getCheckoutState().name(), checkout.getTotalAmount(),
            checkout.getCheckoutItems().stream().map(CheckoutItemVm::fromModel).toList());
    }
}
