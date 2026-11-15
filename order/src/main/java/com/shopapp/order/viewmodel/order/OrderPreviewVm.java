package com.shopapp.order.viewmodel.order;

import com.shopapp.order.service.OrderPricing;
import java.math.BigDecimal;
import java.util.List;

/**
 * What an order for this checkout and address would cost: the storefront shows these numbers and never computes
 * tax itself (decisions.md 3c-46). Placing the order prices it again the same way.
 */
public record OrderPreviewVm(
    String checkoutId,
    BigDecimal subtotal,
    BigDecimal totalTax,
    BigDecimal discount,
    BigDecimal deliveryFee,
    BigDecimal total,
    String couponCode,
    List<ItemVm> items
) {

    public record ItemVm(Long productId, String productName, int quantity, BigDecimal productPrice,
                         BigDecimal subtotal, BigDecimal discountAmount, BigDecimal taxPercent,
                         BigDecimal taxAmount) {
    }

    public static OrderPreviewVm of(String checkoutId, OrderPricing pricing) {
        return new OrderPreviewVm(checkoutId, pricing.subtotal(), pricing.totalTax(), pricing.discount(),
            BigDecimal.ZERO, pricing.total(), pricing.couponCode(), pricing.lines().stream()
            .map(line -> new ItemVm(line.item().getProductId(), line.item().getProductName(),
                line.item().getQuantity(), line.item().getProductPrice(), line.subtotal(), line.discountAmount(),
                line.taxPercent(),
                line.taxAmount()))
            .toList());
    }
}
