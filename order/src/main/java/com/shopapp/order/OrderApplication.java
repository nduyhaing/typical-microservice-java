package com.shopapp.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(scanBasePackages = {"com.shopapp.order", "com.shopapp.commonlibrary"})
@ConfigurationPropertiesScan(basePackages = {"com.shopapp.order", "com.shopapp.commonlibrary"})
public class OrderApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderApplication.class, args);
    }
}
