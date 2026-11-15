package com.shopapp.payment.viewmodel.payment;

import com.shopapp.payment.model.enumeration.PaymentStatus;

/** The PayPal payment after the buyer cancelled at PayPal. */
public record PaymentCancelVm(Long paymentId, Long orderId, PaymentStatus paymentStatus) {
}
