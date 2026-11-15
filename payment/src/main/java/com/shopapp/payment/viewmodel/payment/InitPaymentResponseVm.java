package com.shopapp.payment.viewmodel.payment;

/**
 * YAS {@code InitPaymentResponseVm}, with {@code paymentId} = our payment id (YAS: PayPal's order id). COD answers
 * status PENDING and no redirect; PayPal answers PayPal's order status and the approval URL for the buyer.
 */
public record InitPaymentResponseVm(String status, Long paymentId, String redirectUrl) {
}
