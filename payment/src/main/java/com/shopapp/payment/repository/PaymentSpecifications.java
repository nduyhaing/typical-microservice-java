package com.shopapp.payment.repository;

import com.shopapp.payment.model.Payment;
import com.shopapp.payment.model.enumeration.PaymentMethod;
import com.shopapp.payment.model.enumeration.PaymentStatus;
import org.springframework.data.jpa.domain.Specification;

/** Filters of the admin payments list; a null filter matches everything. */
public final class PaymentSpecifications {

    private PaymentSpecifications() {
    }

    /** {@code needsAttention}: only payments {@code PaymentVm.needsAttention} flags (5-63). */
    public static Specification<Payment> filtered(Long orderId, PaymentStatus status, PaymentMethod method,
                                                  boolean needsAttention) {
        return (root, query, cb) -> cb.and(
            orderId == null ? cb.conjunction() : cb.equal(root.get("orderId"), orderId),
            status == null ? cb.conjunction() : cb.equal(root.get("paymentStatus"), status),
            method == null ? cb.conjunction() : cb.equal(root.get("paymentMethod"), method),
            !needsAttention ? cb.conjunction() : cb.or(
                cb.and(cb.equal(root.get("paymentStatus"), PaymentStatus.COMPLETED),
                    cb.isFalse(root.get("orderConfirmed"))),
                cb.and(cb.equal(root.get("paymentStatus"), PaymentStatus.CANCELLED),
                    cb.isNotNull(root.get("gatewayTransactionId")))));
    }
}
