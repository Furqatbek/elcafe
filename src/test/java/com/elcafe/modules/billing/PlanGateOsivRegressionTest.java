package com.elcafe.modules.billing;

import com.elcafe.modules.auth.entity.User;
import com.elcafe.modules.auth.enums.UserRole;
import com.elcafe.modules.auth.repository.UserRepository;
import com.elcafe.modules.billing.entity.SubscriptionPlan;
import com.elcafe.modules.billing.repository.SubscriptionPlanRepository;
import com.elcafe.modules.billing.service.PlanGateService;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import com.elcafe.security.JwtUtil;
import com.elcafe.security.UserPrincipal;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression pin for the plan-gate LazyInitializationException found by the post-OSIV-flip boot
 * smoke: {@code PlanGateService.load()} runs OUTSIDE any transaction when reached through the gate
 * interceptors ({@code requireWriteAccess}/{@code requireFeatureIfPlanned} are unannotated, and their
 * calls into the {@code @Transactional getCurrentPlan} are self-invocations the proxy never sees).
 * With {@code open-in-view} on, the request session initialised the lazy {@code Restaurant.plan}
 * proxy anyway; with it off, EVERY write and every gated-module request of a tenant that HAS a plan
 * became a 500. The rest of the suite missed it because its tenants carry no plan — {@code load()}
 * short-circuits before touching the proxy — so this class seeds the missing case: a tenant-bound
 * staff user whose restaurant points at a real plan row.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:plangateosiv;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;"
                + "DB_CLOSE_ON_EXIT=FALSE;DATABASE_TO_UPPER=FALSE;NON_KEYWORDS=VALUE;"
                + "INIT=CREATE SCHEMA IF NOT EXISTS public",
        "management.health.redis.enabled=false",
        // The condition under test: no request-scoped session may rescue the gate's lazy access.
        "spring.jpa.open-in-view=false",
})
class PlanGateOsivRegressionTest {

    private static final String AUTH = "Authorization";

    @Autowired private MockMvc mvc;
    @Autowired private UserRepository userRepository;
    @Autowired private RestaurantRepository restaurantRepository;
    @Autowired private SubscriptionPlanRepository planRepository;
    @Autowired private PlanGateService planGateService;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private PasswordEncoder passwordEncoder;

    private Long restaurantId;
    private String adminToken;

    @BeforeAll
    void seed() {
        SubscriptionPlan plan = planRepository.save(SubscriptionPlan.builder()
                .code("gate-test")
                .name("Gate Test")
                .featureCodes(new LinkedHashSet<>(Set.of(PlanFeatures.KITCHEN)))
                .build());

        Restaurant restaurant = Restaurant.builder()
                .name("Plan Gate").address("1 Gate St").active(true).build();
        restaurant.setPlan(plan);
        // No expiry: write access must be granted, not read-only-blocked.
        restaurant.setPlanExpiresAt(null);
        restaurantId = restaurantRepository.save(restaurant).getId();

        User admin = userRepository.save(User.builder()
                .email("plangate.admin@test.com").password(passwordEncoder.encode("pw"))
                .firstName("P").lastName("G").role(UserRole.ADMIN).active(true).restaurantId(restaurantId)
                .build());
        adminToken = "Bearer " + jwtUtil.generateAccessToken(UserPrincipal.create(admin));
    }

    @BeforeEach
    void freshLoad() {
        // Each test must exercise load() itself, not a snapshot another test left in the TTL cache.
        planGateService.invalidate(restaurantId);
    }

    @Test
    @DisplayName("service entry without a transaction loads the plan (the exact broken path)")
    void gateEntryPointsWorkWithoutAmbientTransaction() {
        // This call chain (unannotated entry -> self-invoked getCurrentPlan -> load) threw
        // LazyInitializationException before load() switched to the fetch-joined finder.
        assertThatCode(() -> planGateService.requireWriteAccess(restaurantId)).doesNotThrowAnyException();
        assertThat(planGateService.hasFeature(restaurantId, PlanFeatures.KITCHEN)).isTrue();
        assertThat(planGateService.hasFeature(restaurantId, PlanFeatures.ANALYTICS)).isFalse();
    }

    @Test
    @DisplayName("write request of a planned tenant passes the write guard (was a 500)")
    void writeGuardSurvivesForPlannedTenant() throws Exception {
        // Nonexistent order: the controller must answer 404 — meaning PlanWriteGuardInterceptor's
        // requireWriteAccess already ran and survived. With the bug the interceptor 500'd before the
        // controller was ever reached.
        mvc.perform(patch("/api/v1/orders/999999/status").param("status", "PREPARING")
                        .header(AUTH, adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("gated module the plan includes stays reachable (was a 500)")
    void grantedFeatureModuleReachable() throws Exception {
        mvc.perform(get("/api/v1/kitchen/orders/active")
                        .param("restaurantId", String.valueOf(restaurantId))
                        .header(AUTH, adminToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("gated module the plan lacks is rejected with the upgrade-CTA 403, not a 500")
    void missingFeatureModuleGetsStructured403() throws Exception {
        mvc.perform(get("/api/v1/analytics/financial/daily-revenue").header(AUTH, adminToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("plan.feature_required:analytics")));
    }
}
