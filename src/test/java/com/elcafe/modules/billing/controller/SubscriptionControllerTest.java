package com.elcafe.modules.billing.controller;

import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.modules.auth.enums.UserRole;
import com.elcafe.modules.billing.dto.BillingStatusDto;
import com.elcafe.modules.billing.dto.SetPlanRequest;
import com.elcafe.modules.billing.service.PlanGateService;
import com.elcafe.security.UserPrincipal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The /billing/admin/set-plan endpoint must enforce restaurant ownership on the body's restaurantId
 * (audit #14) — hasRole('ADMIN') alone would let a tenant rewrite another tenant's plan. Verified by
 * driving the controller method directly.
 */
class SubscriptionControllerTest {

    private final PlanGateService planGateService = mock(PlanGateService.class);
    private final RestaurantAuthorizationService authz = mock(RestaurantAuthorizationService.class);
    private final SubscriptionController controller = new SubscriptionController(planGateService, authz);

    private final UserPrincipal actor = new UserPrincipal(1L, "admin@test.com", "pw", UserRole.ADMIN, true, 1L);

    private SetPlanRequest request(Long restaurantId) {
        SetPlanRequest r = new SetPlanRequest();
        r.setRestaurantId(restaurantId);
        r.setPlanCode("pro");
        return r;
    }

    @Test
    @DisplayName("set-plan validates ownership of the body restaurantId before changing the plan")
    void setPlan_checksOwnership_thenDelegates() {
        when(planGateService.setPlan(any(), any())).thenReturn(BillingStatusDto.builder().build());

        controller.setPlan(request(42L), actor);

        verify(authz).validateRestaurantAccess(42L);
        verify(planGateService).setPlan(any(), any());
    }

    @Test
    @DisplayName("set-plan on a foreign restaurant is denied and never reaches the gate service")
    void setPlan_crossTenant_denied() {
        doThrow(new AccessDeniedException("denied")).when(authz).validateRestaurantAccess(99L);

        assertThatThrownBy(() -> controller.setPlan(request(99L), actor))
                .isInstanceOf(AccessDeniedException.class);
        verify(planGateService, never()).setPlan(any(), any());
    }
}
