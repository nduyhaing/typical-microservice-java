package com.shopapp.payment.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/** The reconciler's back-off (decisions.md 5-61). */
class PaymentServiceTest {

    @Test
    void confirmRetryDelay_startsAt30Seconds_doubles_andStopsAtAnHour() {
        assertThat(PaymentService.confirmRetryDelay(1)).isEqualTo(Duration.ofSeconds(30));
        assertThat(PaymentService.confirmRetryDelay(2)).isEqualTo(Duration.ofSeconds(60));
        assertThat(PaymentService.confirmRetryDelay(3)).isEqualTo(Duration.ofSeconds(120));
        assertThat(PaymentService.confirmRetryDelay(7)).isEqualTo(Duration.ofSeconds(1920));
        assertThat(PaymentService.confirmRetryDelay(8)).isEqualTo(Duration.ofHours(1));
        assertThat(PaymentService.confirmRetryDelay(50)).isEqualTo(Duration.ofHours(1));
        assertThat(PaymentService.confirmRetryDelay(0)).isEqualTo(Duration.ofSeconds(30));
    }
}
