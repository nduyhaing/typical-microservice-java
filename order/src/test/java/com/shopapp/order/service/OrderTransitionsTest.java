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
import static org.assertj.core.api.Assertions.assertThat;

import com.shopapp.order.model.enumeration.OrderStatus;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** The order status machine (decisions.md 5-1): every one of the 81 (from, to) pairs. */
class OrderTransitionsTest {

    /** The expected table: from -> the statuses an admin may move it to. */
    private static final Map<OrderStatus, Set<OrderStatus>> EXPECTED = Map.of(
        PENDING, EnumSet.of(PENDING_PAYMENT, ACCEPTED, PAID, CANCELLED, REJECT),
        PENDING_PAYMENT, EnumSet.of(ACCEPTED, PAID, CANCELLED, REJECT),
        ACCEPTED, EnumSet.of(PAID, SHIPPING, CANCELLED, REJECT),
        PAID, EnumSet.of(SHIPPING, REFUND),
        SHIPPING, EnumSet.of(COMPLETED, REFUND),
        COMPLETED, EnumSet.of(REFUND),
        REFUND, EnumSet.noneOf(OrderStatus.class),
        CANCELLED, EnumSet.noneOf(OrderStatus.class),
        REJECT, EnumSet.noneOf(OrderStatus.class));

    static Stream<Arguments> everyPair() {
        return Stream.of(OrderStatus.values())
            .flatMap(from -> Stream.of(OrderStatus.values()).map(to -> Arguments.of(from, to)));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("everyPair")
    void transitionTable(OrderStatus from, OrderStatus to) {
        assertThat(OrderTransitions.isAllowed(from, to)).isEqualTo(EXPECTED.get(from).contains(to));
    }

    @Test
    void allowedFrom_listsTheTable() {
        EXPECTED.forEach((from, targets) -> assertThat(OrderTransitions.allowedFrom(from)).isEqualTo(targets));
    }

    @Test
    void customerMayCancel_onlyBeforePaymentOrAcceptance() {
        assertThat(EnumSet.allOf(OrderStatus.class).stream().filter(OrderTransitions::customerMayCancel))
            .containsExactlyInAnyOrder(PENDING, PENDING_PAYMENT);
    }

    @Test
    void goodsComeBack_whenAnUnshippedOrderIsCancelledRejectedOrRefunded() {
        for (OrderStatus from : OrderStatus.values()) {
            for (OrderStatus to : OrderStatus.values()) {
                boolean expected = EnumSet.of(CANCELLED, REJECT, REFUND).contains(to)
                    && EnumSet.of(PENDING, PENDING_PAYMENT, ACCEPTED, PAID).contains(from)
                    && OrderTransitions.isAllowed(from, to);
                assertThat(OrderTransitions.givesGoodsBack(from, to)).as(from + " -> " + to).isEqualTo(expected);
            }
        }
    }

    @Test
    void boughtStatuses_arePaidShippingCompleted() {
        assertThat(OrderTransitions.BOUGHT_STATUSES).containsExactlyInAnyOrder(PAID, SHIPPING, COMPLETED);
    }

    @Test
    void ended_areTheTerminalStatuses() {
        assertThat(EnumSet.allOf(OrderStatus.class).stream().filter(OrderTransitions::isEnded))
            .containsExactlyInAnyOrder(CANCELLED, REJECT, REFUND);
    }
}
