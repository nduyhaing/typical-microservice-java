package com.shopapp.payment.viewmodel.external;

/** A PayPal order created by payment-paypal: its id and the buyer's approval URL. */
public record PaypalOrderVm(String paypalOrderId, String status, String approvalUrl) {
}
