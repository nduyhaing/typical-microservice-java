package com.shopapp.commonlibrary.utils;

import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.stream.Stream;
import org.slf4j.helpers.MessageFormatter;

public final class MessagesUtils {

    /** The service's own codes ({@code messages/messages.properties}, optional) win over the library's. */
    private static final List<ResourceBundle> BUNDLES = Stream.of("messages.messages", "messages.common-messages")
        .map(MessagesUtils::loadBundle)
        .flatMap(Optional::stream)
        .toList();

    private MessagesUtils() {
    }

    /**
     * Resolves a message code and fills its {@code {}} placeholders. An unknown code resolves to the code itself.
     */
    public static String getMessage(String errorCode, Object... args) {
        String message = BUNDLES.stream()
            .filter(bundle -> bundle.containsKey(errorCode))
            .map(bundle -> bundle.getString(errorCode))
            .findFirst()
            .orElse(errorCode);
        return MessageFormatter.arrayFormat(message, args).getMessage();
    }

    private static Optional<ResourceBundle> loadBundle(String baseName) {
        try {
            return Optional.of(ResourceBundle.getBundle(baseName, Locale.ROOT));
        } catch (MissingResourceException ex) {
            return Optional.empty();
        }
    }
}
