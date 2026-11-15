package com.shopapp.recommendation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shopapp.recommendation.repository.ProductEmbeddingRepository;
import com.shopapp.recommendation.viewmodel.ProductIdPageVm;
import com.shopapp.recommendation.viewmodel.ReindexResultVm;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;

/** The reindex removes a product product no longer shows at the product's own version, not "now" (5-70). */
class ReindexServiceTest {

    private static final Instant MODIFIED = Instant.parse("2026-11-15T10:00:00Z");
    private static final Instant CREATED = Instant.parse("2026-11-01T08:00:00Z");

    private final ProductClient client = mock(ProductClient.class);
    private final ProductEmbeddingRepository repository = mock(ProductEmbeddingRepository.class);
    private final ProductVectorSyncService sync = new ProductVectorSyncService(client, repository,
        mock(EmbeddingModel.class));
    private final ReindexService reindex = new ReindexService(client, sync);

    @Test
    void productNoLongerShown_isRemovedAtItsOwnLastModifiedVersion() {
        when(client.listProducts(0, ReindexService.PAGE_SIZE, "admin-token")).thenReturn(new ProductIdPageVm(List.of(
            new ProductIdPageVm.Item(7L, CREATED, MODIFIED), new ProductIdPageVm.Item(8L, CREATED, null)), true));
        when(client.getEsDetail(anyLong())).thenReturn(Optional.empty());
        when(repository.tombstone(anyLong(), anyLong())).thenReturn(true);

        ReindexResultVm result = reindex.reindex("admin-token");

        assertThat(result.removed()).isEqualTo(2);
        verify(repository).tombstone(eq(7L), eq(ChronoUnit.MICROS.between(Instant.EPOCH, MODIFIED)));
        // never modified: its creation time
        verify(repository).tombstone(eq(8L), eq(ChronoUnit.MICROS.between(Instant.EPOCH, CREATED)));
    }
}
