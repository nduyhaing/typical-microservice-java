package com.shopapp.order.service;

import static com.shopapp.order.model.enumeration.OrderStatus.ACCEPTED;
import static com.shopapp.order.model.enumeration.OrderStatus.CANCELLED;
import static com.shopapp.order.model.enumeration.OrderStatus.COMPLETED;
import static com.shopapp.order.model.enumeration.OrderStatus.PAID;
import static com.shopapp.order.model.enumeration.OrderStatus.PENDING;
import static com.shopapp.order.model.enumeration.OrderStatus.PENDING_PAYMENT;
import static com.shopapp.order.model.enumeration.OrderStatus.REFUND;
import static com.shopapp.order.model.enumeration.OrderStatus.REJECT;
import static com.shopapp.order.model.enumeration.OrderStatus.SHIPPING;

import com.shopapp.order.model.enumeration.OrderStatus;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The order status machine (YAS {@link OrderStatus} values; YAS itself enforced no transitions), the one place that
 * decides which status changes are allowed (decisions.md 5-1):
 * <pre>
 *   PENDING         -> PENDING_PAYMENT, ACCEPTED, PAID, CANCELLED, REJECT
 *   PENDING_PAYMENT -> ACCEPTED (cash on delivery accepted), PAID, CANCELLED, REJECT
 *   ACCEPTED        -> PAID, SHIPPING, CANCELLED, REJECT
 *   PAID            -> SHIPPING, REFUND
 *   SHIPPING        -> COMPLETED, REFUND
 *   COMPLETED       -> REFUND
 *   CANCELLED, REJECT, REFUND: final
 * </pre>
 * A paid order is not "cancelled" but refunded. Cancelling, rejecting or refunding an order that has not shipped
 * gives its stock and coupon use back ({@link #givesGoodsBack}).
 */
public final class OrderTransitions {

    /** Statuses in which the customer counts as having bought the order's products (rating, 4-30 narrowed by 5-3). */
    public static final Set<OrderStatus> BOUGHT_STATUSES = Collections.unmodifiableSet(EnumSet.of(PAID, SHIPPING,
        COMPLETED));

    private static final Set<OrderStatus> ENDED = EnumSet.of(CANCELLED, REJECT, REFUND);
    private static final Set<OrderStatus> NOT_SHIPPED = EnumSet.of(PENDING, PENDING_PAYMENT, ACCEPTED, PAID);
    private static final Set<OrderStatus> CUSTOMER_CANCELLABLE = EnumSet.of(PENDING, PENDING_PAYMENT);
    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = new EnumMap<>(OrderStatus.class);

    static {
        ALLOWED.put(PENDING, EnumSet.of(PENDING_PAYMENT, ACCEPTED, PAID, CANCELLED, REJECT));
        ALLOWED.put(PENDING_PAYMENT, EnumSet.of(ACCEPTED, PAID, CANCELLED, REJECT));
        ALLOWED.put(ACCEPTED, EnumSet.of(PAID, SHIPPING, CANCELLED, REJECT));
        ALLOWED.put(PAID, EnumSet.of(SHIPPING, REFUND));
        ALLOWED.put(SHIPPING, EnumSet.of(COMPLETED, REFUND));
        ALLOWED.put(COMPLETED, EnumSet.of(REFUND));
        ENDED.forEach(status -> ALLOWED.put(status, EnumSet.noneOf(OrderStatus.class)));
    }

    private OrderTransitions() {
    }

    public static boolean isAllowed(OrderStatus from, OrderStatus to) {
        return from != null && to != null && ALLOWED.get(from).contains(to);
    }

    /** The statuses an order in {@code from} may move to (a copy). */
    public static Set<OrderStatus> allowedFrom(OrderStatus from) {
        return from == null ? EnumSet.noneOf(OrderStatus.class) : EnumSet.copyOf(ALLOWED.get(from));
    }

    /** A customer may cancel their own order only before it is paid or accepted. */
    public static boolean customerMayCancel(OrderStatus status) {
        return CUSTOMER_CANCELLABLE.contains(status);
    }

    /** Whether this (allowed) change gives the order's stock and coupon use back: an unshipped order ends. */
    public static boolean givesGoodsBack(OrderStatus from, OrderStatus to) {
        return isAllowed(from, to) && ENDED.contains(to) && NOT_SHIPPED.contains(from);
    }

    /** Final statuses: nothing moves out of them. */
    public static boolean isEnded(OrderStatus status) {
        return ENDED.contains(status);
    }
}
