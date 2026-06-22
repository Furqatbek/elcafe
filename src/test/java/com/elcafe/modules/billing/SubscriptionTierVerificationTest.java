package com.elcafe.modules.billing;

import com.elcafe.common.audit.service.AuditService;
import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.exception.GlobalExceptionHandler;
import com.elcafe.modules.auth.enums.UserRole;
import com.elcafe.modules.billing.dto.BillingStatusDto;
import com.elcafe.modules.billing.entity.SubscriptionPlan;
import com.elcafe.modules.billing.interceptor.PlanFeatureGuardInterceptor;
import com.elcafe.modules.billing.interceptor.PlanWriteGuardInterceptor;
import com.elcafe.modules.billing.repository.SubscriptionPlanRepository;
import com.elcafe.modules.billing.service.PlanGateService;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import com.elcafe.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Staging-style end-to-end verification of the subscription tier system. Wires the REAL
 * {@link PlanGateService}, the REAL feature/write-guard interceptors, the REAL tenant resolution and
 * the real 403 mapping over MockMvc, with seeded Start / Pro / expired-Pro restaurants — and asserts
 * what a client actually sees. This is the in-process analogue of clicking through staging (no live
 * backend/browser is available in CI).
 */
class SubscriptionTierVerificationTest {

    private static final long START_RID = 1L;
    private static final long PRO_RID = 2L;
    private static final long EXPIRED_RID = 3L; // Pro, expired 10 days ago (past the 3-day grace)

    private MockMvc mvc;
    private PlanGateService gate;

    @BeforeEach
    void setUp() {
        SubscriptionPlanRepository planRepo = mock(SubscriptionPlanRepository.class);
        RestaurantRepository restaurantRepo = mock(RestaurantRepository.class);
        AuditService audit = mock(AuditService.class);

        RestaurantAuthorizationService authz = new RestaurantAuthorizationService();
        ReflectionTestUtils.setField(authz, "enforcementMode", "shadow");
        gate = new PlanGateService(planRepo, restaurantRepo, authz, audit);

        SubscriptionPlan start = plan("start", Set.of());
        SubscriptionPlan pro = plan("pro", new LinkedHashSet<>(PlanFeatures.pro()));

        // Build the restaurant mocks before stubbing findById: restaurant()/plan() stub internally,
        // and Mockito forbids nested stubbing inside a when(...) argument.
        Restaurant startRestaurant = restaurant(start, null);
        Restaurant proRestaurant = restaurant(pro, null);
        Restaurant expiredRestaurant = restaurant(pro, LocalDateTime.now().minusDays(10));
        when(restaurantRepo.findById(START_RID)).thenReturn(Optional.of(startRestaurant));
        when(restaurantRepo.findById(PRO_RID)).thenReturn(Optional.of(proRestaurant));
        when(restaurantRepo.findById(EXPIRED_RID)).thenReturn(Optional.of(expiredRestaurant));

        mvc = MockMvcBuilders.standaloneSetup(new DummyController())
                .addInterceptors(new PlanWriteGuardInterceptor(gate, authz),
                        new PlanFeatureGuardInterceptor(gate, authz))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private SubscriptionPlan plan(String code, Set<String> codes) {
        SubscriptionPlan p = mock(SubscriptionPlan.class);
        when(p.getCode()).thenReturn(code);
        when(p.getName()).thenReturn(code);
        when(p.getFeatureCodes()).thenReturn(codes);
        return p;
    }

    private Restaurant restaurant(SubscriptionPlan plan, LocalDateTime expiresAt) {
        Restaurant r = mock(Restaurant.class);
        when(r.getPlan()).thenReturn(plan);
        when(r.getPlanExpiresAt()).thenReturn(expiresAt);
        return r;
    }

    private void authAs(UserRole role, Long restaurantId) {
        UserPrincipal p = new UserPrincipal(99L, "staff@test.com", "x", role, true, restaurantId);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities()));
    }

    @Test
    @DisplayName("Start tier → paid module (inventory) → 403 plan.feature_required:inventory")
    void startTier_paidModule_forbidden() throws Exception {
        authAs(UserRole.ADMIN, START_RID);
        mvc.perform(get("/api/v1/inventory/ingredients"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("plan.feature_required:inventory"));
    }

    @Test
    @DisplayName("Pro tier → same paid module → allowed (200)")
    void proTier_paidModule_allowed() throws Exception {
        authAs(UserRole.ADMIN, PRO_RID);
        mvc.perform(get("/api/v1/inventory/ingredients")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Start tier → core module (orders) → allowed (never gated)")
    void startTier_coreModule_allowed() throws Exception {
        authAs(UserRole.ADMIN, START_RID);
        mvc.perform(get("/api/v1/orders")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("SUPER_ADMIN → paid module → allowed (platform operator bypasses gating)")
    void superAdmin_bypasses() throws Exception {
        authAs(UserRole.SUPER_ADMIN, null);
        mvc.perform(get("/api/v1/inventory/ingredients")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("billing status reflects the tier: Start = no codes, Pro = all 25 (not read-only)")
    void billingStatusReflectsTier() {
        assertThat(gate.getBillingStatus(START_RID).getFeatureCodes()).isEmpty();

        BillingStatusDto pro = gate.getBillingStatus(PRO_RID);
        assertThat(pro.getFeatureCodes()).hasSize(25).contains("inventory", "payroll", "marketing.sms");
        assertThat(pro.getReadOnly()).isFalse();
    }

    @Test
    @DisplayName("Expired plan (past grace) → mutation blocked with plan.read_only")
    void expiredPlan_writeBlocked() throws Exception {
        authAs(UserRole.ADMIN, EXPIRED_RID);
        mvc.perform(post("/api/v1/orders"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("plan.read_only"));
    }

    @Test
    @DisplayName("Expired plan → reads still work (read-only, not a blackout) and status flags readOnly")
    void expiredPlan_readAllowed() throws Exception {
        authAs(UserRole.ADMIN, EXPIRED_RID);
        mvc.perform(get("/api/v1/inventory/ingredients")).andExpect(status().isOk());
        assertThat(gate.getBillingStatus(EXPIRED_RID).getReadOnly()).isTrue();
    }

    @RestController
    static class DummyController {
        @GetMapping("/api/v1/inventory/ingredients")
        public String inventory() {
            return "ok";
        }

        @GetMapping("/api/v1/orders")
        public String listOrders() {
            return "ok";
        }

        @PostMapping("/api/v1/orders")
        public String createOrder() {
            return "ok";
        }
    }
}
