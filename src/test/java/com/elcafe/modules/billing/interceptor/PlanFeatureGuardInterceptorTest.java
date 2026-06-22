package com.elcafe.modules.billing.interceptor;

import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.exception.ForbiddenException;
import com.elcafe.exception.GlobalExceptionHandler;
import com.elcafe.modules.billing.service.PlanGateService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PlanFeatureGuardInterceptorTest {

    // ---------------------------------------------------------------- featureFor(uri) map

    @Test
    @DisplayName("featureFor maps paid paths to codes (specific before general)")
    void featureFor_mapsPaidPaths() {
        assertThat(PlanFeatureGuardInterceptor.featureFor("/api/v1/inventory/ingredients")).isEqualTo("inventory");
        assertThat(PlanFeatureGuardInterceptor.featureFor("/api/v1/inventory/production-batches")).isEqualTo("kitchen.production");
        assertThat(PlanFeatureGuardInterceptor.featureFor("/api/v1/inventory/po-suggestions")).isEqualTo("inventory.po_suggestions");
        assertThat(PlanFeatureGuardInterceptor.featureFor("/api/v1/kitchen/stations")).isEqualTo("kitchen.stations");
        assertThat(PlanFeatureGuardInterceptor.featureFor("/api/v1/kitchen/orders")).isEqualTo("kitchen");
        assertThat(PlanFeatureGuardInterceptor.featureFor("/api/v1/dashboard")).isEqualTo("analytics");
        assertThat(PlanFeatureGuardInterceptor.featureFor("/api/v1/financial/payroll")).isEqualTo("payroll");
        assertThat(PlanFeatureGuardInterceptor.featureFor("/api/v1/financial/expenses")).isEqualTo("finance");
        assertThat(PlanFeatureGuardInterceptor.featureFor("/api/v1/sms/campaigns")).isEqualTo("marketing.sms");
        assertThat(PlanFeatureGuardInterceptor.featureFor("/api/v1/telegram/subscribers")).isEqualTo("telegram.subscribers");
        assertThat(PlanFeatureGuardInterceptor.featureFor("/api/v1/telegram/campaigns")).isEqualTo("marketing.telegram");
        assertThat(PlanFeatureGuardInterceptor.featureFor("/api/v1/restaurants/5/promotions")).isEqualTo("marketing");
        assertThat(PlanFeatureGuardInterceptor.featureFor("/api/v1/restaurants/5/milestones")).isEqualTo("marketing.milestones");
        assertThat(PlanFeatureGuardInterceptor.featureFor("/api/v1/restaurants/5/referrals")).isEqualTo("marketing.referrals");
    }

    @Test
    @DisplayName("featureFor is null for core, public, courier-app, webhook and billing/auth paths")
    void featureFor_nullForUngated() {
        assertThat(PlanFeatureGuardInterceptor.featureFor("/api/v1/orders")).isNull();
        assertThat(PlanFeatureGuardInterceptor.featureFor("/api/v1/pos/orders")).isNull();
        assertThat(PlanFeatureGuardInterceptor.featureFor("/api/v1/products")).isNull();
        assertThat(PlanFeatureGuardInterceptor.featureFor("/api/v1/customers")).isNull();
        assertThat(PlanFeatureGuardInterceptor.featureFor("/api/v1/financial/accounts")).isNull();
        assertThat(PlanFeatureGuardInterceptor.featureFor("/api/v1/public/reviews")).isNull();
        assertThat(PlanFeatureGuardInterceptor.featureFor("/api/v1/courier/orders")).isNull();
        assertThat(PlanFeatureGuardInterceptor.featureFor("/api/v1/instagram/webhook")).isNull();
        assertThat(PlanFeatureGuardInterceptor.featureFor("/api/v1/billing/me")).isNull();
        assertThat(PlanFeatureGuardInterceptor.featureFor("/api/v1/auth/login")).isNull();
        assertThat(PlanFeatureGuardInterceptor.featureFor("/api/v1/self-service/start")).isNull();
    }

    // ---------------------------------------------------------------- preHandle wiring

    private final PlanGateService gate = mock(PlanGateService.class);
    private final RestaurantAuthorizationService authz = mock(RestaurantAuthorizationService.class);
    private final PlanFeatureGuardInterceptor interceptor = new PlanFeatureGuardInterceptor(gate, authz);

    private MockMvc mvc() {
        return MockMvcBuilders.standaloneSetup(new DummyController())
                .addInterceptors(interceptor)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("staff whose plan lacks the feature → 403 plan.feature_required:<code>")
    void gatedPath_planLacksFeature_returns403() throws Exception {
        when(authz.isAdmin()).thenReturn(false);
        when(authz.getCurrentUserRestaurantId()).thenReturn(5L);
        doThrow(new ForbiddenException("plan.feature_required:inventory"))
                .when(gate).requireFeatureIfPlanned(5L, "inventory");

        mvc().perform(get("/api/v1/inventory/ingredients"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("plan.feature_required:inventory"));
    }

    @Test
    @DisplayName("staff whose plan has the feature → 200")
    void gatedPath_hasFeature_ok() throws Exception {
        when(authz.isAdmin()).thenReturn(false);
        when(authz.getCurrentUserRestaurantId()).thenReturn(5L);
        // requireFeatureIfPlanned is a no-op (mock) → allowed
        mvc().perform(get("/api/v1/inventory/ingredients")).andExpect(status().isOk());
        verify(gate).requireFeatureIfPlanned(5L, "inventory");
    }

    @Test
    @DisplayName("core path is never gated")
    void corePath_notGated() throws Exception {
        mvc().perform(get("/api/v1/orders")).andExpect(status().isOk());
        verifyNoInteractions(gate);
    }

    @Test
    @DisplayName("non-staff (null tenant) on a gated path → not gated")
    void nullTenant_notGated() throws Exception {
        when(authz.isAdmin()).thenReturn(false);
        when(authz.getCurrentUserRestaurantId()).thenReturn(null);
        mvc().perform(get("/api/v1/inventory/ingredients")).andExpect(status().isOk());
        verify(gate, never()).requireFeatureIfPlanned(anyLong(), anyString());
    }

    @Test
    @DisplayName("SUPER_ADMIN is never gated")
    void superAdmin_notGated() throws Exception {
        when(authz.isAdmin()).thenReturn(true);
        mvc().perform(get("/api/v1/inventory/ingredients")).andExpect(status().isOk());
        verify(gate, never()).requireFeatureIfPlanned(anyLong(), anyString());
    }

    @RestController
    static class DummyController {
        @GetMapping("/api/v1/inventory/ingredients")
        public String inventory() {
            return "ok";
        }

        @GetMapping("/api/v1/orders")
        public String orders() {
            return "ok";
        }
    }
}
