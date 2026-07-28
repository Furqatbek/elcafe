package com.elcafe.common.channel;

import com.elcafe.exception.BadRequestException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The shared "you need a restaurant to create tenant-owned data" guard that every per-tenant channel
 * create routes through.
 */
class ChannelWriteGuardTest {

    @Test
    @DisplayName("returns the restaurant id when the caller has one")
    void returnsRestaurantWhenPresent() {
        assertThat(ChannelWriteGuard.requireRestaurant(7L, "An SMS template", "to create one")).isEqualTo(7L);
    }

    @Test
    @DisplayName("refuses a null scope, building the message from subject + action")
    void refusesNullScopeWithExactMessage() {
        assertThatThrownBy(() -> ChannelWriteGuard.requireRestaurant(
                null, "An Instagram configuration", "to connect an Instagram account"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("An Instagram configuration belongs to a restaurant. Sign in with a "
                        + "restaurant-scoped account to connect an Instagram account.");
    }
}
