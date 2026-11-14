package com.shopapp.product.repository;

import com.shopapp.product.model.Brand;
import com.shopapp.product.model.Product;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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
     * Products that can be put in an order (decisions.md 2c-2): published, allowed to order, and not a parent with
     * options (only its variations can be bought). A variation also needs a published parent (decisions.md 3a-17).
     */
    @Query("SELECT p FROM Product p LEFT JOIN p.parent parent WHERE p.id IN :productIds AND p.isPublished = TRUE "
        + "AND p.isAllowedToOrder = TRUE AND p.hasOptions = FALSE "
        + "AND (parent IS NULL OR parent.isPublished = TRUE) ORDER BY p.id ASC")
    Page<Product> findOrderableByIds(@Param("productIds") List<Long> productIds, Pageable pageable);

    Optional<Product> findByGtinAndIsPublishedTrue(String gtin);

    Optional<Product> findBySkuAndIsPublishedTrue(String sku);



    List<Product> findAllByIdIn(List<Long> productIds);

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
}