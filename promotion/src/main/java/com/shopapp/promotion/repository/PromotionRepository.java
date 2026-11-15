package com.shopapp.promotion.repository;

import com.shopapp.promotion.model.Promotion;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PromotionRepository extends JpaRepository<Promotion, Long> {

    /** With its targets loaded, so it can be used outside a transaction. */
    @EntityGraph(attributePaths = "promotionApplies")
    @Query("SELECT p FROM Promotion p WHERE LOWER(p.couponCode) = LOWER(:couponCode)")
    Optional<Promotion> findByCouponCodeIgnoreCase(@Param("couponCode") String couponCode);

    @Query("SELECT COUNT(p) > 0 FROM Promotion p WHERE LOWER(p.couponCode) = LOWER(:couponCode) "
        + "AND (:excludeId IS NULL OR p.id <> :excludeId)")
    boolean existsCouponCode(@Param("couponCode") String couponCode, @Param("excludeId") Long excludeId);

    @Query("SELECT COUNT(p) > 0 FROM Promotion p WHERE LOWER(p.slug) = LOWER(:slug) "
        + "AND (:excludeId IS NULL OR p.id <> :excludeId)")
    boolean existsSlug(@Param("slug") String slug, @Param("excludeId") Long excludeId);

    /**
     * Name and coupon code contain the (lower-cased, LIKE-escaped) filters; a start date keeps promotions still
     * running at or after it, an end date those starting at or before it (overlap; YAS needed both dates).
     */
    @Query("SELECT p FROM Promotion p "
        + "WHERE LOWER(p.name) LIKE CONCAT('%', :name, '%') ESCAPE '!' "
        + "AND LOWER(p.couponCode) LIKE CONCAT('%', :couponCode, '%') ESCAPE '!' "
        + "AND (CAST(:startDate AS java.time.Instant) IS NULL OR p.endDate >= :startDate) "
        + "AND (CAST(:endDate AS java.time.Instant) IS NULL OR p.startDate <= :endDate)")
    Page<Promotion> findPromotions(@Param("name") String name,
                                   @Param("couponCode") String couponCode,
                                   @Param("startDate") Instant startDate,
                                   @Param("endDate") Instant endDate,
                                   Pageable pageable);

    /**
     * Takes one use if the promotion is active, running at {@code now} and not used up; returns the rows changed
     * (0 or 1). One statement, so concurrent callers can never push the count past the limit.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE promotion SET usage_count = usage_count + 1 WHERE id = :id AND is_active "
        + "AND start_date <= :now AND end_date >= :now "
        + "AND (usage_type = 'UNLIMITED' OR usage_count < usage_limit)", nativeQuery = true)
    int incrementUsage(@Param("id") Long id, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE promotion SET usage_count = usage_count - 1 WHERE id = :id AND usage_count > 0",
        nativeQuery = true)
    int decrementUsage(@Param("id") Long id);
}
