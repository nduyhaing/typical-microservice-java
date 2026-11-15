package com.shopapp.payment.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** The reconciler of completed payments order has not accepted yet (decisions.md 5-61). */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
