package com.shopapp.commonlibrary.utils;

import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import org.slf4j.helpers.MessageFormatter;

public final class MessagesUtils {

    private static final ResourceBundle MESSAGE_BUNDLE = ResourceBundle.getBundle("messages.messages", Locale.ROOT);

    private MessagesUtils() {
    }

    /**
     * Resolves a message code from {@code messages/messages.properties} and fills its {@code {}} placeholders.
     * An unknown code resolves to the code itself.
     */
    public static String getMessage(String errorCode, Object... args) {
        String message;
        try {
            message = MESSAGE_BUNDLE.getString(errorCode);
        } catch (MissingResourceException ex) {
            message = errorCode;
        }
        return MessageFormatter.arrayFormat(message, args).getMessage();
    }
}
