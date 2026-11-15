package com.shopapp.order.service;

import com.shopapp.order.model.Order;
import com.shopapp.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Gives a cancelled, rejected or refunded-before-shipping order's stock and coupon use back (decisions.md 5-4). Runs
 * after the status change is committed, outside any transaction (no remote call holds a row lock): product
 * {@code PUT /internal/products/restore-quantity?reference=} with the order's stored stock reference and promotion
 * {@code POST /internal/promotions/release-usage?reference=} with its coupon reference, both with the order service's
 * own token, so it works the same whoever cancels. Each is recorded as done ({@code stock_restored_on},
 * {@code promotion_usage_released_on}) only when the service confirmed; a retry (the same cancel again, or the sweep
 * every few minutes) calls only what is still pending. Product and promotion give back at most once per reference,
 * so even two concurrent attempts cannot give back twice.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderGoodsBackService {

    /** Orders looked at per sweep. */
    static final int SWEEP_BATCH = 50;

    private final OrderRepository orderRepository;
    private final ProductService productService;
    private final PromotionService promotionService;

    /**
     * Gives back what the order still owes, if it was asked to.
     *
     * @return whether nothing is pending any more
     */
    public boolean giveBack(Long orderId) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null || order.getGoodsBackRequestedOn() == null) {
            return true;
        }
        boolean done = true;
        if (order.getStockReference() != null && order.getStockRestoredOn() == null) {
            if (productService.restoreOrderStock(orderId, order.getStockReference())) {
                orderRepository.markStockRestored(orderId);
            } else {
                done = false;
            }
        }
        if (order.getPromotionUsageReference() != null && order.getCouponReleasedOn() == null) {
            if (promotionService.releaseOrderUsage(orderId, order.getPromotionUsageReference())) {
                orderRepository.markCouponReleased(orderId);
            } else {
                done = false;
            }
        }
        if (!done) {
            orderRepository.recordGoodsBackAttempt(orderId);
            log.warn("Order {} still owes its stock or coupon use back; retried later", orderId);
        }
        return done;
    }

    /**
     * Retries orders whose stock or coupon use could not be given back (product or promotion was down), at most
     * {@link #SWEEP_BATCH} per run, the least recently tried first, with a back-off per order (5-65).
     */
    @Scheduled(initialDelayString = "${shopapp.order.goods-back-retry:PT5M}",
        fixedDelayString = "${shopapp.order.goods-back-retry:PT5M}")
    public void retryPending() {
        orderRepository.findIdsOwingGoodsBack(SWEEP_BATCH).forEach(this::giveBack);
    }
}
