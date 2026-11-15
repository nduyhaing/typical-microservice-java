package com.shopapp.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.VersionType;
import com.shopapp.search.model.Product;
import com.shopapp.search.viewmodel.ProductEsDetailVm;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.VersionConflictException;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.stereotype.Service;

/**
 * Brings one product's document in line with product's current state (YAS ProductSyncDataService). Every event
 * re-reads the product, so duplicates and out-of-order events are harmless; the document version is product's
 * {@code lastModifiedOn} in microseconds ({@code external_gte}), so a slower, older read never overwrites a newer
 * one (decisions.md 4-13).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductSyncService {

    /** What happened to the document. */
    public enum Outcome { INDEXED, REMOVED, STALE }

    private final ProductClient productClient;
    private final ElasticsearchOperations operations;
    private final ElasticsearchClient client;

    /**
     * Re-reads the product and indexes it, or removes it when product says 404 (deleted, unpublished, hidden) or
     * it is not published and visible individually. Product unavailable → {@link ProductUnavailableException}.
     */
    public Outcome sync(long id) {
        Optional<ProductEsDetailVm> detail = productClient.getEsDetail(id);
        if (detail.isEmpty()) {
            return delete(id);
        }
        ProductEsDetailVm vm = detail.get();
        long version = versionOf(vm);
        if (!vm.isPublished() || !vm.isVisibleIndividually()) {
            return delete(id, version);
        }
        Product product = Product.builder()
            .id(id)
            .version(version)
            .name(vm.name())
            .slug(vm.slug())
            .price(vm.price())
            .isPublished(true)
            .isVisibleIndividually(true)
            .isAllowedToOrder(vm.isAllowedToOrder())
            .isFeatured(vm.isFeatured())
            .thumbnailMediaId(vm.thumbnailMediaId())
            .thumbnailUrl(vm.thumbnailUrl())
            .brand(vm.brand())
            .categories(vm.categories() == null ? List.of() : vm.categories())
            .attributes(vm.attributes() == null ? List.of() : vm.attributes())
            .createdOn(vm.createdOn())
            .build();
        try {
            operations.save(product);
            return Outcome.INDEXED;
        } catch (VersionConflictException ex) {
            log.info("Product {} version {} is older than the indexed one; skipped", id, version);
            return Outcome.STALE;
        }
    }

    /**
     * Removes the document after a hard delete or a product 404: versioned "now", so a read of the product that
     * started before cannot bring it back while Elasticsearch keeps the delete (index.gc_deletes, 60 s).
     */
    public Outcome delete(long id) {
        return delete(id, ChronoUnit.MICROS.between(Instant.EPOCH, Instant.now()));
    }

    /** Removes the document unless the indexed version is newer than {@code version}. */
    Outcome delete(long id, long version) {
        try {
            client.delete(d -> d.index(Product.INDEX).id(String.valueOf(id))
                .version(version).versionType(VersionType.ExternalGte));
            return Outcome.REMOVED;
        } catch (ElasticsearchException ex) {
            if (ex.status() == 404) {
                return Outcome.REMOVED;
            }
            if (ex.status() == 409) {
                log.info("Product {} delete at version {} is older than the indexed one; skipped", id, version);
                return Outcome.STALE;
            }
            throw ex;
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    static long versionOf(ProductEsDetailVm vm) {
        Instant modified = vm.lastModifiedOn() != null ? vm.lastModifiedOn() : vm.createdOn();
        return modified == null ? 0 : ChronoUnit.MICROS.between(Instant.EPOCH, modified);
    }
}
