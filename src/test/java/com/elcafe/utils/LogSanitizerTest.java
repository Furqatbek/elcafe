package com.elcafe.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Pins the go-live gate A6 guarantee: logs never carry more than a phone's last 4 digits. */
class LogSanitizerTest {

    @Test
    void keepsOnlyLastFourDigits() {
        assertThat(LogSanitizer.phone("+998901234567")).isEqualTo("***4567");
        assertThat(LogSanitizer.phone("1234567890")).isEqualTo("***7890");
    }

    @Test
    void neverExposesMoreThanLastFour() {
        String masked = LogSanitizer.phone("+998901234567");
        assertThat(masked).startsWith("***").endsWith("4567");
        assertThat(masked).doesNotContain("99890123"); // the identifying prefix is gone
    }

    @Test
    void nullBlankAndShortInputsAreSafe() {
        assertThat(LogSanitizer.phone(null)).isEqualTo("<none>");
        assertThat(LogSanitizer.phone("   ")).isEqualTo("<none>");
        assertThat(LogSanitizer.phone("12")).isEqualTo("****");
        assertThat(LogSanitizer.phone("1234")).isEqualTo("****");
    }
}
