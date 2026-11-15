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
import com.shopapp.order.viewmodel.external.ProductQuantityVm;
import com.shopapp.order.viewmodel.order.OrderAddressPostVm;
import com.shopapp.order.viewmodel.order.OrderPostVm;
import com.shopapp.order.viewmodel.order.OrderPreviewPostVm;
import com.shopapp.order.viewmodel.order.OrderPreviewVm;
import com.shopapp.order.viewmodel.order.OrderVm;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Orders (adapted from YAS OrderService). An order is built from the customer's pending checkout — its items and
 * prices — never from client totals (decisions.md 2c-1). Slice 3c: shipped to a saved or typed address with location
 * ids, taxed per line from the tax service, stock taken from product before the order is committed and given back
 * when the commit fails (3c-41..45). Status PENDING_PAYMENT; no discount, delivery fee or payment yet.
 *
 * <p>Place order runs in two phases. Outside any transaction: check the checkout, resolve the address (location),
 * get the tax rates (tax) and price it. Then one transaction: lock the checkout row, check it is still pending, take
 * the stock (product), save the order and complete the checkout. A concurrent second "Place order" for the same
 * checkout waits on the row lock and then finds it completed, so it never takes stock; if the transaction fails
 * after the stock was (or may have been) taken, the stock is given back under the same reference.
 */
@Service
@RequiredArgsConstructor
public class OrderService {

    private final CheckoutRepository checkoutRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CartService cartService;
    private final ProductService productService;
    private final TaxService taxService;
    private final LocationService locationService;
    private final TransactionTemplate transactionTemplate;

    /** The server's price of a checkout shipped to an address; takes no stock and saves nothing (3c-46). */
    public OrderPreviewVm previewOrder(OrderPreviewPostVm previewPostVm) {
        Checkout checkout = pendingOwnCheckout(previewPostVm.checkoutId());
        OrderAddress shipping = shippingAddress(previewPostVm.shippingAddressId(),
            previewPostVm.shippingAddressPostVm());
        return OrderPreviewVm.of(checkout.getId(), price(checkout.getCheckoutItems(), shipping));
    }

    public OrderVm createOrder(OrderPostVm orderPostVm) {
        Checkout checkout = pendingOwnCheckout(orderPostVm.checkoutId());
        OrderAddress shipping = shippingAddress(orderPostVm.shippingAddressId(), orderPostVm.shippingAddressPostVm());
        OrderAddressPostVm billingPostVm = orderPostVm.billingAddressPostVm();
        OrderAddress billing = billingPostVm == null ? copyOf(shipping)
            : locationService.resolveTypedAddress(billingPostVm);
        OrderPricing pricing = price(checkout.getCheckoutItems(), shipping);

        StockReservation reservation = new StockReservation();
        OrderVm order;
        try {
            order = transactionTemplate.execute(status ->
                placeOrderInTransaction(orderPostVm, shipping, billing, pricing, reservation));
        } catch (RuntimeException ex) {
            if (reservation.mayHoldStock()) {
                // compensation (3c-45): the order was not saved, give the units back
                productService.restoreStock(reservation);
            }
            if (ex instanceof DataIntegrityViolationException) {
                // the unique index on order.checkout_id caught a concurrent second order (decisions.md 2c-8)
                throw new BadRequestException(Constants.ErrorCode.CHECKOUT_ALREADY_ORDERED, orderPostVm.checkoutId());
            }
            throw ex;
        }
        // after the order is committed: the cart is cleaned up best effort (decisions.md 2c-5)
        cartService.removeOrderedItems(checkout.getCheckoutItems().stream()
            .map(item -> new CartItemDeleteVm(item.getProductId(), item.getQuantity()))
            .toList());
        return order;
    }

    private OrderVm placeOrderInTransaction(OrderPostVm orderPostVm, OrderAddress shipping, OrderAddress billing,
                                            OrderPricing pricing, StockReservation reservation) {
        // row lock: a concurrent call for the same checkout waits here, then sees COMPLETED and takes no stock
        Checkout checkout = checkoutRepository.findWithLockById(orderPostVm.checkoutId())
            .orElseThrow(() -> new NotFoundException(Constants.ErrorCode.CHECKOUT_NOT_FOUND,
                orderPostVm.checkoutId()));
        CheckoutService.requireOwner(checkout);
        if (checkout.getCheckoutState() != CheckoutState.PENDING) {
            throw new BadRequestException(Constants.ErrorCode.CHECKOUT_ALREADY_ORDERED, checkout.getId());
        }
        productService.subtractStock(reservation, stockQuantities(checkout.getCheckoutItems()),
            checkout.getCheckoutItems().stream().collect(Collectors.toMap(CheckoutItem::getProductId,
                CheckoutItem::getProductName, (first, second) -> first)));
        return saveOrder(checkout, orderPostVm, shipping, billing, pricing);
    }

