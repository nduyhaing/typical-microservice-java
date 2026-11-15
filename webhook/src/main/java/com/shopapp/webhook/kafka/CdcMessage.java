package com.shopapp.webhook.kafka;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * One Debezium row event (JSON, with or without the schema envelope): key {@code {"id":N}}, value
 * {@code {"before":…,"after":…,"op":"c|r|u|d",…}} or null (the tombstone after a delete, read as op {@code d}).
 *
 * @param id     the row id from the key
 * @param op     Debezium operation
 * @param before the before image, null when absent (default replica identity sends none for updates)
 * @param after  the after image, null when absent
 */
public record CdcMessage(long id, String op, JsonNode before, JsonNode after) {

    /** Parses the message; anything that is not a Debezium row event is a {@link PoisonMessageException}. */
    public static CdcMessage parse(ObjectMapper mapper, String key, String value) {
        long id = parseId(mapper, key);
        if (value == null) {
            return new CdcMessage(id, "d", null, null);
        }
        JsonNode envelope = readObject(mapper, value, "value");
        JsonNode payload = envelope.has("payload") && envelope.get("payload").isObject()
            ? envelope.get("payload") : envelope;
        JsonNode op = payload.get("op");
        if (op == null || !op.isString()) {
            throw new PoisonMessageException("CDC message for row " + id + " has no op");
        }
        return new CdcMessage(id, op.asString(), image(payload.get("before")), image(payload.get("after")));
    }

    private static JsonNode image(JsonNode node) {
        return node != null && node.isObject() ? node : null;
    }

    private static long parseId(ObjectMapper mapper, String key) {
        if (key == null) {
            throw new PoisonMessageException("CDC message without key");
        }
        JsonNode node = readObject(mapper, key, "key");
        JsonNode keyPayload = node.has("payload") && node.get("payload").isObject() ? node.get("payload") : node;
        JsonNode id = keyPayload.get("id");
        if (id == null || !id.isIntegralNumber() || !id.canConvertToLong() || id.asLong() <= 0) {
            throw new PoisonMessageException("CDC key has no row id");
        }
        return id.asLong();
    }

    private static JsonNode readObject(ObjectMapper mapper, String json, String part) {
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
}
