package com.elcafe.common.tenant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TenantEnforcementMode parsing")
class TenantEnforcementModeTest {

    @Test
    @DisplayName("recognises the off aliases")
    void offAliases() {
        assertThat(TenantEnforcementMode.from("off")).isEqualTo(TenantEnforcementMode.OFF);
        assertThat(TenantEnforcementMode.from("disabled")).isEqualTo(TenantEnforcementMode.OFF);
        assertThat(TenantEnforcementMode.from("false")).isEqualTo(TenantEnforcementMode.OFF);
    }

    @Test
    @DisplayName("recognises the enforce aliases, case-insensitively and trimmed")
    void enforceAliases() {
        assertThat(TenantEnforcementMode.from("enforce")).isEqualTo(TenantEnforcementMode.ENFORCE);
        assertThat(TenantEnforcementMode.from("block")).isEqualTo(TenantEnforcementMode.ENFORCE);
        assertThat(TenantEnforcementMode.from("strict")).isEqualTo(TenantEnforcementMode.ENFORCE);
        assertThat(TenantEnforcementMode.from("  Enforce  ")).isEqualTo(TenantEnforcementMode.ENFORCE);
    }

    @Test
    @DisplayName("defaults unknown and null values to the safe-to-observe SHADOW")
    void defaultsToShadow() {
        assertThat(TenantEnforcementMode.from("shadow")).isEqualTo(TenantEnforcementMode.SHADOW);
        assertThat(TenantEnforcementMode.from(null)).isEqualTo(TenantEnforcementMode.SHADOW);
        assertThat(TenantEnforcementMode.from("nonsense")).isEqualTo(TenantEnforcementMode.SHADOW);
    }
}
