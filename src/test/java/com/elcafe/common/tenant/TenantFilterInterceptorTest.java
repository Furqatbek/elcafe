package com.elcafe.common.tenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TenantFilterInterceptor gating")
class TenantFilterInterceptorTest {

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    /**
     * Captures the tenant the Hibernate filter would be enabled for, overriding the one method that
     * touches a live persistence context so the gating logic can be tested in isolation.
     */
    private static class CapturingInterceptor extends TenantFilterInterceptor {
        Long enabledFor;

        CapturingInterceptor(String mode) {
            super(null, mode);
        }

        @Override
        void enableFilter(Long tenantId) {
            this.enabledFor = tenantId;
        }
    }

    @Test
    @DisplayName("enforce + a bound tenant enables the filter for that restaurant")
    void enforceWithTenantEnablesFilter() {
        CapturingInterceptor interceptor = new CapturingInterceptor("enforce");
        TenantContext.setRestaurantId(7L);

        boolean proceed = interceptor.preHandle(null, null, null);

        assertThat(proceed).isTrue();
        assertThat(interceptor.enabledFor).isEqualTo(7L);
    }

    @Test
    @DisplayName("shadow mode never enables the filter (zero behaviour change)")
    void shadowDoesNotEnableFilter() {
        CapturingInterceptor interceptor = new CapturingInterceptor("shadow");
        TenantContext.setRestaurantId(7L);

        interceptor.preHandle(null, null, null);

        assertThat(interceptor.enabledFor).isNull();
    }

    @Test
    @DisplayName("off mode never enables the filter")
    void offDoesNotEnableFilter() {
        CapturingInterceptor interceptor = new CapturingInterceptor("off");
        TenantContext.setRestaurantId(7L);

        interceptor.preHandle(null, null, null);

        assertThat(interceptor.enabledFor).isNull();
    }

    @Test
    @DisplayName("enforce without a bound tenant (SUPER_ADMIN aggregate / unbound token) stays unscoped")
    void enforceWithoutTenantDoesNotEnableFilter() {
        CapturingInterceptor interceptor = new CapturingInterceptor("enforce");
        // TenantContext intentionally left unset.

        boolean proceed = interceptor.preHandle(null, null, null);

        assertThat(proceed).isTrue();
        assertThat(interceptor.enabledFor).isNull();
    }
}
