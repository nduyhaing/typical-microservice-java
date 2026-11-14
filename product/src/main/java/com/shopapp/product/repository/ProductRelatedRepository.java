package com.shopapp.product.repository;

import com.shopapp.product.model.Product;
import com.shopapp.product.model.ProductRelated;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductRelatedRepository extends JpaRepository<ProductRelated, Long> {
    Page<ProductRelated> findAllByProduct(Product product, Pageable pageable);

    /** Storefront: only related products a shopper may see (published, listed on their own).
     * The entity is named "product_related" in YAS, hence the JPQL entity name. */
    @Query("SELECT pr FROM product_related pr WHERE pr.product = :product "
        + "AND pr.relatedProduct.isPublished = TRUE AND pr.relatedProduct.isVisibleIndividually = TRUE "
        + "ORDER BY pr.relatedProduct.id ASC")
    Page<ProductRelated> findStorefrontRelated(@Param("product") Product product, Pageable pageable);
}
