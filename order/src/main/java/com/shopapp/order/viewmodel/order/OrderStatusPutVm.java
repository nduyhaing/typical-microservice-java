package com.shopapp.order.viewmodel.order;

import com.shopapp.order.model.enumeration.DeliveryMethod;
import com.shopapp.order.model.enumeration.DeliveryStatus;
import com.shopapp.order.model.enumeration.OrderStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Admin status change (decisions.md 5-1): the new order status (the current status again changes only the delivery
 * fields), optionally the delivery status and method (YAS fields; without a delivery status it follows the order
 * status) and a reject reason.
 */
public record OrderStatusPutVm(
    @NotNull OrderStatus orderStatus,
    DeliveryStatus deliveryStatus,
    DeliveryMethod deliveryMethod,
    @Size(max = 255) String rejectReason
) {
}
