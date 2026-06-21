package com.elcafe.common.security.service;

import com.elcafe.common.tenant.TenantContext;
import com.elcafe.modules.auth.enums.UserRole;
import com.elcafe.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RestaurantAuthorizationServiceTest {

    private final RestaurantAuthorizationService service = new RestaurantAuthorizationService();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(UserRole role, Long restaurantId) {
        UserPrincipal principal = new UserPrincipal(1L, "user@test.com", "pw", role, true, restaurantId);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private void setMode(String mode) {
        ReflectionTestUtils.setField(service, "enforcementMode", mode);
    }

    @Test
    @DisplayName("checkAccess ENFORCE — cross-tenant throws AccessDenied")
    void checkAccess_enforce_crossTenant_throws() {
        authenticateAs(UserRole.ADMIN, 1L);
        setMode("enforce");
        assertThatThrownBy(() -> service.checkAccess(2L)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("checkAccess ENFORCE — same tenant allowed")
    void checkAccess_enforce_sameTenant_ok() {
        authenticateAs(UserRole.ADMIN, 1L);
        setMode("enforce");
        assertThatCode(() -> service.checkAccess(1L)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("checkAccess SHADOW — cross-tenant does NOT throw")
    void checkAccess_shadow_crossTenant_noThrow() {
        authenticateAs(UserRole.ADMIN, 1L);
        setMode("shadow");
        assertThatCode(() -> service.checkAccess(2L)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("checkAccess OFF — cross-tenant does NOT throw")
    void checkAccess_off_crossTenant_noThrow() {
        authenticateAs(UserRole.ADMIN, 1L);
        setMode("off");
        assertThatCode(() -> service.checkAccess(2L)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("checkAccess ENFORCE — SUPER_ADMIN is cross-tenant (allowed)")
    void checkAccess_enforce_superAdmin_ok() {
        authenticateAs(UserRole.SUPER_ADMIN, 1L);
        setMode("enforce");
        assertThatCode(() -> service.checkAccess(2L)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("checkAccess — null/unset mode defaults to shadow (no throw)")
    void checkAccess_nullMode_defaultsShadow() {
        authenticateAs(UserRole.ADMIN, 1L);
        // enforcementMode left unset (null)
        assertThatCode(() -> service.checkAccess(2L)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateRestaurantAccess — ADMIN is now tenant-scoped (no cross-tenant bypass)")
    void validate_admin_isTenantScoped() {
        authenticateAs(UserRole.ADMIN, 1L);
        assertThatThrownBy(() -> service.validateRestaurantAccess(2L)).isInstanceOf(AccessDeniedException.class);
        assertThatCode(() -> service.validateRestaurantAccess(1L)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("currentTenantScopeOrNull ENFORCE — tenant-scoped caller returns own restaurant")
    void scope_enforce_tenantCaller_returnsOwn() {
        authenticateAs(UserRole.ADMIN, 7L);
        setMode("enforce");
        assertThat(service.currentTenantScopeOrNull()).isEqualTo(7L);
    }

    @Test
    @DisplayName("currentTenantScopeOrNull ENFORCE — SUPER_ADMIN is unscoped (null)")
    void scope_enforce_superAdmin_null() {
        authenticateAs(UserRole.SUPER_ADMIN, 7L);
        setMode("enforce");
        assertThat(service.currentTenantScopeOrNull()).isNull();
    }

    @Test
    @DisplayName("currentTenantScopeOrNull SHADOW — unscoped (null), preserving current behaviour")
    void scope_shadow_null() {
        authenticateAs(UserRole.ADMIN, 7L);
        setMode("shadow");
        assertThat(service.currentTenantScopeOrNull()).isNull();
    }

    @Test
    @DisplayName("currentTenantScopeOrNull OFF — unscoped (null)")
    void scope_off_null() {
        authenticateAs(UserRole.ADMIN, 7L);
        setMode("off");
        assertThat(service.currentTenantScopeOrNull()).isNull();
    }

    @Test
    @DisplayName("currentTenantScopeOrNull ENFORCE — unauthenticated is unscoped (null)")
    void scope_enforce_noPrincipal_null() {
        setMode("enforce");
        assertThat(service.currentTenantScopeOrNull()).isNull();
    }

    @Test
    @DisplayName("currentTenantReadScope ENFORCE — tenant caller returns own restaurant")
    void readScope_enforce_tenantCaller_returnsOwn() {
        authenticateAs(UserRole.ADMIN, 7L);
        setMode("enforce");
        assertThat(service.currentTenantReadScope()).isEqualTo(7L);
    }

    @Test
    @DisplayName("currentTenantReadScope ENFORCE — caller with no restaurant gets deny-all sentinel")
    void readScope_enforce_noRestaurant_sentinel() {
        authenticateAs(UserRole.ADMIN, null);
        setMode("enforce");
        assertThat(service.currentTenantReadScope()).isEqualTo(TenantContext.NO_ACCESS);
    }

    @Test
    @DisplayName("currentTenantReadScope ENFORCE — SUPER_ADMIN is unscoped (null)")
    void readScope_enforce_superAdmin_null() {
        authenticateAs(UserRole.SUPER_ADMIN, null);
        setMode("enforce");
        assertThat(service.currentTenantReadScope()).isNull();
    }

    @Test
    @DisplayName("currentTenantReadScope SHADOW — unscoped (null) even with no restaurant")
    void readScope_shadow_noRestaurant_null() {
        authenticateAs(UserRole.ADMIN, null);
        setMode("shadow");
        assertThat(service.currentTenantReadScope()).isNull();
    }
}
