package com.elcafe.common.security;

import com.elcafe.exception.BadRequestException;
import com.elcafe.modules.auth.enums.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The one rule that decides whether an account can exist without a restaurant.
 *
 * <p>Worth its own test because the failure it prevents is invisible rather than loud: an account with
 * a tenant-scoped role and no restaurant signs in perfectly and then shows an empty application, which
 * every operator reads as a wiped database. The rule has to hold for <em>every</em> role, including
 * ones added later, so the coverage below is exhaustive over the enum rather than a sample.
 */
class UserTenantBindingTest {

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = "SUPER_ADMIN", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("every role except SUPER_ADMIN must belong to a restaurant")
    void everyTenantScopedRoleNeedsARestaurant(UserRole role) {
        assertThat(UserTenantBinding.requiresRestaurant(role)).isTrue();

        assertThatThrownBy(() -> UserTenantBinding.require(role, null, "Cannot create this user"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("must belong to a restaurant");
    }

    /**
     * The platform operator's null is meaningful, not missing — being outside every tenant is what
     * lets it provision them. Requiring a restaurant here would make the platform unadministerable.
     */
    @Test
    @DisplayName("SUPER_ADMIN is the one role for which no restaurant is correct")
    void superAdminMayBeUnbound() {
        assertThat(UserTenantBinding.requiresRestaurant(UserRole.SUPER_ADMIN)).isFalse();

        assertThatCode(() -> UserTenantBinding.require(UserRole.SUPER_ADMIN, null, "Cannot create"))
                .doesNotThrowAnyException();
        assertThat(UserTenantBinding.isUnbound(UserRole.SUPER_ADMIN, null)).isFalse();
    }

    @Test
    @DisplayName("a bound tenant-scoped account passes")
    void boundAccountIsAccepted() {
        assertThatCode(() -> UserTenantBinding.require(UserRole.ADMIN, 4L, "Cannot create"))
                .doesNotThrowAnyException();
        assertThat(UserTenantBinding.isUnbound(UserRole.ADMIN, 4L)).isFalse();
    }

    /** The message is the whole point — it has to explain the symptom, not just state a constraint. */
    @Test
    @DisplayName("the refusal explains what the broken state looks like, not just that it is invalid")
    void messageExplainsTheSymptom() {
        assertThatThrownBy(() -> UserTenantBinding.require(UserRole.ADMIN, null, "Cannot update this user"))
                .hasMessageContaining("Cannot update this user")
                .hasMessageContaining("sees no data")
                .hasMessageContaining("SUPER_ADMIN");
    }

    @Test
    @DisplayName("isUnbound identifies exactly the accounts the boot-time audit must report")
    void isUnboundIdentifiesTheAuditTargets() {
        assertThat(UserTenantBinding.isUnbound(UserRole.ADMIN, null)).isTrue();
        assertThat(UserTenantBinding.isUnbound(UserRole.COURIER, null)).isTrue();
        assertThat(UserTenantBinding.isUnbound(UserRole.OWNER, null)).isTrue();
        assertThat(UserTenantBinding.isUnbound(UserRole.SUPER_ADMIN, null)).isFalse();
        assertThat(UserTenantBinding.isUnbound(UserRole.ADMIN, 1L)).isFalse();
    }
}
