package com.shopapp.location;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(scanBasePackages = {"com.shopapp.location", "com.shopapp.commonlibrary"})
@ConfigurationPropertiesScan(basePackages = {"com.shopapp.location", "com.shopapp.commonlibrary"})
public class LocationApplication {

    public static void main(String[] args) {
        SpringApplication.run(LocationApplication.class, args);
    }
}
