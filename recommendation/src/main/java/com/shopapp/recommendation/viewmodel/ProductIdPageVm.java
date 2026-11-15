package com.shopapp.recommendation.viewmodel;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** The part of product's back office list page ({@code ProductListGetVm}) the reindex needs. */
public record ProductIdPageVm(List<Item> productContent, boolean isLast) {

    /** One row: the id and when the product last changed. */
    public record Item(Long id, Instant createdOn, Instant lastModifiedOn) {

        /**
         * The product's own version (µs of {@code lastModifiedOn}, else {@code createdOn}), the version a removal is
         * written at (decisions.md 5-70); null when product sends neither.
         */
        public Long version() {
            Instant modified = lastModifiedOn != null ? lastModifiedOn : createdOn;
            return modified == null ? null : ChronoUnit.MICROS.between(Instant.EPOCH, modified);
        }
    }
}
