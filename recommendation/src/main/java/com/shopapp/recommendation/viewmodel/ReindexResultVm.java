package com.shopapp.recommendation.viewmodel;

/** What {@code POST /backoffice/reindex} did: products embedded and products removed. */
public record ReindexResultVm(int indexed, int removed) {
}
