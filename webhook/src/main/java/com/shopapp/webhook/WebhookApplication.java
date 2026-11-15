package com.shopapp.webhook;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"com.shopapp.webhook", "com.shopapp.commonlibrary"})
@ConfigurationPropertiesScan(basePackages = {"com.shopapp.webhook", "com.shopapp.commonlibrary"})
@EnableScheduling
public class WebhookApplication {

    public static void main(String[] args) {
        SpringApplication.run(WebhookApplication.class, args);
    }
}
