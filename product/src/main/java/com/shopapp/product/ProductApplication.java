package com.shopapp.product;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(scanBasePackages = {"com.shopapp.product", "com.shopapp.commonlibrary"})
@ConfigurationPropertiesScan(basePackages = {"com.shopapp.product", "com.shopapp.commonlibrary"})
public class ProductApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProductApplication.class, args);
    }
}
