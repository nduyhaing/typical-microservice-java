package com.shopapp.payment.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shopapp.payment.service.PaymentReconciler;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * A completed PayPal payment order has not accepted is retried by the reconciler with a back-off; a refusal stops the
 * retries and is flagged in the back office (decisions.md 5-61, 5-63).
 */
class PaymentReconcileIT extends PaymentItSupport {

    @Autowired
    private PaymentReconciler reconciler;

    /** A captured PayPal payment order could not be told about (order answered 503). */
    private long capturedWhileOrderDown(long orderId) throws Exception {
        order(orderId, "u-1", "120.50", "PENDING_PAYMENT", "PENDING");
        mockMvc.perform(json(post("/init"), "{\"orderId\":" + orderId + ",\"paymentMethod\":\"PAYPAL\"}")
            .with(customer("u-1"))).andExpect(status().isOk());
        String token = "PP" + PAYPAL_ORDER_SEQ.get();
        ORDER_PAY_STATUS.set(503);
        mockMvc.perform(json(post("/capture"), "{\"token\":\"" + token + "\"}").with(customer("u-1")))
            .andExpect(status().isServiceUnavailable());
        return paymentIdOf(token);
    }

    private Map<String, Object> row(long paymentId) {
        return jdbc.queryForMap("select payment_status, order_confirmed, order_refused, order_confirm_attempts,"
            + " next_order_confirm_on > now() as waiting,"
            + " extract(epoch from next_order_confirm_on - now()) as wait_seconds from payment where id = ?",
            paymentId);
    }

    private void due(long paymentId) {
        jdbc.update("update payment set next_order_confirm_on = now() - interval '1 second' where id = ?", paymentId);
    }

    @Test
    void orderDownAtCapture_isRetriedWithABackOff_untilOrderAcceptsIt() throws Exception {
        long paymentId = capturedWhileOrderDown(10);
        Map<String, Object> failed = row(paymentId);
        assertThat(failed.get("payment_status")).isEqualTo("COMPLETED");
        assertThat(failed.get("order_confirmed")).isEqualTo(false);
        assertThat(failed.get("order_confirm_attempts")).isEqualTo(1);
        assertThat(failed.get("waiting")).isEqualTo(true);
        assertThat(ORDER_PAY_CALLS).hasSize(1);

        // not due yet: nothing is sent
        assertThat(reconciler.reconcile()).isZero();
        assertThat(ORDER_PAY_CALLS).hasSize(1);

        // due, order still down: one more try, the wait doubles
        due(paymentId);
        assertThat(reconciler.reconcile()).isZero();
        assertThat(ORDER_PAY_CALLS).hasSize(2);
        Map<String, Object> again = row(paymentId);
        assertThat(again.get("order_confirm_attempts")).isEqualTo(2);
        assertThat(((Number) again.get("wait_seconds")).doubleValue()).isBetween(50.0, 61.0);

        // order is back: accepted, and never tried again
        ORDER_PAY_STATUS.set(200);
        due(paymentId);
        assertThat(reconciler.reconcile()).isEqualTo(1);
        assertThat(row(paymentId).get("order_confirmed")).isEqualTo(true);
        assertThat(ORDERS.get(10L).paymentStatus()).isEqualTo("COMPLETED");
        assertThat(ORDER_PAYMENT_IDS.get(10L)).isEqualTo(paymentId);
        due(paymentId);
        assertThat(reconciler.reconcile()).isZero();
        assertThat(ORDER_PAY_CALLS).hasSize(3);
        assertThat(PAYPAL_CAPTURE_CALLS).hasSize(1);
    }

    @Test
    void orderRefusingTheCompletedPayment_stopsTheRetries_andFlagsItForARefund() throws Exception {
        long paymentId = capturedWhileOrderDown(10);
        ORDER_PAY_STATUS.set(400);
        due(paymentId);

        assertThat(reconciler.reconcile()).isZero();
        Map<String, Object> refused = row(paymentId);
        assertThat(refused.get("order_refused")).isEqualTo(true);
        assertThat(refused.get("order_confirmed")).isEqualTo(false);
        due(paymentId);
        reconciler.reconcile();
        assertThat(ORDER_PAY_CALLS).hasSize(2);

        mockMvc.perform(get("/backoffice/payments").param("needsAttention", "true").with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.payments.length()").value(1))
            .andExpect(jsonPath("$.payments[0].id").value(paymentId))
            .andExpect(jsonPath("$.payments[0].needsAttention").value(true))
            .andExpect(jsonPath("$.payments[0].orderRefused").value(true))
            .andExpect(jsonPath("$.payments[0].failureMessage").value(
                org.hamcrest.Matchers.startsWith("Order refused the payment")));
    }

    @Test
    void captureRefusedByOrder_isFlaggedAtOnce_andAnOrdinaryPaymentIsNot() throws Exception {
        order(10, "u-1", "120.50", "PENDING_PAYMENT", "PENDING");
        mockMvc.perform(json(post("/init"), "{\"orderId\":10,\"paymentMethod\":\"PAYPAL\"}").with(customer("u-1")))
            .andExpect(status().isOk());
        String token = "PP" + PAYPAL_ORDER_SEQ.get();
        ORDER_PAY_STATUS.set(400);
        mockMvc.perform(json(post("/capture"), "{\"token\":\"" + token + "\"}").with(customer("u-1")))
            .andExpect(status().isBadRequest());
        ORDER_PAY_STATUS.set(200);
        order(11, "u-1", "120.50", "PENDING_PAYMENT", "PENDING");
        mockMvc.perform(json(post("/init"), "{\"orderId\":11,\"paymentMethod\":\"COD\"}").with(customer("u-1")))
            .andExpect(status().isOk());

        long refused = paymentIdOf(token);
        assertThat(row(refused).get("order_refused")).isEqualTo(true);
        mockMvc.perform(get("/backoffice/payments").with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.payments.length()").value(2))
            .andExpect(jsonPath("$.payments[?(@.id == " + refused + ")].needsAttention")
                .value(org.hamcrest.Matchers.contains(true)))
            .andExpect(jsonPath("$.payments[?(@.id != " + refused + ")].needsAttention")
                .value(org.hamcrest.Matchers.contains(false)));
    }

    @Test
    void oneRun_triesAtMostTwentyPayments_neverTriedFirst() throws Exception {
        for (int i = 1; i <= 25; i++) {
            order(100 + i, "u-1", "10.00", "PENDING_PAYMENT", "PENDING");
            jdbc.update("insert into payment (order_id, customer_id, amount, payment_method, payment_status,"
                + " gateway_transaction_id, order_confirmed, next_order_confirm_on) values (?, 'u-1', 10.00, 'PAYPAL',"
                + " 'COMPLETED', ?, false, case when ? then now() - interval '1 hour' else null end)",
                100 + i, "CAP-" + i, i > 20);
        }
        ORDER_PAY_STATUS.set(503);

        reconciler.reconcile();

        assertThat(ORDER_PAY_CALLS).hasSize(20);
        // the five already-tried ones wait for the next run
        assertThat(jdbc.queryForObject("select count(*) from payment where order_confirm_attempts = 0", Long.class))
            .isEqualTo(5);
    }
}
