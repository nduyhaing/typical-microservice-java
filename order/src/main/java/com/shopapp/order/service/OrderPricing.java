package com.shopapp.order.service;

import com.shopapp.order.model.CheckoutItem;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/**
 * The price of a checkout shipped to an address (decisions.md 3c-43): per line subtotal = price x quantity and
 * tax = subtotal x rate / 100 rounded half-up to cents; the order's tax is the sum of the rounded line taxes, so the
 * lines always add up. A line without tax class, or whose class has no rate for the address, pays 0 %.
 * total = subtotal + tax - discount (no discounts before the promotion slice).
 */
public record OrderPricing(List<Line> lines, BigDecimal subtotal, BigDecimal totalTax, BigDecimal discount,
                           BigDecimal total) {

    public static final int MONEY_SCALE = 2;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public record Line(CheckoutItem item, BigDecimal subtotal, BigDecimal taxPercent, BigDecimal taxAmount) {
    }

    /** Rounds a price to cents (product prices are doubles; money is BigDecimal from the boundary on). */
    public static BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Prices the lines.
     *
     * @param ratesByTaxClass percent per tax class id; classes missing from the map have no rate (0 %)
     */
    public static OrderPricing of(List<CheckoutItem> items, Map<Long, BigDecimal> ratesByTaxClass) {
        List<Line> lines = items.stream().map(item -> {
            BigDecimal subtotal = money(item.getProductPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
            BigDecimal rate = item.getTaxClassId() == null ? BigDecimal.ZERO
                : ratesByTaxClass.getOrDefault(item.getTaxClassId(), BigDecimal.ZERO);
            BigDecimal tax = money(subtotal.multiply(rate).divide(HUNDRED));
            return new Line(item, subtotal, rate, tax);
        }).toList();
        BigDecimal subtotal = lines.stream().map(Line::subtotal).reduce(money(BigDecimal.ZERO), BigDecimal::add);
        BigDecimal tax = lines.stream().map(Line::taxAmount).reduce(money(BigDecimal.ZERO), BigDecimal::add);
        BigDecimal discount = money(BigDecimal.ZERO);
        return new OrderPricing(lines, subtotal, tax, discount, subtotal.add(tax).subtract(discount));
    }
}