    private OrderVm saveOrder(Checkout checkout, OrderPostVm orderPostVm, OrderAddress shipping, OrderAddress billing,
                              OrderPricing pricing) {
        List<CheckoutItem> checkoutItems = checkout.getCheckoutItems();
        Order order = orderRepository.save(Order.builder()
            .customerId(checkout.getCustomerId())
            .email(checkout.getEmail())
            .checkoutId(checkout.getId())
            .note(orderPostVm.note() != null ? orderPostVm.note() : checkout.getNote())
            .shippingAddressId(shipping)
            .billingAddressId(billing)
            .numberItem(checkoutItems.stream().mapToInt(CheckoutItem::getQuantity).sum())
            .totalPrice(pricing.total())
            .tax(pricing.totalTax())
            .discount(pricing.discount())
            .deliveryFee(BigDecimal.ZERO)
            .orderStatus(OrderStatus.PENDING_PAYMENT)
            .paymentStatus(PaymentStatus.PENDING)
            .paymentMethodId(orderPostVm.paymentMethod().name())
            .deliveryStatus(DeliveryStatus.PREPARING)
            .build());
        Map<Long, OrderPricing.Line> linesByItem = pricing.lines().stream()
            .collect(Collectors.toMap(line -> line.item().getId(), Function.identity()));
        List<OrderItem> items = orderItemRepository.saveAll(checkoutItems.stream()
            .map(item -> {
                OrderPricing.Line line = Objects.requireNonNull(linesByItem.get(item.getId()));
                return OrderItem.builder()
                    .orderId(order.getId())
                    .productId(item.getProductId())
                    .productName(item.getProductName())
                    .quantity(item.getQuantity())
                    .productPrice(item.getProductPrice())
                    .discountAmount(BigDecimal.ZERO)
                    .taxAmount(line.taxAmount())
                    .taxPercent(line.taxPercent())
                    .shipmentFee(BigDecimal.ZERO)
                    .shipmentTax(BigDecimal.ZERO)
                    .build();
            })
            .toList());
        checkout.setCheckoutState(CheckoutState.COMPLETED);
        return OrderVm.fromModel(order, items);
    }

    /** The caller's pending checkout with its items (403 someone else's, 400 already ordered, 404 unknown). */
    private Checkout pendingOwnCheckout(String checkoutId) {
        Checkout checkout = checkoutRepository.findWithItemsById(checkoutId)
            .orElseThrow(() -> new NotFoundException(Constants.ErrorCode.CHECKOUT_NOT_FOUND, checkoutId));
        CheckoutService.requireOwner(checkout);
        if (checkout.getCheckoutState() != CheckoutState.PENDING) {
            throw new BadRequestException(Constants.ErrorCode.CHECKOUT_ALREADY_ORDERED, checkoutId);
        }
        return checkout;
    }

    /** Exactly one of a saved address id and a typed address (3c-41). */
    private OrderAddress shippingAddress(Long savedAddressId, OrderAddressPostVm typed) {
        if ((savedAddressId == null) == (typed == null)) {
            throw new BadRequestException(Constants.ErrorCode.ADDRESS_CHOICE_REQUIRED);
        }
        return savedAddressId != null ? locationService.getSavedAddress(savedAddressId)
            : locationService.resolveTypedAddress(typed);
    }

    /** One tax call with the distinct tax classes of the lines (3c-44). */
    private OrderPricing price(List<CheckoutItem> items, OrderAddress shipping) {
        List<Long> taxClassIds = items.stream().map(CheckoutItem::getTaxClassId).filter(Objects::nonNull)
            .distinct().toList();
        Map<Long, BigDecimal> rates = taxService.getRates(taxClassIds, shipping.getCountryId(),
            shipping.getStateOrProvinceId(), shipping.getZipCode());
        return OrderPricing.of(items, rates);
    }

    /** One entry per product (a product on two checkout lines is merged). */
    private static List<ProductQuantityVm> stockQuantities(List<CheckoutItem> items) {
        Map<Long, Long> quantities = new LinkedHashMap<>();
        items.forEach(item -> quantities.merge(item.getProductId(), (long) item.getQuantity(), Long::sum));
        return quantities.entrySet().stream()
            .map(entry -> new ProductQuantityVm(entry.getKey(), entry.getValue()))
            .toList();
    }

    private static OrderAddress copyOf(OrderAddress address) {
        return OrderAddress.builder()
            .contactName(address.getContactName()).phone(address.getPhone())
            .addressLine1(address.getAddressLine1()).addressLine2(address.getAddressLine2())
            .city(address.getCity()).zipCode(address.getZipCode())
            .districtId(address.getDistrictId()).districtName(address.getDistrictName())
            .stateOrProvinceId(address.getStateOrProvinceId()).stateOrProvinceName(address.getStateOrProvinceName())
            .countryId(address.getCountryId()).countryName(address.getCountryName())
            .build();
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
