package com.shopapp.search.kafka;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shopapp.search.model.Product;
import com.shopapp.search.service.ProductSyncService;
import java.util.Map;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.kafka.listener.ConsumerSeekAware.ConsumerSeekCallback;
import tools.jackson.databind.json.JsonMapper;

class EmptyIndexRefillTest {

    private final ElasticsearchOperations operations = mock(ElasticsearchOperations.class);
    private final ConsumerSeekCallback callback = mock(ConsumerSeekCallback.class);
    private final ProductCdcConsumer consumer = new ProductCdcConsumer(mock(ProductSyncService.class),
        JsonMapper.builder().build(), operations);
    private final Map<TopicPartition, Long> assigned = Map.of(new TopicPartition("dbproduct.public.product", 0), 42L);

    @Test
    void emptyIndex_rereadsTheCompactedTopicFromTheStart() {
        when(operations.count(any(Query.class), eq(Product.class))).thenReturn(0L);

        consumer.onPartitionsAssigned(assigned, callback);

        verify(callback).seekToBeginning(assigned.keySet());
    }

    @Test
    void filledIndex_keepsTheCommittedOffset() {
        when(operations.count(any(Query.class), eq(Product.class))).thenReturn(3L);

        consumer.onPartitionsAssigned(assigned, callback);

        verify(callback, never()).seekToBeginning(any());
    }

    @Test
    void elasticsearchDown_keepsTheCommittedOffset() {
        when(operations.count(any(Query.class), eq(Product.class))).thenThrow(new IllegalStateException("down"));

        consumer.onPartitionsAssigned(assigned, callback);

        verify(callback, never()).seekToBeginning(any());
    }

    @Test
    void onlyTheFirstAssignmentRefills() {
        when(operations.count(any(Query.class), eq(Product.class))).thenReturn(0L);

        consumer.onPartitionsAssigned(assigned, callback);
        consumer.onPartitionsAssigned(assigned, callback);

        verify(callback, org.mockito.Mockito.times(1)).seekToBeginning(assigned.keySet());
    }
}
