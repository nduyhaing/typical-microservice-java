package com.shopapp.order.service;

import com.shopapp.order.model.CheckoutItem;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The price of a checkout shipped to an address (decisions.md 3c-43, 4-51, 4-52): per line subtotal = price x
 * quantity; a coupon discounts the eligible lines only (PERCENTAGE: subtotal x percent / 100 per line, FIXED: the
 * amount capped at the eligible subtotal, shared in proportion to the line subtotals with the rounding remainder on
 * the last eligible line); tax = (subtotal - discount) x rate / 100. Every amount is rounded half-up to cents per
 * line and the order's figures are sums of the rounded lines, so the lines always add up. A line without tax class,
 * or whose class has no rate for the address, pays 0 %. total = subtotal - discount + tax; no line and no total is
 * ever negative.
 */
public record OrderPricing(List<Line> lines, BigDecimal subtotal, BigDecimal totalTax, BigDecimal discount,
                           BigDecimal total, Coupon coupon) {

    public static final int MONEY_SCALE = 2;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(MONEY_SCALE);

    public record Line(CheckoutItem item, BigDecimal subtotal, BigDecimal discountAmount, BigDecimal taxPercent,
                       BigDecimal taxAmount) {
    }

    /** The coupon code as promotion knows it, or null without a coupon. */
    public String couponCode() {
        return coupon == null ? null : coupon.code();
    }

    /** Rounds a price to cents (product prices are doubles; money is BigDecimal from the boundary on). */
    public static BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    /** The subtotal of a checkout before any discount (what promotion checks the minimum order against). */
    public static BigDecimal subtotalOf(List<CheckoutItem> items) {
        return items.stream().map(OrderPricing::lineSubtotal).reduce(ZERO, BigDecimal::add);
    }

    /**
     * Prices the lines.
     *
     * @param ratesByTaxClass percent per tax class id; classes missing from the map have no rate (0 %)
     * @param coupon          the verified coupon, or null
     */
    public static OrderPricing of(List<CheckoutItem> items, Map<Long, BigDecimal> ratesByTaxClass, Coupon coupon) {
        List<BigDecimal> subtotals = items.stream().map(OrderPricing::lineSubtotal).toList();
        List<BigDecimal> discounts = discounts(items, subtotals, coupon);
        List<Line> lines = new ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            CheckoutItem item = items.get(i);
            BigDecimal rate = item.getTaxClassId() == null ? BigDecimal.ZERO
                : ratesByTaxClass.getOrDefault(item.getTaxClassId(), BigDecimal.ZERO);
            BigDecimal taxable = subtotals.get(i).subtract(discounts.get(i));
            BigDecimal tax = money(taxable.multiply(rate).divide(HUNDRED));
            lines.add(new Line(item, subtotals.get(i), discounts.get(i), rate, tax));
        }
        BigDecimal subtotal = lines.stream().map(Line::subtotal).reduce(ZERO, BigDecimal::add);
        BigDecimal discount = lines.stream().map(Line::discountAmount).reduce(ZERO, BigDecimal::add);
        BigDecimal tax = lines.stream().map(Line::taxAmount).reduce(ZERO, BigDecimal::add);
        return new OrderPricing(List.copyOf(lines), subtotal, tax, discount, subtotal.subtract(discount).add(tax),
            coupon);
    }

    private static BigDecimal lineSubtotal(CheckoutItem item) {
        return money(item.getProductPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
    }

    /** The discount of each line (0 for lines the coupon does not apply to), never above the line subtotal. */
    private static List<BigDecimal> discounts(List<CheckoutItem> items, List<BigDecimal> subtotals, Coupon coupon) {
        List<BigDecimal> discounts = new ArrayList<>(items.size());
        items.forEach(item -> discounts.add(ZERO));
        if (coupon == null || coupon.value() == null || coupon.value().signum() <= 0) {
            return discounts;
        }
        List<Integer> eligible = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            if (coupon.eligibleProductIds().contains(items.get(i).getProductId()) && subtotals.get(i).signum() > 0) {
                eligible.add(i);
            }
        }
        if (eligible.isEmpty()) {
            return discounts;
        }
        if (coupon.type() == Coupon.Type.PERCENTAGE) {
            BigDecimal percent = coupon.value().min(HUNDRED);
            eligible.forEach(i -> discounts.set(i,
                money(subtotals.get(i).multiply(percent).divide(HUNDRED)).min(subtotals.get(i))));
            return discounts;
        }
        BigDecimal eligibleSubtotal = eligible.stream().map(subtotals::get).reduce(ZERO, BigDecimal::add);
        BigDecimal amount = money(coupon.value()).min(eligibleSubtotal);
        BigDecimal remaining = amount;
        for (int k = 0; k < eligible.size() - 1; k++) {
            int i = eligible.get(k);
            BigDecimal share = money(amount.multiply(subtotals.get(i)).divide(eligibleSubtotal, 10,
                RoundingMode.HALF_UP)).min(subtotals.get(i)).min(remaining);
            discounts.set(i, share);
            remaining = remaining.subtract(share);
        }
        // the rounding remainder goes on the last eligible line; if it does not fit there (extreme rounding), the
        // excess moves back to earlier lines that still have room, so every line stays between 0 and its subtotal
        for (int k = eligible.size() - 1; k >= 0 && remaining.signum() > 0; k--) {
            int i = eligible.get(k);
            BigDecimal room = subtotals.get(i).subtract(discounts.get(i));
            BigDecimal extra = remaining.min(room);
            discounts.set(i, discounts.get(i).add(extra));
            remaining = remaining.subtract(extra);
        }
        return discounts;
    }
}
