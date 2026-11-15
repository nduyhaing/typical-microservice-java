package com.shopapp.search.viewmodel;

/** What {@code POST /backoffice/reindex} did: documents written and documents removed. */
public record ReindexResultVm(int indexed, int removed) {
}
