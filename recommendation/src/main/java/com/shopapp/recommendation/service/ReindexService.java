package com.shopapp.recommendation.service;

import com.shopapp.recommendation.viewmodel.ProductIdPageVm;
import com.shopapp.recommendation.viewmodel.ReindexResultVm;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Re-reads every product from product and embeds or removes it (search's ReindexService, decisions.md 4-16). */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReindexService {

    static final int PAGE_SIZE = 100;
    /** 1 000 000 products at most; stops a product that never says isLast. */
    static final int MAX_PAGES = 10_000;

    private final ProductClient productClient;
    private final ProductVectorSyncService syncService;

    public ReindexResultVm reindex(String bearerToken) {
        int embedded = 0;
        int removed = 0;
        for (int page = 0; page < MAX_PAGES; page++) {
            ProductIdPageVm ids = productClient.listProducts(page, PAGE_SIZE, bearerToken);
            if (ids == null || ids.productContent() == null) {
                break;
            }
            for (ProductIdPageVm.Item item : ids.productContent()) {
                switch (syncService.sync(item.id(), null)) {
                    case EMBEDDED -> embedded++;
                    case REMOVED -> removed++;
                    default -> { }
                }
            }
            if (ids.isLast() || ids.productContent().isEmpty()) {
                break;
            }
        }
        log.info("Reindex done: {} embedded, {} removed", embedded, removed);
        return new ReindexResultVm(embedded, removed);
    }
}
