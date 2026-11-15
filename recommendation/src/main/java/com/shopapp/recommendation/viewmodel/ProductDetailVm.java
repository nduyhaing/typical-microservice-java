package com.shopapp.recommendation.viewmodel;

/**
 * The part of product's storefront detail ({@code GET /storefront/product/{slug}}, {@code ProductDetailGetVm}) that
 * the products-es view lacks: the short description (YAS reads {@code /storefront/products/detail/{id}}, which
 * shop_app's product does not have).
 */
public record ProductDetailVm(Long id, String name, String shortDescription) {
}
