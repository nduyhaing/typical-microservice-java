package com.shopapp.order.service;

import java.util.UUID;

/**
 * The coupon use taken for one "place order" attempt (decisions.md 4-53): its own random reference, separate from the
 * stock reference and never shown to the customer, and whether promotion may hold a use under it — set when
 * apply-usage is sent, cleared when promotion answers 4xx (nothing taken).
 */
public final class CouponUsage {

    private final UUID reference = UUID.randomUUID();
    private boolean mayHoldUse;

    public UUID reference() {
        return reference;
    }

    public boolean mayHoldUse() {
        return mayHoldUse;
    }

    void sent() {
        mayHoldUse = true;
    }

    void refused() {
        mayHoldUse = false;
    }
}
