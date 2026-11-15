package com.shopapp.product.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * A result read inside a database transaction that still needs media URLs: the media ids to look up and how to
 * finish the view model from the id-to-URL map. {@code finish} must only use plain values captured inside the
 * transaction, never lazy entity state (decisions.md 3b-19).
 *
 * @param mediaIds media ids to resolve (nulls and duplicates allowed)
 * @param finish   builds the result from the URLs; read them with {@link MediaService#urlOf(Map, Long)}
 * @param <T>      the view model
 */
public record WithMedia<T>(Collection<Long> mediaIds, Function<Map<Long, String>, T> finish) {

    /** A result without images. */
    public static <T> WithMedia<T> of(T value) {
        return new WithMedia<>(List.of(), urls -> value);
    }
}
