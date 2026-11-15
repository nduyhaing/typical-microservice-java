package com.shopapp.order.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.shopapp.order.model.CheckoutItem;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Discount and tax rules of a priced checkout (decisions.md 3c-43, 4-51, 4-52). */
class OrderPricingTest {

    private static long nextId = 1;

    private static CheckoutItem item(long productId, String price, int quantity, Long taxClassId) {
        return CheckoutItem.builder().id(nextId++).productId(productId).productPrice(new BigDecimal(price))
            .quantity(quantity).taxClassId(taxClassId).build();
    }

    private static final Map<Long, BigDecimal> TEN_PERCENT_FOR_CLASS_1 = Map.of(1L, BigDecimal.TEN);

    private static BigDecimal discountOf(OrderPricing pricing, long productId) {
        return pricing.lines().stream().filter(line -> line.item().getProductId() == productId).findFirst()
            .orElseThrow().discountAmount();
    }

    @Test
    void withoutCoupon_noDiscount() {
        OrderPricing pricing = OrderPricing.of(List.of(item(25, "819.00", 2, 1L)), TEN_PERCENT_FOR_CLASS_1, null);

        assertThat(pricing.discount()).isEqualByComparingTo("0");
        assertThat(pricing.totalTax()).isEqualByComparingTo("163.80");
        assertThat(pricing.total()).isEqualByComparingTo("1801.80");
        assertThat(pricing.couponCode()).isNull();
    }

    @Test
    void percentage_roundedHalfUpPerEligibleLine_taxOnTheDiscountedAmount() {
        // 0.25 x 10 % = 0.025 -> 0.03 (HALF_UP); line 2 is not eligible
        OrderPricing pricing = OrderPricing.of(List.of(item(28, "0.25", 1, 1L), item(26, "19.00", 1, 1L)),
            TEN_PERCENT_FOR_CLASS_1, new Coupon("P10", Coupon.Type.PERCENTAGE, BigDecimal.TEN, List.of(28L)));

        assertThat(discountOf(pricing, 28)).isEqualByComparingTo("0.03");
        assertThat(discountOf(pricing, 26)).isEqualByComparingTo("0");
        // tax: (0.25 - 0.03) x 10 % = 0.022 -> 0.02; 19.00 x 10 % = 1.90
        assertThat(pricing.totalTax()).isEqualByComparingTo("1.92");
        assertThat(pricing.subtotal()).isEqualByComparingTo("19.25");
        assertThat(pricing.discount()).isEqualByComparingTo("0.03");
        assertThat(pricing.total()).isEqualByComparingTo("21.14");
        assertThat(pricing.couponCode()).isEqualTo("P10");
    }

    @Test
    void fixed_distributedProportionally_remainderOnTheLastEligibleLine() {
        OrderPricing pricing = OrderPricing.of(
            List.of(item(25, "819.00", 2, 1L), item(26, "19.00", 1, 2L), item(27, "10.00", 1, null)),
            TEN_PERCENT_FOR_CLASS_1, new Coupon("TEN", Coupon.Type.FIXED, BigDecimal.TEN, List.of(25L, 26L, 27L)));

        assertThat(discountOf(pricing, 25)).isEqualByComparingTo("9.83");
        assertThat(discountOf(pricing, 26)).isEqualByComparingTo("0.11");
        assertThat(discountOf(pricing, 27)).isEqualByComparingTo("0.06");
        assertThat(pricing.discount()).isEqualByComparingTo("10.00");
        assertThat(pricing.totalTax()).isEqualByComparingTo("162.82");
        assertThat(pricing.total()).isEqualByComparingTo("1819.82");
    }

    @Test
    void fixed_cappedAtTheEligibleSubtotal() {
        OrderPricing pricing = OrderPricing.of(List.of(item(25, "819.00", 1, 1L), item(27, "10.00", 1, null)),
            TEN_PERCENT_FOR_CLASS_1, new Coupon("BIG", Coupon.Type.FIXED, new BigDecimal("50"), List.of(27L)));

        assertThat(discountOf(pricing, 27)).isEqualByComparingTo("10.00");
        assertThat(discountOf(pricing, 25)).isEqualByComparingTo("0");
        assertThat(pricing.discount()).isEqualByComparingTo("10.00");
        assertThat(pricing.total()).isEqualByComparingTo("900.90");
    }

    @Test
    void fixed_neverMakesALineNegative_whenRoundingWouldPushTheRemainderBelowZero() {
        // three 0.01 lines and a 0.02 discount: shares 0.0067 -> 0.01 each would exceed the cap; no line < 0
        OrderPricing pricing = OrderPricing.of(
            List.of(item(1, "0.01", 1, null), item(2, "0.01", 1, null), item(3, "0.01", 1, null)), Map.of(),
            new Coupon("TINY", Coupon.Type.FIXED, new BigDecimal("0.02"), List.of(1L, 2L, 3L)));

        assertThat(pricing.discount()).isEqualByComparingTo("0.02");
        assertThat(pricing.lines()).allSatisfy(line -> {
            assertThat(line.discountAmount()).isBetween(BigDecimal.ZERO, line.subtotal());
        });
        assertThat(pricing.total()).isEqualByComparingTo("0.01");
    }

    @Test
    void percentageOfAHundred_makesEligibleLinesFree_withoutTax() {
        OrderPricing pricing = OrderPricing.of(List.of(item(25, "819.00", 1, 1L)), TEN_PERCENT_FOR_CLASS_1,
            new Coupon("FREE", Coupon.Type.PERCENTAGE, new BigDecimal("100"), List.of(25L)));

        assertThat(pricing.discount()).isEqualByComparingTo("819.00");
        assertThat(pricing.totalTax()).isEqualByComparingTo("0");
        assertThat(pricing.total()).isEqualByComparingTo("0");
    }
}
