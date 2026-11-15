package com.shopapp.search.service;

import com.shopapp.search.viewmodel.ProductIdPageVm;
import com.shopapp.search.viewmodel.ReindexResultVm;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Re-reads every product from product and indexes or removes it, e.g. after brand or category renames (which do not
 * produce product CDC events) or after messages were dead-lettered (decisions.md 4-16). Not in YAS.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReindexService {

    static final int PAGE_SIZE = 100;
    /** 1 000 000 products at most; stops a product that never says isLast. */
    static final int MAX_PAGES = 10_000;

    private final ProductClient productClient;
    private final ProductSyncService syncService;

    public ReindexResultVm reindex(String bearerToken) {
        int indexed = 0;
        int removed = 0;
        for (int page = 0; page < MAX_PAGES; page++) {
            ProductIdPageVm ids = productClient.listProducts(page, PAGE_SIZE, bearerToken);
            if (ids == null || ids.productContent() == null) {
                break;
            }
            for (ProductIdPageVm.Item item : ids.productContent()) {
                switch (syncService.sync(item.id(), null)) {
                    case INDEXED -> indexed++;
                    case REMOVED -> removed++;
                    default -> { }
                }
            }
            if (ids.isLast() || ids.productContent().isEmpty()) {
                break;
            }
        }
        log.info("Reindex done: {} indexed, {} removed", indexed, removed);
        return new ReindexResultVm(indexed, removed);
    }
}
