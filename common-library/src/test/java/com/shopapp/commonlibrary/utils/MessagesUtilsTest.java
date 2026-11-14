package com.shopapp.commonlibrary.utils;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MessagesUtilsTest {

    @Test
    void resolvesCodeAndFillsPlaceholders() {
        assertThat(MessagesUtils.getMessage("RESOURCE_NOT_FOUND", "Product", 7))
            .isEqualTo("Resource Product with id 7 not found");
    }

    @Test
    void unknownCodeFallsBackToCode() {
        assertThat(MessagesUtils.getMessage("NO_SUCH_CODE")).isEqualTo("NO_SUCH_CODE");
    }

    @Test
    void tooFewArgsLeavesPlaceholder() {
        assertThat(MessagesUtils.getMessage("RESOURCE_NOT_FOUND", "Product"))
            .isEqualTo("Resource Product with id {} not found");
    }

    @Test
    void serviceBundleKeyResolves() {
        assertThat(MessagesUtils.getMessage("SERVICE_ONLY_KEY", "hi")).isEqualTo("Service says hi");
    }

    @Test
    void libraryKeyStillResolvesWhenServiceShipsItsOwnBundle() {
        assertThat(MessagesUtils.getMessage("SIGN_IN_REQUIRED")).isEqualTo("Authentication required");
    }
}
