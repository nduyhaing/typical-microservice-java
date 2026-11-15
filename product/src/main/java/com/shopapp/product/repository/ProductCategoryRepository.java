package com.shopapp.product.repository;

import com.shopapp.product.model.Category;
import com.shopapp.product.model.ProductCategory;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductCategoryRepository extends JpaRepository<ProductCategory, Long> {
    Page<ProductCategory> findAllByCategory(Pageable pageable, Category category);

    /**
     * Storefront variant of {@link #findAllByCategory}: only published products that are listed on their own
     * (YAS returns unpublished products and variations here; decisions.md 2a-8).
     */
    @Query("SELECT pc FROM ProductCategory pc WHERE pc.category = :category "
        + "AND pc.product.isPublished = TRUE AND pc.product.isVisibleIndividually = TRUE ORDER BY pc.product.id ASC")
    Page<ProductCategory> findStorefrontByCategory(@Param("category") Category category, Pageable pageable);

    List<ProductCategory> findAllByProductId(Long productId);

    /** (product id, category id) pairs of these products, in one query. */
    @Query("SELECT pc.product.id AS productId, pc.category.id AS categoryId FROM ProductCategory pc "
        + "WHERE pc.product.id IN :productIds")
    List<CategoryLink> findCategoryLinks(@Param("productIds") Collection<Long> productIds);

    /** One row of {@link #findCategoryLinks}. */
    interface CategoryLink {
        Long getProductId();

        Long getCategoryId();
    }

    boolean existsByCategoryId(Long categoryId);
}
