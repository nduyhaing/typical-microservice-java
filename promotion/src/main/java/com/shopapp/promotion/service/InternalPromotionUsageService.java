package com.shopapp.promotion.service;

import com.shopapp.promotion.repository.PromotionRepository;
import com.shopapp.promotion.repository.PromotionUsageRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Coupon uses given back by the order service itself (decisions.md 5-5). */
@Service
@RequiredArgsConstructor
public class InternalPromotionUsageService {

    private final PromotionRepository promotionRepository;
    private final PromotionUsageRepository promotionUsageRepository;

    /**
     * Gives back the use taken under {@code reference} whoever took it (a cancelled order's customer), once: the
     * conditional UPDATE marks it released only if it was not, so concurrent calls count it back at most once.
     * Unknown references change nothing.
     */
    @Transactional
    public void releaseUsage(UUID reference) {
        promotionUsageRepository.findByReference(reference)
            .filter(usage -> promotionUsageRepository.releaseOfAnyUser(reference) == 1)
            .ifPresent(usage -> promotionRepository.decrementUsage(usage.getPromotionId()));
    }
}
