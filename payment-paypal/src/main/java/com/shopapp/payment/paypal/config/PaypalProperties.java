package com.shopapp.payment.paypal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * PayPal REST credentials and the storefront return/cancel URLs (decisions.md 5-21). The client id and secret come
 * from {@code .env} ({@code PAYPAL_CLIENT_ID} / {@code PAYPAL_CLIENT_SECRET}); YAS kept them in the payment
 * provider's {@code additional_settings} column, i.e. in the database and readable by any admin.
 *
 * @param baseUrl PayPal REST base URL (sandbox {@code https://api-m.sandbox.paypal.com})
 */
@ConfigurationProperties(prefix = "shopapp.paypal")
public record PaypalProperties(String baseUrl, String clientId, String clientSecret, String returnUrl,
                               String cancelUrl, String brandName) {

    /** Both credentials are set; otherwise every PayPal call answers 503 "PayPal is not configured". */
    public boolean configured() {
        return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
    }
}
