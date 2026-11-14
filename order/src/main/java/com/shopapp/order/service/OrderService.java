package com.shopapp.order.service;

import com.shopapp.commonlibrary.exception.BadRequestException;
import com.shopapp.commonlibrary.exception.ForbiddenException;
import com.shopapp.commonlibrary.exception.NotFoundException;
import com.shopapp.commonlibrary.utils.AuthenticationUtils;
import com.shopapp.order.model.Checkout;
import com.shopapp.order.model.CheckoutItem;
import com.shopapp.order.model.Order;
import com.shopapp.order.model.OrderAddress;
import com.shopapp.order.model.OrderItem;
import com.shopapp.order.model.enumeration.CheckoutState;
import com.shopapp.order.model.enumeration.DeliveryStatus;
import com.shopapp.order.model.enumeration.OrderStatus;
import com.shopapp.order.model.enumeration.PaymentStatus;
import com.shopapp.order.repository.CheckoutRepository;
import com.shopapp.order.repository.OrderItemRepository;
import com.shopapp.order.repository.OrderRepository;
import com.shopapp.order.utils.Constants;
import com.shopapp.order.viewmodel.external.CartItemDeleteVm;
import com.shopapp.order.viewmodel.order.OrderAddressPostVm;
import com.shopapp.order.viewmodel.order.OrderPostVm;
import com.shopapp.order.viewmodel.order.OrderVm;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Orders (adapted from YAS OrderService). An order is built from the customer's pending checkout — its items and
 * prices — never from client totals (decisions.md 2c-1). Slice 2c: status PENDING_PAYMENT, no tax, discount,
 * delivery fee or payment (later slices).
 */
@Service
@RequiredArgsConstructor
public class OrderService {

    private final CheckoutRepository checkoutRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CartService cartService;
    private final TransactionTemplate transactionTemplate;

    public OrderVm createOrder(OrderPostVm orderPostVm) {
        Checkout[] ordered = new Checkout[1];
        OrderVm order;
        try {
            order = transactionTemplate.execute(status -> placeOrderInTransaction(orderPostVm, ordered));
        } catch (DataIntegrityViolationException ex) {
            // the unique index on order.checkout_id caught a concurrent second order (decisions.md 2c-8)
            throw new BadRequestException(Constants.ErrorCode.CHECKOUT_ALREADY_ORDERED, orderPostVm.checkoutId());
        }
        // after the order is committed: the cart is cleaned up best effort (decisions.md 2c-5)
        cartService.removeOrderedItems(ordered[0].getCheckoutItems().stream()
            .map(item -> new CartItemDeleteVm(item.getProductId(), item.getQuantity()))
            .toList());
        return order;
    }

    private OrderVm placeOrderInTransaction(OrderPostVm orderPostVm, Checkout[] ordered) {
        // row lock: a concurrent call for the same checkout waits here, then sees COMPLETED
        Checkout checkout = checkoutRepository.findWithLockById(orderPostVm.checkoutId())
            .orElseThrow(() -> new NotFoundException(Constants.ErrorCode.CHECKOUT_NOT_FOUND,
                orderPostVm.checkoutId()));
        CheckoutService.requireOwner(checkout);
        if (checkout.getCheckoutState() != CheckoutState.PENDING) {
            throw new BadRequestException(Constants.ErrorCode.CHECKOUT_ALREADY_ORDERED, checkout.getId());
        }
        ordered[0] = checkout;
        return saveOrder(checkout, orderPostVm);
    }

    private OrderVm saveOrder(Checkout checkout, OrderPostVm orderPostVm) {
        OrderAddress shipping = orderPostVm.shippingAddressPostVm().toModel();
        OrderAddressPostVm billingPostVm = orderPostVm.billingAddressPostVm();
        OrderAddress billing = billingPostVm == null ? orderPostVm.shippingAddressPostVm().toModel()
            : billingPostVm.toModel();
        List<CheckoutItem> checkoutItems = checkout.getCheckoutItems();
        Order order = orderRepository.save(Order.builder()
            .customerId(checkout.getCustomerId())
            .email(checkout.getEmail())
            .checkoutId(checkout.getId())
            .note(orderPostVm.note() != null ? orderPostVm.note() : checkout.getNote())
            .shippingAddressId(shipping)
            .billingAddressId(billing)
            .numberItem(checkoutItems.stream().mapToInt(CheckoutItem::getQuantity).sum())
            .totalPrice(checkout.getTotalAmount())
            .tax(0)
            .discount(0)
            .deliveryFee(BigDecimal.ZERO)
            .orderStatus(OrderStatus.PENDING_PAYMENT)
            .paymentStatus(PaymentStatus.PENDING)
            .paymentMethodId(orderPostVm.paymentMethod().name())
            .deliveryStatus(DeliveryStatus.PREPARING)
            .build());
        List<OrderItem> items = orderItemRepository.saveAll(checkoutItems.stream()
            .map(item -> OrderItem.builder()
                .orderId(order.getId())
                .productId(item.getProductId())
                .productName(item.getProductName())
                .quantity(item.getQuantity())
                .productPrice(item.getProductPrice())
                .discountAmount(BigDecimal.ZERO)
                .taxAmount(BigDecimal.ZERO)
                .taxPercent(BigDecimal.ZERO)
                .shipmentFee(BigDecimal.ZERO)
                .shipmentTax(BigDecimal.ZERO)
                .build())
            .toList());
        checkout.setCheckoutState(CheckoutState.COMPLETED);
        return OrderVm.fromModel(order, items);
    }

    @Transactional(readOnly = true)
    public List<OrderVm> getMyOrders() {
        List<Order> orders = orderRepository.findAllByCustomerIdOrderByCreatedOnDescIdDesc(
            AuthenticationUtils.extractUserId());
        Map<Long, List<OrderItem>> itemsByOrder = orderItemRepository
            .findAllByOrderIdIn(orders.stream().map(Order::getId).toList()).stream()
            .collect(Collectors.groupingBy(OrderItem::getOrderId));
        return orders.stream()
            .map(order -> OrderVm.fromModel(order, itemsByOrder.getOrDefault(order.getId(), List.of())))
            .toList();
    }

    @Transactional(readOnly = true)
    public OrderVm getOrderByCheckoutId(String checkoutId) {
        Order order = orderRepository.findByCheckoutId(checkoutId)
            .orElseThrow(() -> new NotFoundException(Constants.ErrorCode.ORDER_NOT_FOUND, checkoutId));
        if (!AuthenticationUtils.extractUserId().equals(order.getCustomerId())) {
            throw new ForbiddenException(Constants.ErrorCode.ORDER_NOT_YOURS);
        }
        return OrderVm.fromModel(order, orderItemRepository.findAllByOrderId(order.getId()));
    }
}
