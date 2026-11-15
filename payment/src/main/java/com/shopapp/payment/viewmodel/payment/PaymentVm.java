package com.shopapp.payment.viewmodel.payment;

import com.shopapp.payment.model.Payment;
import com.shopapp.payment.model.enumeration.PaymentMethod;
import com.shopapp.payment.model.enumeration.PaymentStatus;
import java.math.BigDecimal;
import java.time.ZonedDateTime;

/** A payment in the admin list (shop_app addition: YAS has no payments list; decisions.md 5-17). */
public record PaymentVm(
    Long id,
    Long orderId,
    String checkoutId,
    String customerId,
    BigDecimal amount,
    BigDecimal paymentFee,
    PaymentMethod paymentMethod,
    PaymentStatus paymentStatus,
    String gatewayTransactionId,
    String paymentProviderCheckoutId,
    String failureMessage,
    boolean orderConfirmed,
    ZonedDateTime createdOn,
    ZonedDateTime lastModifiedOn
) {

    public static PaymentVm fromModel(Payment payment) {
        return new PaymentVm(payment.getId(), payment.getOrderId(), payment.getCheckoutId(),
            payment.getCustomerId(), payment.getAmount(), payment.getPaymentFee(), payment.getPaymentMethod(),
            payment.getPaymentStatus(), payment.getGatewayTransactionId(), payment.getPaymentProviderCheckoutId(),
            payment.getFailureMessage(), payment.isOrderConfirmed(), payment.getCreatedOn(),
            payment.getLastModifiedOn());
    }
}
