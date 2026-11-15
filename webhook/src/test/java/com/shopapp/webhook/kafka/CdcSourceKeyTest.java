package com.shopapp.webhook.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.record.TimestampType;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** The delivery log's dedup key is the database change, not the Kafka offset (decisions.md 5-64). */
class CdcSourceKeyTest {

    private final ObjectMapper mapper = JsonMapper.builder().build();

    private static ConsumerRecord<String, String> record(long offset, long timestamp, String value) {
        return new ConsumerRecord<>("dborder.public.order", 0, offset, timestamp, TimestampType.CREATE_TIME, 0, 0,
            "{\"id\":21}", value, new RecordHeaders(), java.util.Optional.empty());
    }

    private static String event(long lsn, long txId) {
        return "{\"before\":null,\"after\":{\"id\":21,\"status\":\"PAID\"},\"op\":\"u\",\"source\":{\"lsn\":" + lsn
            + ",\"txId\":" + txId + ",\"schema\":\"public\",\"table\":\"order\",\"ts_ms\":1}}";
    }

    private String key(ConsumerRecord<String, String> record) {
        return CdcConsumers.sourceKey(record, CdcMessage.parse(mapper, record.key(), record.value()));
    }

    @Test
    void sameOffsetAfterATopicReset_withAnotherLsn_isANewKey() {
        String before = key(record(77, 1000, event(24_000_100L, 900)));
        String after = key(record(77, 5000, event(25_000_200L, 950)));

        assertThat(after).isNotEqualTo(before);
        assertThat(before).isEqualTo("lsn:24000100:tx:900:public.order:21");
    }

    @Test
    void theSameChangeReadAgainFromAnotherOffset_isTheSameKey() {
        assertThat(key(record(77, 1000, event(24_000_100L, 900))))
            .isEqualTo(key(record(1203, 9000, event(24_000_100L, 900))));
    }

    @Test
    void twoChangesOfOneRowInOneTransaction_haveDifferentKeys() {
        assertThat(key(record(1, 1000, event(24_000_100L, 900))))
            .isNotEqualTo(key(record(2, 1000, event(24_000_180L, 900))));
    }

    @Test
    void withoutASourceBlock_theTimestampAndOffsetAreUsed() {
        assertThat(key(record(77, 1000, null))).isEqualTo("ts:1000:dborder.public.order-0@77");
        assertThat(key(record(77, 1000, "{\"before\":null,\"after\":{\"id\":21},\"op\":\"u\"}")))
            .isEqualTo("ts:1000:dborder.public.order-0@77");
    }
}
