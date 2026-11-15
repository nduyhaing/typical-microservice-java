package com.shopapp.payment.paypal.viewmodel;

/** Whether PayPal credentials are configured, and the one currency payment-paypal charges in. */
public record PaypalStatusVm(boolean configured, String currency) {
}
