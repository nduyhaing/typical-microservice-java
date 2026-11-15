package com.shopapp.webhook.config;

import com.shopapp.webhook.delivery.HostResolver;
import com.shopapp.webhook.delivery.SsrfGuard;
import com.shopapp.webhook.delivery.SystemHostResolver;
import com.shopapp.webhook.delivery.WebhookSender;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The SSRF guard and the outbound HTTP client (decisions.md 5-33). */
@Configuration(proxyBeanMethods = false)
public class DeliveryConfig {

    @Bean
    HostResolver hostResolver() {
        return new SystemHostResolver();
    }

    @Bean
    SsrfGuard ssrfGuard(HostResolver hostResolver, WebhookProperties properties) {
        return new SsrfGuard(hostResolver, properties);
    }

    @Bean(destroyMethod = "close")
    WebhookSender webhookSender(SsrfGuard ssrfGuard, WebhookProperties properties) {
        return new WebhookSender(ssrfGuard, properties);
    }
}
