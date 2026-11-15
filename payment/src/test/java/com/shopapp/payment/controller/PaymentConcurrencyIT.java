package com.shopapp.payment.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Init, capture and confirmation of one order run one after another (decisions.md 5-60): a payment completed by a
 * capture is never overwritten by a concurrent init, two captures capture once, two inits leave one pending attempt.
 * The PayPal stubs are held at a gate to make the requests overlap.
 */
class PaymentConcurrencyIT extends PaymentItSupport {

    private final ExecutorService pool = Executors.newFixedThreadPool(4);

    @AfterEach
    void stopPool() {
        pool.shutdownNow();
    }

    private Future<MvcResult> async(Callable<MvcResult> request) {
        return pool.submit(request);
    }

    private MvcResult initPaypalNow(long orderId) throws Exception {
        return mockMvc.perform(json(post("/init"), "{\"orderId\":" + orderId + ",\"paymentMethod\":\"PAYPAL\"}")
            .with(customer("u-1"))).andReturn();
    }

    private MvcResult initCodNow(long orderId) throws Exception {
        return mockMvc.perform(json(post("/init"), "{\"orderId\":" + orderId + ",\"paymentMethod\":\"COD\"}")
            .with(customer("u-1"))).andReturn();
    }

    private MvcResult captureNow(String token) throws Exception {
        return mockMvc.perform(json(post("/capture"), "{\"token\":\"" + token + "\"}").with(customer("u-1")))
            .andReturn();
    }

    private static int status(Future<MvcResult> result) throws Exception {
        return result.get(30, TimeUnit.SECONDS).getResponse().getStatus();
    }

    @Test
    void codInitDuringACapture_waits_andTheCaptureStaysCompletedWithItsCaptureId() throws Exception {
        order(10, "u-1", "120.50", "PENDING_PAYMENT", "PENDING");
        assertThat(initPaypalNow(10).getResponse().getStatus()).isEqualTo(200);
        String token = "PP" + PAYPAL_ORDER_SEQ.get();
        CountDownLatch release = new CountDownLatch(1);
        CAPTURE_GATE.set(release);

        Future<MvcResult> capture = async(() -> captureNow(token));
        await("the capture to reach PayPal", () -> PAYPAL_CAPTURE_CALLS.size() == 1);
        Future<MvcResult> cod = async(() -> initCodNow(10));
        Thread.sleep(700);
        assertThat(cod.isDone()).as("the COD init waits for the running capture").isFalse();
        release.countDown();

        assertThat(status(capture)).isEqualTo(200);
        // the order was paid by the capture meanwhile: no second payment
        assertThat(status(cod)).isEqualTo(400);
        long paymentId = paymentIdOf(token);
        assertThat(paymentStatus(paymentId)).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("select gateway_transaction_id from payment where id = ?", String.class,
            paymentId)).isEqualTo("CAP" + token);
        assertThat(countWithStatus("PENDING")).isZero();
        assertThat(countWithStatus("COMPLETED")).isEqualTo(1);
    }

    @Test
    void twoConcurrentCaptures_captureOnce_andPayOnce() throws Exception {
        order(10, "u-1", "120.50", "PENDING_PAYMENT", "PENDING");
        assertThat(initPaypalNow(10).getResponse().getStatus()).isEqualTo(200);
        String token = "PP" + PAYPAL_ORDER_SEQ.get();
        CountDownLatch release = new CountDownLatch(1);
        CAPTURE_GATE.set(release);

        Future<MvcResult> first = async(() -> captureNow(token));
        await("the first capture to reach PayPal", () -> PAYPAL_CAPTURE_CALLS.size() == 1);
        Future<MvcResult> second = async(() -> captureNow(token));
        Thread.sleep(500);
        release.countDown();

        assertThat(status(first)).isEqualTo(200);
        assertThat(status(second)).isEqualTo(200);
        assertThat(PAYPAL_CAPTURE_CALLS).hasSize(1);
        long paymentId = paymentIdOf(token);
        assertThat(ORDERS.get(10L).paymentStatus()).isEqualTo("COMPLETED");
        assertThat(ORDER_PAYMENT_IDS.get(10L)).isEqualTo(paymentId);
        assertThat(ORDER_PAY_CALLS).allSatisfy(call ->
            assertThat(((Number) JsonPath.read(call.body(), "$.paymentId")).longValue()).isEqualTo(paymentId));
        assertThat(countWithStatus("COMPLETED")).isEqualTo(1);
    }

    @Test
    void twoConcurrentInits_leaveExactlyOnePendingAttempt_andOnlyItsApprovalUrlIsAnswered() throws Exception {
        order(10, "u-1", "120.50", "PENDING_PAYMENT", "PENDING");
        CountDownLatch release = new CountDownLatch(1);
        CREATE_GATE.set(release);

        Future<MvcResult> first = async(() -> initPaypalNow(10));
        await("the first init to reach PayPal", () -> PAYPAL_CREATE_CALLS.size() == 1);
        Future<MvcResult> second = async(() -> initPaypalNow(10));
        await("the second init to reach PayPal", () -> PAYPAL_CREATE_CALLS.size() == 2);
        release.countDown();

        List<MvcResult> results = List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
        assertThat(countWithStatus("PENDING")).isEqualTo(1);
        long ok = results.stream().filter(result -> result.getResponse().getStatus() == 200).count();
        assertThat(ok).isEqualTo(1);
        for (MvcResult result : results) {
            if (result.getResponse().getStatus() == 200) {
                long paymentId = ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.paymentId"))
                    .longValue();
                assertThat(paymentStatus(paymentId)).isEqualTo("PENDING");
            } else {
                assertThat(result.getResponse().getStatus()).isEqualTo(400);
            }
        }
    }

    @Test
    void codAndPaypalInitsAtOnce_leaveExactlyOnePendingAttempt() throws Exception {
        order(10, "u-1", "120.50", "PENDING_PAYMENT", "PENDING");
        CountDownLatch release = new CountDownLatch(1);
        CREATE_GATE.set(release);

        Future<MvcResult> paypal = async(() -> initPaypalNow(10));
        await("the PayPal init to reach PayPal", () -> PAYPAL_CREATE_CALLS.size() == 1);
        assertThat(initCodNow(10).getResponse().getStatus()).isEqualTo(200);
        release.countDown();

        // the newer COD attempt replaced the PayPal one while PayPal was creating it
        assertThat(status(paypal)).isEqualTo(400);
        assertThat(countWithStatus("PENDING")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select payment_method from payment where payment_status = 'PENDING'",
            String.class)).isEqualTo("COD");
    }
}
