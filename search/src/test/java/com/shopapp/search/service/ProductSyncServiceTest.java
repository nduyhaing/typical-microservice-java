package com.shopapp.search.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.shopapp.search.viewmodel.ProductEsDetailVm;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProductSyncServiceTest {

    private static ProductEsDetailVm vm(Instant created, Instant modified) {
        return new ProductEsDetailVm(1L, "A", "a", 1.0, true, true, true, false, null, "", null, List.of(),
            List.of(), created, modified);
    }

    @Test
    void version_isLastModifiedInMicros_elseCreated_elseZero() {
        Instant created = Instant.parse("2026-01-01T00:00:00Z");
        Instant modified = Instant.parse("2026-01-01T00:00:01.000002Z");
        assertThat(ProductSyncService.versionOf(vm(created, modified)))
            .isEqualTo(created.getEpochSecond() * 1_000_000 + 1_000_002);
        assertThat(ProductSyncService.versionOf(vm(created, null))).isEqualTo(created.getEpochSecond() * 1_000_000);
        assertThat(ProductSyncService.versionOf(vm(null, null))).isZero();
    }
}
