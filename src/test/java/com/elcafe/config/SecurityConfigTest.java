package com.elcafe.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigTest {

    @Test
    @DisplayName("splitCsv trims each origin and drops blanks (spaces after commas don't break matching)")
    void splitCsv_trimsAndDropsBlanks() {
        assertThat(SecurityConfig.splitCsv("https://qahvoon.uz, https://www.qahvoon.uz , http://localhost:8081"))
                .containsExactly("https://qahvoon.uz", "https://www.qahvoon.uz", "http://localhost:8081");
    }

    @Test
    @DisplayName("splitCsv tolerates trailing/empty entries")
    void splitCsv_toleratesEmpties() {
        assertThat(SecurityConfig.splitCsv("*,"))
                .containsExactly("*");
        assertThat(SecurityConfig.splitCsv(" , https://a.uz ,"))
                .containsExactly("https://a.uz");
    }
}
