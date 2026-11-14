package com.shopapp.product.viewmodel.product;

import com.shopapp.product.model.enumeration.DimensionUnit;
import com.shopapp.product.viewmodel.productoption.ProductOptionValuePostVm;
import java.util.List;

/** What product create ({@link ProductPostVm}) and update ({@link ProductPutVm}) bodies have in common (YAS). */
public interface ProductSaveVm<T extends ProductVariationSaveVm> extends ProductProperties {

    Long brandId();

    List<Long> categoryIds();

    String shortDescription();

    String description();

    String specification();

    Double weight();

    DimensionUnit dimensionUnit();

    Double length();

    Double width();

    Double height();

    Boolean isAllowedToOrder();

    Boolean isPublished();

    Boolean isFeatured();

    Boolean isVisibleIndividually();

    Boolean stockTrackingEnabled();

    String metaTitle();

    String metaKeyword();

    String metaDescription();

    List<T> variations();

    List<ProductOptionValuePostVm> productOptionValues();

    List<Long> relatedProductIds();

    Long taxClassId();

    @Override
    default Long id() {
        return null;
    }
}
