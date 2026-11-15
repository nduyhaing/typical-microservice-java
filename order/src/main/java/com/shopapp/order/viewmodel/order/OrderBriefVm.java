package com.shopapp.order.viewmodel.order;

import com.shopapp.order.model.Order;
import com.shopapp.order.model.enumeration.DeliveryMethod;
import com.shopapp.order.model.enumeration.DeliveryStatus;
import com.shopapp.order.model.enumeration.OrderStatus;
import com.shopapp.order.model.enumeration.PaymentStatus;
import java.math.BigDecimal;
import java.time.ZonedDateTime;

/** A row of the back-office order list (YAS OrderBriefVm). */
public record OrderBriefVm(
    Long id,
    String email,
    OrderVm.AddressVm billingAddressVm,
    BigDecimal totalPrice,
    OrderStatus orderStatus,
    DeliveryMethod deliveryMethod,
    DeliveryStatus deliveryStatus,
    PaymentStatus paymentStatus,
    ZonedDateTime createdOn
) {

    public static OrderBriefVm fromModel(Order order) {
        return new OrderBriefVm(order.getId(), order.getEmail(),
            OrderVm.AddressVm.fromModel(order.getBillingAddressId()), order.getTotalPrice(), order.getOrderStatus(),
            order.getDeliveryMethod(), order.getDeliveryStatus(), order.getPaymentStatus(), order.getCreatedOn());
    }
}
