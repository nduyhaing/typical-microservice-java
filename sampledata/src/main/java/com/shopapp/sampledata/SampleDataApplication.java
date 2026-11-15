package com.shopapp.sampledata;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(scanBasePackages = {"com.shopapp.sampledata", "com.shopapp.commonlibrary"})
@ConfigurationPropertiesScan(basePackages = {"com.shopapp.sampledata", "com.shopapp.commonlibrary"})
public class SampleDataApplication {

    public static void main(String[] args) {
        SpringApplication.run(SampleDataApplication.class, args);
    }
}
