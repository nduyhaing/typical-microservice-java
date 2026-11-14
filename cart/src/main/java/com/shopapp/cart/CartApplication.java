package com.shopapp.cart;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(scanBasePackages = {"com.shopapp.cart", "com.shopapp.commonlibrary"})
@ConfigurationPropertiesScan(basePackages = {"com.shopapp.cart", "com.shopapp.commonlibrary"})
public class CartApplication {

    public static void main(String[] args) {
        SpringApplication.run(CartApplication.class, args);
    }
}
