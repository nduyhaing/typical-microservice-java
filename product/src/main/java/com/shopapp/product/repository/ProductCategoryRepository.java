package com.shopapp.product.repository;

import com.shopapp.product.model.Category;
import com.shopapp.product.model.ProductCategory;
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

    boolean existsByCategoryId(Long categoryId);
}
