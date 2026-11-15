package com.shopapp.product.repository;

import com.shopapp.product.model.Brand;
import com.shopapp.product.model.Product;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findAllByBrandAndIsPublishedTrueOrderByIdAsc(Brand brand);

    /** Storefront brand listing: also hides variations (YAS shows them; decisions.md 2a-8). */
    List<Product> findAllByBrandAndIsPublishedTrueAndIsVisibleIndividuallyTrueOrderByIdAsc(Brand brand);

    Optional<Product> findBySlugAndIsPublishedTrue(String slug);

    /** Storefront detail: variations (not listed on their own) have no page of their own (decisions.md 2a-15). */
    Optional<Product> findBySlugAndIsPublishedTrueAndIsVisibleIndividuallyTrue(String slug);

    Optional<Product> findByIdAndIsPublishedTrue(Long id);

    /**
     * Storefront id lookups: published, and a variation also needs a published parent (its own flag only means
     * "not deleted", decisions.md 3a-17, 3a-33).
     */
    @Query("SELECT p FROM Product p LEFT JOIN p.parent parent WHERE p.id IN :productIds AND p.isPublished = TRUE "
        + "AND (parent IS NULL OR parent.isPublished = TRUE) ORDER BY p.id ASC")
    List<Product> findStorefrontByIds(@Param("productIds") List<Long> productIds);

    @Query("SELECT p FROM Product p LEFT JOIN p.parent parent WHERE p.id = :id AND p.isPublished = TRUE "
        + "AND (parent IS NULL OR parent.isPublished = TRUE)")
    Optional<Product> findStorefrontById(@Param("id") Long id);

    /**
     * Products that can be put in an order (decisions.md 2c-2): published, allowed to order, and not a parent with
     * options (only its variations can be bought). A variation also needs a published parent (decisions.md 3a-17).
     */
    @Query("SELECT p FROM Product p LEFT JOIN p.parent parent WHERE p.id IN :productIds AND p.isPublished = TRUE "
        + "AND p.isAllowedToOrder = TRUE AND p.hasOptions = FALSE "
        + "AND (parent IS NULL OR parent.isPublished = TRUE) ORDER BY p.id ASC")
    Page<Product> findOrderableByIds(@Param("productIds") List<Long> productIds, Pageable pageable);

    Optional<Product> findByGtinAndIsPublishedTrue(String gtin);

    Optional<Product> findBySkuAndIsPublishedTrue(String sku);

    /** Back office: a brand in use by any product (published or not) cannot be deleted. */
    boolean existsByBrandId(Long brandId);

    // Back office uniqueness checks cover every product, published or not (decisions.md 3a-16).
    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, Long id);

    boolean existsBySku(String sku);

    boolean existsBySkuAndIdNot(String sku, Long id);

    boolean existsByGtin(String gtin);

    boolean existsByGtinAndIdNot(String gtin, Long id);

    /**
     * Back office list: every product except variations (managed through their parent), published or not, filtered
     * by name (contains, case-insensitive) and exact brand name (YAS filters published only and drops products
     * without a brand when no brand filter is given — decisions.md 3a-16).
     */
    @Query(value = "SELECT p FROM Product p LEFT JOIN p.brand b WHERE p.parent IS NULL "
        + "AND LOWER(p.name) LIKE CONCAT('%', :productName, '%') "
        + "AND (:brandName = '' OR b.name = :brandName) ORDER BY p.id ASC",
        countQuery = "SELECT COUNT(p) FROM Product p LEFT JOIN p.brand b WHERE p.parent IS NULL "
            + "AND LOWER(p.name) LIKE CONCAT('%', :productName, '%') "
            + "AND (:brandName = '' OR b.name = :brandName)")
    Page<Product> findForBackoffice(@Param("productName") String productName, @Param("brandName") String brandName,
                                    Pageable pageable);

    @Query(value = "FROM Product p WHERE p.isFeatured = TRUE "
            + "AND p.isVisibleIndividually = TRUE "
            + "AND p.isPublished = TRUE ORDER BY p.id ASC ")
    Page<Product> getFeaturedProduct(Pageable pageable);

    @Query(value = "SELECT DISTINCT p FROM Product p LEFT JOIN p.productCategories pc LEFT JOIN pc.category c "
            + "WHERE LOWER(p.name) LIKE %:productName% "
            + "AND (c.slug = :categorySlug OR (:categorySlug IS NULL OR :categorySlug = '')) "
            + "AND (:startPrice IS NULL OR p.price >= :startPrice) "
            + "AND (:endPrice IS NULL OR p.price <= :endPrice) "
            + "AND p.isVisibleIndividually = TRUE "
            + "AND p.isPublished = TRUE "
            + "ORDER BY p.id ASC ")
    Page<Product> findByProductNameAndCategorySlugAndPriceBetween(@Param("productName") String productName,
                                                                  @Param("categorySlug") String categorySlug,
                                                                  @Param("startPrice") Double startPrice,
                                                                  @Param("endPrice") Double endPrice,
                                                                  Pageable pageable);


    @Query("SELECT DISTINCT p FROM Product p JOIN p.productCategories pc WHERE pc.id IN :categoryIds ORDER BY p.id ASC")
    List<Product> findByCategoryIdsIn(@Param("categoryIds") List<Long> categoryIds);

    @Query("SELECT p FROM Product p JOIN p.brand b WHERE b.id IN :brandIds ORDER BY p.id ASC")
    List<Product> findByBrandIdsIn(@Param("brandIds") List<Long> brandIds);

    @Query("SELECT p FROM Product p ORDER BY p.createdOn DESC, p.id DESC")
    List<Product> getLatestProducts(Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.id IN :productIds AND p.isPublished = TRUE")
    Page<Product> findAllPublishedProductsByIds(@Param("productIds") List<Long> productIds, Pageable pageable);

    /**
     * Atomic stock subtraction (decisions.md 3c-21): takes {@code quantity} units only when a stock-tracked product
     * has at least that many; returns 0 (nothing changed) otherwise. Concurrent calls queue on the row lock and
     * re-check the condition, so the last units are never sold twice.
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Product p SET p.stockQuantity = p.stockQuantity - :quantity "
        + "WHERE p.id = :id AND p.stockTrackingEnabled = TRUE AND p.stockQuantity >= :quantity")
    int subtractStock(@Param("id") Long id, @Param("quantity") long quantity);

    /** Gives units back (restore-quantity); a missing quantity counts as 0. */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Product p SET p.stockQuantity = COALESCE(p.stockQuantity, 0) + :quantity WHERE p.id = :id")
    int addStock(@Param("id") Long id, @Param("quantity") long quantity);

    /** Sets the stock quantity (admin update-quantity); the entity never writes the column (decisions.md 3c-61). */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Product p SET p.stockQuantity = :quantity WHERE p.id = :id")
    int setStock(@Param("id") Long id, @Param("quantity") Long quantity);

    /**
     * Inventory's stock adjustment (decisions.md 3c-60): adds {@code delta} (negative removes) to the current
     * quantity, atomically, never going below 0; a missing quantity counts as 0.
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Product p SET p.stockQuantity = GREATEST(COALESCE(p.stockQuantity, 0) + :delta, 0) "
        + "WHERE p.id = :id")
    int adjustStock(@Param("id") Long id, @Param("delta") long delta);

    /**
     * Inventory's product lookup (YAS for-warehouse): products that can hold stock (not a parent with options),
     * name and sku contain the given text (case-insensitive, empty = any), and in / not in the given ids.
     */
    @Query("SELECT p FROM Product p WHERE p.hasOptions = FALSE "
        + "AND LOWER(p.name) LIKE CONCAT('%', :name, '%') "
        + "AND (:sku = '' OR LOWER(p.sku) LIKE CONCAT('%', :sku, '%')) "
        + "AND (:selection = 'ALL' OR (:selection = 'YES' AND p.id IN :productIds) "
        + "OR (:selection = 'NO' AND p.id NOT IN :productIds)) "
        + "ORDER BY p.id ASC")
    List<Product> findForWarehouse(@Param("name") String name, @Param("sku") String sku,
                                   @Param("productIds") List<Long> productIds,
                                   @Param("selection") String selection);
}
