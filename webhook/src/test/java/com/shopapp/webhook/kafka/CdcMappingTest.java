package com.shopapp.webhook.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shopapp.webhook.model.EventName;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class CdcMappingTest {

    private final ObjectMapper mapper = JsonMapper.builder().build();

    private CdcMessage parse(String key, String value) {
        return CdcMessage.parse(mapper, key, value);
    }

    @Test
    void parse_readsKeyOpAndImages_withOrWithoutTheSchemaEnvelope() {
        CdcMessage plain = parse("{\"id\":7}", "{\"before\":null,\"after\":{\"id\":7,\"name\":\"A\"},\"op\":\"u\"}");
        assertThat(plain.id()).isEqualTo(7);
        assertThat(plain.op()).isEqualTo("u");
        assertThat(plain.before()).isNull();
        assertThat(plain.after().get("name").asString()).isEqualTo("A");

        CdcMessage enveloped = parse("{\"payload\":{\"id\":8}}",
            "{\"schema\":{},\"payload\":{\"before\":{\"id\":8},\"after\":{\"id\":8},\"op\":\"c\"}}");
        assertThat(enveloped.id()).isEqualTo(8);
        assertThat(enveloped.op()).isEqualTo("c");
        assertThat(enveloped.before()).isNotNull();
    }

    @Test
    void tombstone_isADeleteWithoutImages() {
        CdcMessage tombstone = parse("{\"id\":7}", null);
        assertThat(tombstone.op()).isEqualTo("d");
        assertThat(tombstone.after()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"not json", "[1,2]", "{\"before\":null,\"after\":{}}", "{\"op\":5}"})
    void invalidValues_arePoison(String value) {
        assertThatThrownBy(() -> parse("{\"id\":1}", value)).isInstanceOf(PoisonMessageException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"nope", "{}", "{\"id\":\"x\"}", "{\"id\":0}", "{\"id\":1.5}"})
    void invalidKeys_arePoison(String key) {
        assertThatThrownBy(() -> parse(key, "{\"op\":\"u\",\"after\":{}}"))
            .isInstanceOf(PoisonMessageException.class);
    }

    @Test
    void nullKey_isPoison() {
        assertThatThrownBy(() -> parse(null, "{\"op\":\"u\",\"after\":{}}"))
            .isInstanceOf(PoisonMessageException.class);
    }

    @Test
    void productUpdate_becomesOnProductUpdated_withTheCatalogColumns() {
        CdcMessage message = parse("{\"id\":5}", "{\"before\":null,\"after\":{\"id\":5,\"name\":\"Galaxy\","
            + "\"slug\":\"galaxy\",\"sku\":\"G-1\",\"gtin\":null,\"price\":\"799.0\",\"is_published\":true,"
            + "\"is_featured\":false,\"is_visible_individually\":true,\"stock_quantity\":4,\"brand_id\":2,"
            + "\"description\":\"long text\",\"last_modified_on\":1794700800000000},\"op\":\"u\"}");

        Optional<ObjectNode> data = CdcMapping.productUpdated(mapper, message);

        assertThat(data).isPresent();
        ObjectNode node = data.get();
        assertThat(node.get("productId").asLong()).isEqualTo(5);
        assertThat(node.get("name").asString()).isEqualTo("Galaxy");
        assertThat(node.get("slug").asString()).isEqualTo("galaxy");
        assertThat(node.get("price").asString()).isEqualTo("799.0");
        assertThat(node.get("isPublished").asBoolean()).isTrue();
        assertThat(node.get("stockQuantity").asLong()).isEqualTo(4);
        assertThat(node.get("lastModifiedOn").asString()).isEqualTo("2026-11-15T00:00:00Z");
        assertThat(node.has("description")).isFalse();
        assertThat(node.get("gtin").isNull()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"c", "r", "d"})
    void otherProductOperations_areIgnored(String op) {
        CdcMessage message = parse("{\"id\":5}", "{\"before\":null,\"after\":{\"id\":5},\"op\":\"" + op + "\"}");
        assertThat(CdcMapping.productUpdated(mapper, message)).isEmpty();
    }

    @Test
    void orderCreate_isOnOrderCreated() {
        CdcMessage message = parse("{\"id\":9}", order("c", null, "PENDING"));

        CdcMapping.OrderChange change = CdcMapping.orderChange(message, null);

        assertThat(change.event()).isEqualTo(EventName.ON_ORDER_CREATED);
        assertThat(change.status()).isEqualTo("PENDING");
        ObjectNode data = CdcMapping.orderData(mapper, message, change);
        assertThat(data.get("orderId").asLong()).isEqualTo(9);
        assertThat(data.get("status").asString()).isEqualTo("PENDING");
        assertThat(data.get("checkoutId").asString()).isEqualTo("chk-9");
        assertThat(data.get("totalAmount").asString()).isEqualTo("120.50");
        assertThat(data.has("email")).isFalse();
        assertThat(data.has("customerId")).isFalse();
        assertThat(data.has("previousStatus")).isFalse();
    }

    @Test
    void orderUpdate_withABeforeImage_isAStatusUpdateOnlyWhenTheStatusChanged() {
        CdcMessage changed = parse("{\"id\":9}", order("u", "PENDING", "PAID"));
        CdcMapping.OrderChange change = CdcMapping.orderChange(changed, "SOMETHING_ELSE");
        assertThat(change.event()).isEqualTo(EventName.ON_ORDER_STATUS_UPDATED);
        assertThat(change.previousStatus()).isEqualTo("PENDING");
        assertThat(CdcMapping.orderData(mapper, changed, change).get("previousStatus").asString())
            .isEqualTo("PENDING");

        CdcMessage same = parse("{\"id\":9}", order("u", "PAID", "PAID"));
        assertThat(CdcMapping.orderChange(same, null).event()).isNull();
    }

    @Test
    void orderUpdate_withoutABeforeImage_comparesWithTheLastSeenStatus() {
        CdcMessage update = parse("{\"id\":9}", order("u", null, "SHIPPING"));

        assertThat(CdcMapping.orderChange(update, "SHIPPING").event()).isNull();
        CdcMapping.OrderChange fromSeen = CdcMapping.orderChange(update, "PAID");
        assertThat(fromSeen.event()).isEqualTo(EventName.ON_ORDER_STATUS_UPDATED);
        assertThat(fromSeen.previousStatus()).isEqualTo("PAID");
        // never seen (e.g. created before webhook started): reported, previous status unknown
        CdcMapping.OrderChange unknown = CdcMapping.orderChange(update, null);
        assertThat(unknown.event()).isEqualTo(EventName.ON_ORDER_STATUS_UPDATED);
        assertThat(unknown.previousStatus()).isNull();
    }

    @Test
    void orderSnapshotAndDelete_sendNothing_butSnapshotRecordsTheStatus() {
        CdcMapping.OrderChange snapshot = CdcMapping.orderChange(parse("{\"id\":9}", order("r", null, "PAID")), null);
        assertThat(snapshot.event()).isNull();
        assertThat(snapshot.status()).isEqualTo("PAID");
        assertThat(CdcMapping.orderChange(parse("{\"id\":9}", null), "PAID").event()).isNull();
    }

    private static String order(String op, String beforeStatus, String afterStatus) {
        String before = beforeStatus == null ? "null" : "{\"id\":9,\"status\":\"" + beforeStatus + "\"}";
        return "{\"before\":" + before + ",\"after\":{\"id\":9,\"status\":\"" + afterStatus + "\","
            + "\"checkout_id\":\"chk-9\",\"email\":\"a@b.c\",\"customer_id\":\"u-1\",\"total_amount\":\"120.50\","
            + "\"payment_status\":\"PENDING\",\"number_item\":2,\"created_on\":1794700800000000},\"op\":\"" + op
            + "\"}";
    }
}
