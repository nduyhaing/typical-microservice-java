package com.shopapp.payment.paypal.viewmodel;

/**
 * A created PayPal order: its id (the {@code token} PayPal appends to the return URL) and the URL the buyer approves
 * the payment at.
 */
public record PaypalOrderVm(String paypalOrderId, String status, String approvalUrl) {
}
