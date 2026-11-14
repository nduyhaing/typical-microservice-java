package com.shopapp.product.viewmodel.product;

import java.util.List;

/** Fields a product and its variations share in the back office create/update bodies (YAS). */
public interface ProductProperties {
    Long id();

    String name();

    String slug();

    String sku();

    String gtin();

    Double price();

    Long thumbnailMediaId();

    List<Long> productImageIds();
}
