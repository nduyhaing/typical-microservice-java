package com.shopapp.order.viewmodel.order;

import com.shopapp.order.model.Order;
import com.shopapp.order.model.OrderItem;
import com.shopapp.order.model.enumeration.DeliveryMethod;
import com.shopapp.order.model.enumeration.DeliveryStatus;
import com.shopapp.order.model.enumeration.OrderStatus;
import com.shopapp.order.model.enumeration.PaymentStatus;
import com.shopapp.order.service.OrderTransitions;
import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

/**
 * An order as the back office shows it (YAS GET /backoffice/orders/{id} answers OrderVm; shop_app adds what an admin
 * needs to act on it, decisions.md 5-1): both addresses, delivery and payment fields, the statuses it may move to
 * ({@code allowedStatuses}, from the one status machine) and whether its stock and coupon use were given back. The
 * stock and coupon references themselves are never shown.
 */
public record OrderDetailVm(
    Long id,
    String email,
    String customerId,
    String checkoutId,
    String note,
    int numberItem,
    BigDecimal subtotal,
    BigDecimal tax,
    BigDecimal discount,
    BigDecimal totalPrice,
    BigDecimal deliveryFee,
    String couponCode,
    OrderStatus orderStatus,
    PaymentStatus paymentStatus,
    String paymentMethod,
    Long paymentId,
    DeliveryStatus deliveryStatus,
    DeliveryMethod deliveryMethod,
    String rejectReason,
    ZonedDateTime createdOn,
    ZonedDateTime lastModifiedOn,
    String lastModifiedBy,
    OrderVm.AddressVm shippingAddressVm,
    OrderVm.AddressVm billingAddressVm,
    List<OrderVm.ItemVm> orderItemVms,
    Set<OrderStatus> allowedStatuses,
    GoodsBack goodsBack
) {

    /**
     * Stock and coupon use of an order that ended before shipping: when it was asked to give them back, whether it
     * has any to give back, and when each was given back ({@code null} while still pending).
     */
    public record GoodsBack(ZonedDateTime requestedOn, boolean stockToRestore, ZonedDateTime stockRestoredOn,
                            boolean couponToRelease, ZonedDateTime couponReleasedOn) {
    }

    public static OrderDetailVm fromModel(Order order, List<OrderItem> items) {
        BigDecimal subtotal = items.stream()
            .map(item -> item.getProductPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        GoodsBack goodsBack = order.getGoodsBackRequestedOn() == null ? null
            : new GoodsBack(order.getGoodsBackRequestedOn(), order.getStockReference() != null,
                order.getStockRestoredOn(), order.getPromotionUsageReference() != null, order.getCouponReleasedOn());
        return new OrderDetailVm(order.getId(), order.getEmail(), order.getCustomerId(), order.getCheckoutId(),
            order.getNote(), order.getNumberItem(), subtotal, order.getTax(), order.getDiscount(),
            order.getTotalPrice(), order.getDeliveryFee(), order.getCouponCode(), order.getOrderStatus(),
            order.getPaymentStatus(), order.getPaymentMethodId(), order.getPaymentId(),
            order.getDeliveryStatus(), order.getDeliveryMethod(), order.getRejectReason(),
            order.getCreatedOn(), order.getLastModifiedOn(), order.getLastModifiedBy(),
            OrderVm.AddressVm.fromModel(order.getShippingAddressId()),
            OrderVm.AddressVm.fromModel(order.getBillingAddressId()),
            items.stream().map(OrderVm.ItemVm::fromModel).toList(),
            OrderTransitions.allowedFrom(order.getOrderStatus()), goodsBack);
    }
}
