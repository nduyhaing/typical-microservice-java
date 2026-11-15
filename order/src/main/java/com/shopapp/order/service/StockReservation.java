package com.shopapp.order.service;

import java.util.UUID;

/**
 * The stock taken for one "place order" attempt (decisions.md 3c-45): a random reference generated here, never
 * shown to the customer (else they could give a placed order's stock back), and whether product may hold units
 * under it — set when subtract-quantity is sent, cleared when product answers 4xx (nothing taken).
 */
public final class StockReservation {

    private final String reference = UUID.randomUUID().toString();
    private boolean mayHoldStock;

    public String reference() {
        return reference;
    }

    public boolean mayHoldStock() {
        return mayHoldStock;
    }

    void sent() {
        mayHoldStock = true;
    }

    void refused() {
        mayHoldStock = false;
    }
}
