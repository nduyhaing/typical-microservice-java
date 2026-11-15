package com.shopapp.payment.paypal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(scanBasePackages = {"com.shopapp.payment.paypal", "com.shopapp.commonlibrary"})
@ConfigurationPropertiesScan(basePackages = {"com.shopapp.payment.paypal", "com.shopapp.commonlibrary"})
public class PaymentPaypalApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentPaypalApplication.class, args);
    }
}
