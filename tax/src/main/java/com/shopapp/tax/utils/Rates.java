package com.shopapp.tax.utils;

import java.math.BigDecimal;

public final class Rates {

    private Rates() {
    }

    /**
     * Drops the column's trailing zeros but keeps one decimal, so 10.0000 is sent as {@code 10.0} and 8.8750 as
     * {@code 8.875} (never in exponent form).
     */
    public static BigDecimal display(BigDecimal rate) {
        BigDecimal stripped = rate.stripTrailingZeros();
        return stripped.scale() < 1 ? stripped.setScale(1) : stripped;
    }
}
