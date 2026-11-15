package com.shopapp.payment.service;

import com.shopapp.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Retries telling order about completed payments it has not accepted yet (decisions.md 5-61): money was captured at
 * PayPal but order was down (or slow) when the buyer came back, and the buyer may never replay the capture. Runs every
 * minute, at most {@link #BATCH} payments per run, each with its own back-off (30 s doubling to an hour); order
 * refusing stops the retries and leaves the payment flagged in the back office for a manual refund.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentReconciler {

    /** Payments tried per run. */
    static final int BATCH = 20;

    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;

    /**
     * One run.
     *
     * @return how many payments order accepted in this run
     */
    @Scheduled(initialDelayString = "${shopapp.payment.reconcile-interval:PT1M}",
        fixedDelayString = "${shopapp.payment.reconcile-interval:PT1M}")
    public int reconcile() {
        int accepted = 0;
        for (Long id : paymentRepository.findIdsToReconcile(BATCH)) {
            try {
                if (paymentService.reconcile(id)) {
                    accepted++;
                }
            } catch (RuntimeException ex) {
                log.warn("Could not reconcile payment {}: {}", id, ex.getMessage());
            }
        }
        if (accepted > 0) {
            log.info("Order accepted {} completed payment(s) on retry", accepted);
        }
        return accepted;
    }
}
