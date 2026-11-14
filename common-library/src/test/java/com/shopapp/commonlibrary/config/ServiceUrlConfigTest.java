package com.shopapp.commonlibrary.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ServiceUrlConfigTest {

    @EnableConfigurationProperties(ServiceUrlConfig.class)
    static class TestConfiguration {
    }

    @Test
    void serviceUrls_bindFromProperties() {
        new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class)
            .withPropertyValues("shopapp.services.media=http://media", "shopapp.services.product=http://product")
            .run(context -> {
                ServiceUrlConfig config = context.getBean(ServiceUrlConfig.class);
                assertThat(config.media()).isEqualTo("http://media");
                assertThat(config.product()).isEqualTo("http://product");
            });
    }
}
