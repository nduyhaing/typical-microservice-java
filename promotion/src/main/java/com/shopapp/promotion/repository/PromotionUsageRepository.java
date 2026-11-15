package com.shopapp.promotion.repository;

import com.shopapp.promotion.model.PromotionUsage;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PromotionUsageRepository extends JpaRepository<PromotionUsage, Long> {

    boolean existsByPromotionId(Long promotionId);

    Optional<PromotionUsage> findByReference(UUID reference);

    /** Marks the caller's own, not yet released use as released; returns the rows changed (0 or 1). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE promotion_usage SET released_on = now() WHERE reference = :reference "
        + "AND user_id = :userId AND released_on IS NULL", nativeQuery = true)
    int release(@Param("reference") UUID reference, @Param("userId") String userId);
}
