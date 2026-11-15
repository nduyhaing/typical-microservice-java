package com.shopapp.payment.viewmodel.payment;

import com.shopapp.payment.model.Payment;
import com.shopapp.payment.model.enumeration.PaymentMethod;
import com.shopapp.payment.model.enumeration.PaymentStatus;
import java.math.BigDecimal;

/** YAS {@code CapturePaymentResponseVm} plus our payment id. */
public record CapturePaymentResponseVm(
    Long orderId,
    String checkoutId,
    Long paymentId,
    BigDecimal amount,
    BigDecimal paymentFee,
    String gatewayTransactionId,
    PaymentMethod paymentMethod,
    PaymentStatus paymentStatus,
    String failureMessage
) {

    public static CapturePaymentResponseVm fromModel(Payment payment) {
        return new CapturePaymentResponseVm(payment.getOrderId(), payment.getCheckoutId(), payment.getId(),
            payment.getAmount(), payment.getPaymentFee(), payment.getGatewayTransactionId(),
            payment.getPaymentMethod(), payment.getPaymentStatus(), payment.getFailureMessage());
    }
}
