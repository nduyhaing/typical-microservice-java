package com.shopapp.commonlibrary.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

class CorsConfigTest {

    static class InspectableCorsRegistry extends CorsRegistry {
        Map<String, CorsConfiguration> configurations() {
            return getCorsConfigurations();
        }
    }

    private static CorsConfiguration corsFor(String allowedOrigins) {
        InspectableCorsRegistry registry = new InspectableCorsRegistry();
        new CorsConfig(allowedOrigins).corsConfigurer().addCorsMappings(registry);
        return registry.configurations().get("/**");
    }

    @Test
    void cors_allowsConfiguredOrigin() {
        assertThat(corsFor("http://storefront.shopapp.local").checkOrigin("http://storefront.shopapp.local"))
            .isEqualTo("http://storefront.shopapp.local");
    }

    @Test
    void cors_rejectsOtherOrigin() {
        assertThat(corsFor("http://storefront.shopapp.local").checkOrigin("http://evil.test")).isNull();
    }

    @Test
    void cors_acceptsCommaSeparatedOrigins() {
        CorsConfiguration cors = corsFor("http://a.shopapp.local, http://b.shopapp.local");
        assertThat(cors.checkOrigin("http://b.shopapp.local")).isEqualTo("http://b.shopapp.local");
    }

    @Test
    void cors_allowsAnyMethodAndHeader() {
        CorsConfiguration cors = corsFor("http://storefront.shopapp.local");
        assertThat(cors.getAllowedMethods()).containsExactly("*");
        assertThat(cors.getAllowedHeaders()).containsExactly("*");
    }
}
