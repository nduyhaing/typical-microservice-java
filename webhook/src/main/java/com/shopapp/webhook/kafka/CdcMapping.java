package com.shopapp.webhook.kafka;

import com.shopapp.webhook.model.EventName;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * CDC row events to webhook events and their {@code data} (decisions.md 5-35). YAS sent the whole after image; here
 * a fixed set of columns is sent, camel-cased: catalog fields for products, and for orders the ids, statuses and
 * totals but no personal data (email, customer id, addresses, note).
 */
public final class CdcMapping {

    /** Product column -> payload field. */
    private static final Map<String, String> PRODUCT_FIELDS = ordered(
        "name", "name", "slug", "slug", "sku", "sku", "gtin", "gtin", "price", "price",
        "is_published", "isPublished", "is_featured", "isFeatured",
        "is_visible_individually", "isVisibleIndividually", "stock_quantity", "stockQuantity",
        "brand_id", "brandId", "last_modified_on", "lastModifiedOn");

    /** Order column -> payload field. */
    private static final Map<String, String> ORDER_FIELDS = ordered(
        "checkout_id", "checkoutId", "status", "status", "payment_status", "paymentStatus",
        "shipment_status", "shipmentStatus", "total_amount", "totalAmount", "total_discount_amount",
        "totalDiscountAmount", "total_tax", "totalTax", "total_shipment_fee", "totalShipmentFee",
        "number_item", "numberItem", "created_on", "createdOn", "last_modified_on", "lastModifiedOn");

    private CdcMapping() {
    }

    /** ON_PRODUCT_UPDATED data for an update ({@code u}, YAS: any update); empty for every other operation. */
    public static Optional<ObjectNode> productUpdated(ObjectMapper mapper, CdcMessage message) {
        if (!"u".equals(message.op())) {
            return Optional.empty();
        }
        if (message.after() == null) {
            throw new PoisonMessageException("Product update " + message.id() + " has no after image");
        }
        ObjectNode data = mapper.createObjectNode();
        data.put("productId", message.id());
        copy(message.after(), data, PRODUCT_FIELDS);
        return Optional.of(data);
    }

    /**
     * What an order row event means.
     *
     * @param event          the webhook event, null when none is sent
     * @param status         the order status after the change (to remember), null when unknown
     * @param previousStatus the status before, when known
     */
    public record OrderChange(EventName event, String status, String previousStatus) {
    }

    /**
     * {@code c} -> ON_ORDER_CREATED; {@code u} -> ON_ORDER_STATUS_UPDATED when the status changed: compared with
     * the before image when Debezium sends one, else with the last status webhook saw for the order (an order never
     * seen counts as changed); {@code r} (snapshot) and {@code d} send nothing.
     *
     * @param lastSeenStatus the status recorded for this order, null when none
     */
    public static OrderChange orderChange(CdcMessage message, String lastSeenStatus) {
        String status = status(message.after());
        return switch (message.op()) {
            case "c" -> new OrderChange(EventName.ON_ORDER_CREATED, status, null);
            case "u" -> {
                if (message.after() == null) {
                    throw new PoisonMessageException("Order update " + message.id() + " has no after image");
                }
                boolean beforeHasStatus = message.before() != null && message.before().has("status");
                String previous = beforeHasStatus ? status(message.before()) : lastSeenStatus;
                boolean changed = status != null && !Objects.equals(previous, status);
                yield new OrderChange(changed ? EventName.ON_ORDER_STATUS_UPDATED : null, status, previous);
            }
            case "r" -> new OrderChange(null, status, null);
            default -> new OrderChange(null, null, null);
        };
    }

    /** The order event's data: ids, statuses and totals from the after image (+ previousStatus when known). */
    public static ObjectNode orderData(ObjectMapper mapper, CdcMessage message, OrderChange change) {
        ObjectNode data = mapper.createObjectNode();
        data.put("orderId", message.id());
        copy(message.after(), data, ORDER_FIELDS);
        if (change.event() == EventName.ON_ORDER_STATUS_UPDATED && change.previousStatus() != null) {
            data.put("previousStatus", change.previousStatus());
        }
        return data;
    }

    private static String status(JsonNode image) {
        if (image == null) {
            return null;
        }
        JsonNode status = image.get("status");
        return status != null && status.isString() ? status.asString() : null;
    }

    private static void copy(JsonNode image, ObjectNode data, Map<String, String> fields) {
        fields.forEach((column, field) -> {
            JsonNode value = image.get(column);
            if (value == null) {
                return;
            }
            if (column.endsWith("_on") && value.isIntegralNumber()) {
                // Debezium sends timestamp(6) as microseconds since the epoch
                data.put(field, Instant.EPOCH.plus(value.asLong(), ChronoUnit.MICROS).toString());
            } else {
                data.set(field, value);
            }
        });
    }

    private static Map<String, String> ordered(String... pairs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return Collections.unmodifiableMap(map);
    }
}
