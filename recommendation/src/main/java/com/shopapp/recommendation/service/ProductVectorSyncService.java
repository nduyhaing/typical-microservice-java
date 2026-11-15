package com.shopapp.recommendation.service;

import com.shopapp.recommendation.repository.ProductEmbeddingRepository;
import com.shopapp.recommendation.repository.ProductEmbeddingRepository.EmbeddedProduct;
import com.shopapp.recommendation.viewmodel.ProductDetailVm;
import com.shopapp.recommendation.viewmodel.ProductEsDetailVm;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

/**
 * Brings one product's embedding in line with product's current state (YAS ProductVectorSyncService). Every event
 * re-reads the product, so duplicates and reordering converge; rows carry product's {@code lastModifiedOn} in
 * microseconds and are only replaced by an equal or newer version (decisions.md 5-41).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductVectorSyncService {

    /** What happened to the row. */
    public enum Outcome { EMBEDDED, REMOVED, STALE }

    private final ProductClient productClient;
    private final ProductEmbeddingRepository repository;
    private final EmbeddingModel embeddingModel;

    /**
     * Re-reads the product and embeds it, or removes it when product says 404 (deleted, unpublished, hidden) or it
     * is not published and visible individually. Product unavailable → {@link ProductUnavailableException}.
     *
     * @param eventVersion the version of the row image that triggered the read (CDC after image, µs), or null
     *                     (reindex): a 404 removes the row at that version, so a 404 read before a republish cannot
     *                     remove the republished product embedded meanwhile (search 4-71); null removes it "now"
     */
    public Outcome sync(long id, Long eventVersion) {
        Optional<ProductEsDetailVm> summary = productClient.getEsDetail(id);
        if (summary.isEmpty()) {
            return eventVersion == null ? delete(id) : remove(id, eventVersion);
        }
        ProductEsDetailVm vm = summary.get();
        long version = versionOf(vm);
        if (!vm.isPublished() || !vm.isVisibleIndividually()) {
            return remove(id, version);
        }
        Optional<Long> stored = repository.findVersion(id);
        if (stored.isPresent() && stored.get() > version) {
            log.info("Product {} version {} is older than the stored one; skipped", id, version);
            return Outcome.STALE;
        }
        Optional<ProductDetailVm> detail = productClient.getDetail(vm.slug());
        if (detail.isEmpty()) {
            return remove(id, version);
        }
        String content = ProductDocumentFormatter.format(vm, detail.get());
        float[] embedding = embeddingModel.embed(content);
        boolean written = repository.upsert(new EmbeddedProduct(id, version, vm.name(), vm.slug(), vm.price(),
            vm.brand(), vm.thumbnailUrl(), content, embedding));
        return written ? Outcome.EMBEDDED : Outcome.STALE;
    }

    /**
     * Removes the product after a hard delete or a product 404: the tombstone is versioned "now", so a read of the
     * product that started before cannot bring it back.
     */
    public Outcome delete(long id) {
        return remove(id, ChronoUnit.MICROS.between(Instant.EPOCH, Instant.now()));
    }

    /**
     * The CDC after image says unpublished or hidden: removes the row at that image's version without asking product
     * (a product outage cannot keep it recommended); without a version, "now".
     */
    public Outcome unlist(long id, Long version) {
        return version == null ? delete(id) : remove(id, version);
    }

    private Outcome remove(long id, long version) {
        return repository.tombstone(id, version) ? Outcome.REMOVED : Outcome.STALE;
    }

    static long versionOf(ProductEsDetailVm vm) {
        Instant modified = vm.lastModifiedOn() != null ? vm.lastModifiedOn() : vm.createdOn();
        return modified == null ? 0 : ChronoUnit.MICROS.between(Instant.EPOCH, modified);
    }
}
