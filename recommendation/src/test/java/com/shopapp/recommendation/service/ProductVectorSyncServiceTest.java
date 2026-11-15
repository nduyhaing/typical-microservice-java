package com.shopapp.recommendation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shopapp.recommendation.repository.ProductEmbeddingRepository;
import com.shopapp.recommendation.repository.ProductEmbeddingRepository.EmbeddedProduct;
import com.shopapp.recommendation.service.ProductVectorSyncService.Outcome;
import com.shopapp.recommendation.viewmodel.ProductDetailVm;
import com.shopapp.recommendation.viewmodel.ProductEsDetailVm;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.embedding.EmbeddingModel;

class ProductVectorSyncServiceTest {

    private static final Instant MODIFIED = Instant.parse("2026-11-15T10:00:00Z");
    private static final long VERSION = ChronoUnit.MICROS.between(Instant.EPOCH, MODIFIED);

    private final ProductClient client = mock(ProductClient.class);
    private final ProductEmbeddingRepository repository = mock(ProductEmbeddingRepository.class);
    private final EmbeddingModel model = mock(EmbeddingModel.class);
    private final ProductVectorSyncService service = new ProductVectorSyncService(client, repository, model);

    private static ProductEsDetailVm summary(boolean published, boolean visible) {
        return new ProductEsDetailVm(7L, "Galaxy S24", "galaxy-s24", 799.0, published, visible, true, false, 3L,
            "http://media/3.png", "Samsung", List.of("Phones"), List.of(), MODIFIED.minusSeconds(60), MODIFIED);
    }

    private static ProductDetailVm detail() {
        return new ProductDetailVm(7L, "Galaxy S24", "Flagship phone");
    }

    @Test
    void sync_publishedProduct_embedsDocumentTextAndUpserts() {
        when(client.getEsDetail(7)).thenReturn(Optional.of(summary(true, true)));
        when(client.getDetail("galaxy-s24")).thenReturn(Optional.of(detail()));
        when(repository.findVersion(7)).thenReturn(Optional.empty());
        float[] vector = {0.1f, 0.2f};
        when(model.embed("Phones. Galaxy S24. Brand: Samsung. Categories: Phones. Flagship phone. Phones"))
            .thenReturn(vector);
        when(repository.upsert(any())).thenReturn(true);

        assertThat(service.sync(7, null)).isEqualTo(Outcome.EMBEDDED);

        ArgumentCaptor<EmbeddedProduct> row = ArgumentCaptor.forClass(EmbeddedProduct.class);
        verify(repository).upsert(row.capture());
        assertThat(row.getValue()).satisfies(product -> {
            assertThat(product.id()).isEqualTo(7);
            assertThat(product.version()).isEqualTo(VERSION);
            assertThat(product.slug()).isEqualTo("galaxy-s24");
            assertThat(product.price()).isEqualTo(799.0);
            assertThat(product.brand()).isEqualTo("Samsung");
            assertThat(product.thumbnailUrl()).isEqualTo("http://media/3.png");
            assertThat(product.embedding()).isSameAs(vector);
        });
    }

    @Test
    void sync_product404_tombstonesWithTimeOfDelete() {
        when(client.getEsDetail(7)).thenReturn(Optional.empty());
        when(repository.tombstone(eq(7L), anyLong())).thenReturn(true);
        long before = ChronoUnit.MICROS.between(Instant.EPOCH, Instant.now());

        assertThat(service.sync(7, null)).isEqualTo(Outcome.REMOVED);

        ArgumentCaptor<Long> version = ArgumentCaptor.forClass(Long.class);
        verify(repository).tombstone(eq(7L), version.capture());
        assertThat(version.getValue()).isGreaterThanOrEqualTo(before);
        verify(model, never()).embed(anyString());
    }

    @Test
    void sync_notPublishedOrHidden_tombstonesWithProductVersion() {
        when(client.getEsDetail(7)).thenReturn(Optional.of(summary(false, true)));
        when(repository.tombstone(7, VERSION)).thenReturn(true);

        assertThat(service.sync(7, null)).isEqualTo(Outcome.REMOVED);

        when(client.getEsDetail(8)).thenReturn(Optional.of(summary(true, false)));
        when(repository.tombstone(8, VERSION)).thenReturn(true);
        assertThat(service.sync(8, null)).isEqualTo(Outcome.REMOVED);
        verify(model, never()).embed(anyString());
    }

    @Test
    void sync_newerVersionStored_skipsWithoutEmbedding() {
        when(client.getEsDetail(7)).thenReturn(Optional.of(summary(true, true)));
        when(repository.findVersion(7)).thenReturn(Optional.of(VERSION + 1));

        assertThat(service.sync(7, null)).isEqualTo(Outcome.STALE);

        verify(model, never()).embed(anyString());
        verify(repository, never()).upsert(any());
    }

    @Test
    void sync_detailGoneMeanwhile_tombstonesAtTheReadVersion() {
        when(client.getEsDetail(7)).thenReturn(Optional.of(summary(true, true)));
        when(repository.findVersion(7)).thenReturn(Optional.empty());
        when(client.getDetail("galaxy-s24")).thenReturn(Optional.empty());
        when(repository.tombstone(7, VERSION)).thenReturn(true);

        assertThat(service.sync(7, null)).isEqualTo(Outcome.REMOVED);
        verify(model, never()).embed(anyString());
    }

    @Test
    void sync_product404ForACdcEvent_tombstonesAtTheEventVersion() {
        when(client.getEsDetail(7)).thenReturn(Optional.empty());
        when(repository.tombstone(7, 1234L)).thenReturn(false);

        assertThat(service.sync(7, 1234L)).isEqualTo(Outcome.STALE);
        verify(repository).tombstone(7, 1234L);
    }

    @Test
    void unlist_tombstonesAtTheImageVersion_withoutCallingProduct() {
        when(repository.tombstone(7, 99L)).thenReturn(true);

        assertThat(service.unlist(7, 99L)).isEqualTo(Outcome.REMOVED);
        verify(client, never()).getEsDetail(anyLong());
    }

    @Test
    void unlistWithoutVersion_tombstonesNow() {
        long before = ChronoUnit.MICROS.between(Instant.EPOCH, Instant.now());
        when(repository.tombstone(eq(7L), anyLong())).thenReturn(true);

        assertThat(service.unlist(7, null)).isEqualTo(Outcome.REMOVED);
        ArgumentCaptor<Long> version = ArgumentCaptor.forClass(Long.class);
        verify(repository).tombstone(eq(7L), version.capture());
        assertThat(version.getValue()).isGreaterThanOrEqualTo(before);
    }

    @Test
    void sync_productUnavailable_propagatesForRetry() {
        when(client.getEsDetail(7)).thenThrow(new ProductUnavailableException("down", null));

        assertThatThrownBy(() -> service.sync(7, null)).isInstanceOf(ProductUnavailableException.class);
    }

    @Test
    void sync_upsertRefusedAsOlder_stale() {
        when(client.getEsDetail(7)).thenReturn(Optional.of(summary(true, true)));
        when(client.getDetail("galaxy-s24")).thenReturn(Optional.of(detail()));
        when(repository.findVersion(7)).thenReturn(Optional.of(VERSION));
        when(model.embed(anyString())).thenReturn(new float[] {1f});
        when(repository.upsert(any())).thenReturn(false);

        assertThat(service.sync(7, null)).isEqualTo(Outcome.STALE);
    }
}
