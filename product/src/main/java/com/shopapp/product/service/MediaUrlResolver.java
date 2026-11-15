package com.shopapp.product.service;

import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Reads in a read-only transaction, then resolves media URLs after it has committed, so no database connection is
 * held while waiting for the media service (decisions.md 3b-19).
 */
@Component
public class MediaUrlResolver {

    private final TransactionTemplate readOnly;
    private final MediaService mediaService;

    public MediaUrlResolver(PlatformTransactionManager transactionManager, MediaService mediaService) {
        this.readOnly = new TransactionTemplate(transactionManager);
        this.readOnly.setReadOnly(true);
        this.mediaService = mediaService;
    }

    public <T> T readThenResolve(Supplier<WithMedia<T>> read) {
        WithMedia<T> loaded = readOnly.execute(status -> read.get());
        return loaded.finish().apply(mediaService.getMediaUrls(loaded.mediaIds()));
    }
}
