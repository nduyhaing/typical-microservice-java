package com.shopapp.search.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shopapp.search.kafka.ProductCdcEvent.Action;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class ProductCdcEventTest {

    private final ObjectMapper mapper = JsonMapper.builder().build();

    private ProductCdcEvent parse(String key, String value) {
        return ProductCdcEvent.parse(mapper, key, value);
    }

    @Test
    void createSnapshotAndUpdate_sync() {
        assertThat(parse("{\"id\":7}", "{\"before\":null,\"after\":{\"id\":7},\"op\":\"c\"}"))
            .isEqualTo(new ProductCdcEvent(7, Action.SYNC));
        assertThat(parse("{\"id\":7}", "{\"before\":null,\"after\":{\"id\":7},\"op\":\"r\"}").action())
            .isEqualTo(Action.SYNC);
        assertThat(parse("{\"id\":7}", "{\"before\":null,\"after\":{\"id\":7,\"name\":\"A\"},\"op\":\"u\"}")
            .action()).isEqualTo(Action.SYNC);
    }

    @Test
    void deleteAndTombstone_delete() {
        assertThat(parse("{\"id\":7}", "{\"before\":{\"id\":7},\"after\":null,\"op\":\"d\"}"))
            .isEqualTo(new ProductCdcEvent(7, Action.DELETE));
        assertThat(parse("{\"id\":7}", null)).isEqualTo(new ProductCdcEvent(7, Action.DELETE));
    }

    @Test
    void stockOnlyUpdateWithBothImages_skipped() {
        String value = "{\"before\":{\"id\":7,\"name\":\"A\",\"stock_quantity\":5,\"last_modified_on\":1},"
            + "\"after\":{\"id\":7,\"name\":\"A\",\"stock_quantity\":4,\"last_modified_on\":2},\"op\":\"u\"}";
        assertThat(parse("{\"id\":7}", value).action()).isEqualTo(Action.SKIP);
    }

    @Test
    void updateChangingAnIndexedColumn_sync() {
        String value = "{\"before\":{\"id\":7,\"name\":\"A\",\"stock_quantity\":5},"
            + "\"after\":{\"id\":7,\"name\":\"B\",\"stock_quantity\":4},\"op\":\"u\"}";
        assertThat(parse("{\"id\":7}", value).action()).isEqualTo(Action.SYNC);
    }

    @Test
    void schemaEnvelope_isUnwrapped() {
        assertThat(parse("{\"schema\":{},\"payload\":{\"id\":9}}",
            "{\"schema\":{},\"payload\":{\"before\":null,\"after\":{\"id\":9},\"op\":\"c\"}}"))
            .isEqualTo(new ProductCdcEvent(9, Action.SYNC));
    }

    @Test
    void unknownOperation_ignored() {
        assertThat(parse("{\"id\":7}", "{\"op\":\"t\"}").action()).isEqualTo(Action.IGNORE);
    }

    @Test
    void malformedMessages_arePoison() {
        assertThatThrownBy(() -> parse("{\"id\":7}", "not json")).isInstanceOf(PoisonMessageException.class);
        assertThatThrownBy(() -> parse("{\"id\":7}", "[1,2]")).isInstanceOf(PoisonMessageException.class);
        assertThatThrownBy(() -> parse("{\"id\":7}", "{\"after\":{}}")).isInstanceOf(PoisonMessageException.class);
        assertThatThrownBy(() -> parse(null, "{\"op\":\"c\"}")).isInstanceOf(PoisonMessageException.class);
        assertThatThrownBy(() -> parse("{\"id\":\"x\"}", "{\"op\":\"c\"}"))
            .isInstanceOf(PoisonMessageException.class);
        assertThatThrownBy(() -> parse("{\"id\":-1}", "{\"op\":\"c\"}")).isInstanceOf(PoisonMessageException.class);
        assertThatThrownBy(() -> parse("garbage", "{\"op\":\"c\"}")).isInstanceOf(PoisonMessageException.class);
    }
}
