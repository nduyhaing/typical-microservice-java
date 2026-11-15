package com.shopapp.recommendation.kafka;

import java.util.Set;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * One Debezium message of {@code dbproduct.public.product} (JSON without schema): key {@code {"id":N}}, value
 * {@code {"before":…,"after":…,"op":"c|r|u|d",…}} or null (tombstone after a delete).
 *
 * @param productId the id from the key
 * @param action    what recommendation does with it
 * @param version   the row's {@code last_modified_on} (else {@code created_on}) in microseconds from the after image,
 *                  the version a removal is written at; null when the image has none (delete, tombstone)
 */
public record ProductCdcEvent(long productId, Action action, Long version) {

    public ProductCdcEvent(long productId, Action action) {
        this(productId, action, null);
    }

    /** What the event asks for. */
    public enum Action {
        /** c, r, u of a published, individually visible row: re-read the product and embed or remove it. */
        SYNC,
        /**
         * c, r, u whose after image is unpublished or not visible individually: remove the embedding at the row's
         * version without asking product, so a product outage cannot keep it recommended (decisions.md 4-70, 5-42).
         */
        UNLIST,
        /** d or tombstone: remove the embedding. */
        DELETE,
        /** An update that only touched columns recommendation does not store (stock, audit fields). */
        SKIP,
        /** An operation recommendation does not handle (truncate, logical message). */
        IGNORE
    }

    /** Product columns whose change alone does not need a re-read (decisions.md 4-12). */
    static final Set<String> NOT_INDEXED_COLUMNS = Set.of("stock_quantity", "last_modified_on", "last_modified_by");

    /**
     * Parses the message; anything that is not a valid Debezium product message (bad JSON, no numeric id, no op)
     * is a {@link PoisonMessageException}, which goes straight to the dead-letter topic.
     */
    public static ProductCdcEvent parse(ObjectMapper mapper, String key, String value) {
        long id = parseId(mapper, key);
        if (value == null) {
            return new ProductCdcEvent(id, Action.DELETE);
        }
        JsonNode envelope = readTree(mapper, value, "value");
        JsonNode payload = envelope.has("payload") && envelope.get("payload").isObject()
            ? envelope.get("payload") : envelope;
        JsonNode op = payload.get("op");
        if (op == null || !op.isString()) {
            throw new PoisonMessageException("CDC message for product " + id + " has no op");
        }
        return switch (op.asString()) {
            case "c", "r" -> rowEvent(id, payload.get("after"), false);
            case "u" -> rowEvent(id, payload.get("after"), onlyNotIndexedColumnsChanged(payload));
            case "d" -> new ProductCdcEvent(id, Action.DELETE);
            default -> new ProductCdcEvent(id, Action.IGNORE);
        };
    }

    private static ProductCdcEvent rowEvent(long id, JsonNode after, boolean onlyNotIndexedColumns) {
        Long version = rowVersion(after);
        if (after != null && after.isObject()
            && (isFalse(after, "is_published") || isFalse(after, "is_visible_individually"))) {
            return new ProductCdcEvent(id, Action.UNLIST, version);
        }
        return new ProductCdcEvent(id, onlyNotIndexedColumns ? Action.SKIP : Action.SYNC, version);
    }

    private static boolean isFalse(JsonNode image, String column) {
        JsonNode value = image.get(column);
        return value != null && value.isBoolean() && !value.asBoolean();
    }

    /**
     * {@code last_modified_on}, else {@code created_on}: Debezium sends {@code timestamp(6)} as microseconds since
     * the epoch (time.precision.mode adaptive, MicroTimestamp). Product writes them in UTC, as its products-es read
     * returns them, so the value equals the version a product read of the same row gets (4-13).
     */
    private static Long rowVersion(JsonNode after) {
        if (after == null || !after.isObject()) {
            return null;
        }
        for (String column : new String[] {"last_modified_on", "created_on"}) {
            JsonNode value = after.get(column);
            if (value != null && value.isIntegralNumber() && value.canConvertToLong()) {
                return value.asLong();
            }
        }
        return null;
    }

    private static long parseId(ObjectMapper mapper, String key) {
        if (key == null) {
            throw new PoisonMessageException("CDC message without key");
        }
        JsonNode node = readTree(mapper, key, "key");
        JsonNode keyPayload = node.has("payload") && node.get("payload").isObject() ? node.get("payload") : node;
        JsonNode id = keyPayload.get("id");
        if (id == null || !id.canConvertToLong() || !id.isIntegralNumber() || id.asLong() <= 0) {
            throw new PoisonMessageException("CDC key has no product id");
        }
        return id.asLong();
    }

    private static JsonNode readTree(ObjectMapper mapper, String json, String part) {
        try {
            JsonNode node = mapper.readTree(json);
            if (node == null || !node.isObject()) {
                throw new PoisonMessageException("CDC " + part + " is not a JSON object");
            }
            return node;
        } catch (JacksonException ex) {
            throw new PoisonMessageException("CDC " + part + " is not valid JSON");
        }
    }

    /**
     * True when both row images are present and differ only in {@link #NOT_INDEXED_COLUMNS}. Without a before image
     * (the default replica identity sends none) the product is always re-read.
     */
    private static boolean onlyNotIndexedColumnsChanged(JsonNode payload) {
        JsonNode before = payload.get("before");
        JsonNode after = payload.get("after");
        if (before == null || after == null || !before.isObject() || !after.isObject()) {
            return false;
        }
        ObjectNode beforeCopy = ((ObjectNode) before).deepCopy();
        ObjectNode afterCopy = ((ObjectNode) after).deepCopy();
        NOT_INDEXED_COLUMNS.forEach(column -> {
            beforeCopy.remove(column);
            afterCopy.remove(column);
        });
        return beforeCopy.equals(afterCopy);
    }
}
