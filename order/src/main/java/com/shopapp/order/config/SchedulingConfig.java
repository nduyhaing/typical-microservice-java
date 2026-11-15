package com.shopapp.order.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** The retry sweep of orders that still owe their stock or coupon use back (decisions.md 5-4). */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
