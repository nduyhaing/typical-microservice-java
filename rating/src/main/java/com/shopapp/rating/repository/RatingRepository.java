package com.shopapp.rating.repository;

import com.shopapp.rating.model.Rating;
import java.time.ZonedDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface RatingRepository extends JpaRepository<Rating, Long> {

    Page<Rating> findByProductId(Long productId, Pageable pageable);

    /**
     * YAS getRatingListWithFilter: contains-filters ignoring case; the caller escapes {@code %}, {@code _} and
     * {@code \} so they match literally (decisions.md 4-34).
     */
    @Query("SELECT r FROM Rating r"
        + " WHERE LOWER(COALESCE(r.productName, '')) LIKE CONCAT('%', :productName, '%') ESCAPE '\\'"
        + " AND LOWER(CONCAT(COALESCE(r.firstName, ''), ' ', COALESCE(r.lastName, '')))"
        + " LIKE CONCAT('%', :customerName, '%') ESCAPE '\\'"
        + " AND LOWER(r.content) LIKE CONCAT('%', :message, '%') ESCAPE '\\'"
        + " AND r.createdOn >= :createdFrom AND r.createdOn <= :createdTo")
    Page<Rating> getRatingListWithFilter(@Param("productName") String productName,
                                         @Param("customerName") String customerName,
                                         @Param("message") String message,
                                         @Param("createdFrom") ZonedDateTime createdFrom,
                                         @Param("createdTo") ZonedDateTime createdTo,
                                         Pageable pageable);

    /** Average star of the product, null without ratings. */
    @Query("SELECT AVG(r.ratingStar) FROM Rating r WHERE r.productId = :productId")
    Double getAverageStar(@Param("productId") Long productId);

    boolean existsByCreatedByAndProductId(String createdBy, Long productId);

    @Query("SELECT r FROM Rating r ORDER BY r.createdOn DESC, r.id DESC")
    List<Rating> getLatestRatings(Pageable pageable);
}
