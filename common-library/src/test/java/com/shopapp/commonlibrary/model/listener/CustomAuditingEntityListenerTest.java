package com.shopapp.commonlibrary.model.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.shopapp.commonlibrary.model.AbstractAuditEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.auditing.AuditingHandler;

class CustomAuditingEntityListenerTest {

    static class Sample extends AbstractAuditEntity {
    }

    private AuditingHandler handler;
    private CustomAuditingEntityListener listener;
    private Sample entity;

    @BeforeEach
    void setUp() {
        handler = mock(AuditingHandler.class);
        listener = new CustomAuditingEntityListener(() -> handler);
        entity = new Sample();
    }

    @Test
    void create_withoutCreatedBy_delegatesToHandler() {
        listener.touchForCreate(entity);
        verify(handler).markCreated(entity);
    }

    @Test
    void create_withPresetCreatedBy_keepsItAndCopiesToLastModifiedBy() {
        entity.setCreatedBy("seed");
        listener.touchForCreate(entity);
        assertThat(entity.getCreatedBy()).isEqualTo("seed");
        assertThat(entity.getLastModifiedBy()).isEqualTo("seed");
        verify(handler, never()).markCreated(any());
    }

    @Test
    void create_withPresetBoth_keepsBoth() {
        entity.setCreatedBy("seed");
        entity.setLastModifiedBy("editor");
        listener.touchForCreate(entity);
        assertThat(entity.getCreatedBy()).isEqualTo("seed");
        assertThat(entity.getLastModifiedBy()).isEqualTo("editor");
        verify(handler, never()).markCreated(any());
    }

    @Test
    void update_withoutLastModifiedBy_delegates() {
        listener.touchForUpdate(entity);
        verify(handler).markModified(entity);
    }

    @Test
    void update_afterCreation_recordsCurrentModifier() {
        entity.setCreatedBy("creator");
        entity.setLastModifiedBy("creator");
        listener.touchForUpdate(entity);
        verify(handler).markModified(entity);
    }
}
