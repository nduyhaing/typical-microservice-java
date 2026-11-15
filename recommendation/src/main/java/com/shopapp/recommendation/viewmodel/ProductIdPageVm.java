package com.shopapp.recommendation.viewmodel;

import java.util.List;

/** The part of product's back office list page ({@code ProductListGetVm}) the reindex needs. */
public record ProductIdPageVm(List<Item> productContent, boolean isLast) {

    /** One row: only the id is read. */
    public record Item(Long id) {
    }
}
