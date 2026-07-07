package com.elcafe.modules.auth.converter;

import com.elcafe.modules.auth.enums.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserRoleConverterTest {

    private final UserRoleConverter converter = new UserRoleConverter();

    @Test
    @DisplayName("known role round-trips by name")
    void knownRoleRoundTrips() {
        assertThat(converter.convertToDatabaseColumn(UserRole.ADMIN)).isEqualTo("ADMIN");
        assertThat(converter.convertToEntityAttribute("ADMIN")).isEqualTo(UserRole.ADMIN);
        assertThat(converter.convertToEntityAttribute("SUPER_ADMIN")).isEqualTo(UserRole.SUPER_ADMIN);
    }

    @Test
    @DisplayName("unrecognised DB value maps to UNKNOWN instead of throwing")
    void unknownValueMapsToUnknown() {
        // This is the exact failure mode: a role string with no enum constant.
        assertThat(converter.convertToEntityAttribute("SOME_FUTURE_ROLE")).isEqualTo(UserRole.UNKNOWN);
        assertThat(converter.convertToEntityAttribute("")).isEqualTo(UserRole.UNKNOWN);
    }

    @Test
    @DisplayName("null passes through both directions")
    void nullPassthrough() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    @DisplayName("refuses to persist the UNKNOWN sentinel (would destroy the original role string)")
    void refusesToWriteUnknown() {
        assertThatThrownBy(() -> converter.convertToDatabaseColumn(UserRole.UNKNOWN))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("UNKNOWN");
    }
}
