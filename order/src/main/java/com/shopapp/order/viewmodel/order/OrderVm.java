package com.shopapp.order.viewmodel.order;

import com.shopapp.order.model.Order;
import com.shopapp.order.model.OrderAddress;
import com.shopapp.order.model.OrderItem;
import com.shopapp.order.model.enumeration.DeliveryStatus;
import com.shopapp.order.model.enumeration.OrderStatus;
import com.shopapp.order.model.enumeration.PaymentStatus;
import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * An order as the storefront shows it ("my orders", order confirmation). {@code totalPrice} = subtotal + tax -
 * discount (decisions.md 3c-43).
 */
public record OrderVm(
    Long id,
    String email,
    String checkoutId,
    String note,
    int numberItem,
    BigDecimal subtotal,
    BigDecimal tax,
    BigDecimal discount,
    BigDecimal totalPrice,
    BigDecimal deliveryFee,
    OrderStatus orderStatus,
    PaymentStatus paymentStatus,
    String paymentMethod,
    DeliveryStatus deliveryStatus,
    ZonedDateTime createdOn,
    AddressVm shippingAddressVm,
    List<ItemVm> orderItemVms
) {

    public record ItemVm(Long productId, String productName, int quantity, BigDecimal productPrice,
                         BigDecimal taxPercent, BigDecimal taxAmount) {

        static ItemVm fromModel(OrderItem item) {
            return new ItemVm(item.getProductId(), item.getProductName(), item.getQuantity(), item.getProductPrice(),
                item.getTaxPercent(), item.getTaxAmount());
        }
    }

    public record AddressVm(String contactName, String phone, String addressLine1, String addressLine2, String city,
                            String zipCode, String districtName, String stateOrProvinceName, String countryName) {

        static AddressVm fromModel(OrderAddress address) {
            return address == null ? null : new AddressVm(address.getContactName(), address.getPhone(),
                address.getAddressLine1(), address.getAddressLine2(), address.getCity(), address.getZipCode(),
                address.getDistrictName(), address.getStateOrProvinceName(), address.getCountryName());
        }
    }

    public static OrderVm fromModel(Order order, List<OrderItem> items) {
        BigDecimal subtotal = items.stream()
            .map(item -> item.getProductPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new OrderVm(order.getId(), order.getEmail(), order.getCheckoutId(), order.getNote(),
            order.getNumberItem(), subtotal, order.getTax(), order.getDiscount(), order.getTotalPrice(),
            order.getDeliveryFee(),
            order.getOrderStatus(), order.getPaymentStatus(), order.getPaymentMethodId(), order.getDeliveryStatus(),
            order.getCreatedOn(), AddressVm.fromModel(order.getShippingAddressId()),
            items.stream().map(ItemVm::fromModel).toList());
    }
}
