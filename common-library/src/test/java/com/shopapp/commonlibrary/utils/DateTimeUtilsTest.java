package com.shopapp.commonlibrary.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class DateTimeUtilsTest {

    private static final LocalDateTime DATE_TIME = LocalDateTime.of(2026, 11, 14, 9, 5, 3);

    @Test
    void format_defaultPattern() {
        assertThat(DateTimeUtils.format(DATE_TIME)).isEqualTo("14-11-2026_09-05-03");
    }

    @Test
    void format_customPattern() {
        assertThat(DateTimeUtils.format(DATE_TIME, "yyyy/MM/dd")).isEqualTo("2026/11/14");
    }
}
