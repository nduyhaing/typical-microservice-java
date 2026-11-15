package com.shopapp.payment.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.shopapp.payment.model.Payment;
import com.shopapp.payment.viewmodel.external.PaypalCaptureVm;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Every check a PayPal capture must pass before the order is marked paid (Review Focus 1). */
class CaptureVerifierTest {

    private static final BigDecimal TOTAL = new BigDecimal("120.50");

    private static Payment payment() {
        return Payment.builder().id(7L).orderId(10L).amount(TOTAL).paymentProviderCheckoutId("PP1").build();
    }

    private static PaypalCaptureVm capture(String orderStatus, String captureStatus, String amount, String currency,
                                           String customId, String paypalOrderId, String captureId) {
        return new PaypalCaptureVm(paypalOrderId, orderStatus, captureId, captureStatus,
            amount == null ? null : new BigDecimal(amount), currency, customId, new BigDecimal("0.71"));
    }

    private static PaypalCaptureVm good() {
        return capture("COMPLETED", "COMPLETED", "120.50", "USD", "payment-7", "PP1", "CAP1");
    }

    @Test
    void aMatchingCapture_passes_alsoWithAnotherScale() {
        assertThat(CaptureVerifier.problem(good(), payment(), TOTAL)).isEmpty();
        assertThat(CaptureVerifier.problem(capture("COMPLETED", "COMPLETED", "120.5", "USD", "payment-7", "PP1",
            "CAP1"), payment(), TOTAL)).isEmpty();
    }

    @Test
    void amountDifferentFromTheOrder_fails() {
        assertThat(CaptureVerifier.problem(capture("COMPLETED", "COMPLETED", "120.49", "USD", "payment-7", "PP1",
            "CAP1"), payment(), TOTAL)).hasValueSatisfying(reason -> assertThat(reason).contains("120.49"));
        assertThat(CaptureVerifier.problem(capture("COMPLETED", "COMPLETED", null, "USD", "payment-7", "PP1",
            "CAP1"), payment(), TOTAL)).isPresent();
    }

    @Test
    void orderTotalChangedSinceInit_fails() {
        assertThat(CaptureVerifier.problem(good(), payment(), new BigDecimal("99.00"))).isPresent();
        assertThat(CaptureVerifier.problem(good(), payment(), null)).isPresent();
    }

    @Test
    void otherCurrency_fails() {
        assertThat(CaptureVerifier.problem(capture("COMPLETED", "COMPLETED", "120.50", "EUR", "payment-7", "PP1",
            "CAP1"), payment(), TOTAL)).isPresent();
        assertThat(CaptureVerifier.problem(capture("COMPLETED", "COMPLETED", "120.50", null, "payment-7", "PP1",
            "CAP1"), payment(), TOTAL)).isPresent();
    }

    @Test
    void otherReferenceOrPaypalOrder_fails() {
        assertThat(CaptureVerifier.problem(capture("COMPLETED", "COMPLETED", "120.50", "USD", "payment-8", "PP1",
            "CAP1"), payment(), TOTAL)).isPresent();
        assertThat(CaptureVerifier.problem(capture("COMPLETED", "COMPLETED", "120.50", "USD", null, "PP1",
            "CAP1"), payment(), TOTAL)).isPresent();
        assertThat(CaptureVerifier.problem(capture("COMPLETED", "COMPLETED", "120.50", "USD", "payment-7", "PP2",
            "CAP1"), payment(), TOTAL)).isPresent();
    }

    @Test
    void notCompletedOrNoCaptureId_fails() {
        assertThat(CaptureVerifier.problem(capture("COMPLETED", "PENDING", "120.50", "USD", "payment-7", "PP1",
            "CAP1"), payment(), TOTAL)).isPresent();
        assertThat(CaptureVerifier.problem(capture("APPROVED", "COMPLETED", "120.50", "USD", "payment-7", "PP1",
            "CAP1"), payment(), TOTAL)).isPresent();
        assertThat(CaptureVerifier.problem(capture("COMPLETED", "COMPLETED", "120.50", "USD", "payment-7", "PP1",
            " "), payment(), TOTAL)).isPresent();
    }
}
