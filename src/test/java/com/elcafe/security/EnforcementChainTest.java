package com.elcafe.security;

import com.elcafe.modules.auth.entity.User;
import com.elcafe.modules.auth.enums.UserRole;
import com.elcafe.modules.auth.repository.UserRepository;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import org.junit.jupiter.api.BeforeAll;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The end-to-end enforcement proof the audit (TEST-1/TEST-2/TEST-6) said did not exist: it boots the FULL
 * application context and drives real HTTP through the ACTUAL production security filter chain
 * (JWT auth → tenant enforcement → subscription gate → {@code @PreAuthorize} method security) with real,
 * signed JWTs for seeded users — not standalone MockMvc, not {@code @WithMockUser}, not a reflection check
 * that an annotation merely exists.
 *
 * <p>Both enforcement flags are set to {@code enforce} for this test, so a wrong role, a wrong tenant, and
 * a suspended tenant each produce their real status code. If someone weakens a {@code @PreAuthorize} role,
 * the tenant filter, or the subscription filter, an assertion here turns red — which nothing else in the
 * suite would catch.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:enforcementchainit;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;"
                + "DB_CLOSE_ON_EXIT=FALSE;DATABASE_TO_UPPER=FALSE;NON_KEYWORDS=VALUE;"
                + "INIT=CREATE SCHEMA IF NOT EXISTS public",
        "management.health.redis.enabled=false",
        // Exercise both gates for real.
        "app.security.tenant-enforcement.mode=enforce",
        "app.subscription.enforcement.mode=enforce",
        // The unauthenticated/wrong-token cases make the JWT filter log fail-closed rejections; mute it.
        "logging.level.com.elcafe.security.JwtAuthenticationFilter=OFF",
})
class EnforcementChainTest {

    private static final String AUTH = "Authorization";
    private static final String USERS = "/api/v1/system-users";       // hasRole('ADMIN')
    private static final String SMS = "/api/v1/sms/campaigns";         // hasRole('SUPER_ADMIN') (platform-operated)

    @Autowired private MockMvc mvc;
    @Autowired private UserRepository userRepository;
    @Autowired private RestaurantRepository restaurantRepository;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private PasswordEncoder passwordEncoder;

    private Long tenantA;
    private Long tenantB;
    private String adminAToken;      // ADMIN of active tenant A
    private String superAdminToken;  // platform operator
    private String adminCToken;      // ADMIN of SUSPENDED tenant C

    @BeforeAll
    void seed() {
        tenantA = restaurantRepository.save(active("Tenant A")).getId();
        tenantB = restaurantRepository.save(active("Tenant B")).getId();
        Long tenantC = restaurantRepository.save(suspended("Tenant C")).getId();

        adminAToken = tokenFor(user("admin.a@test.com", UserRole.ADMIN, tenantA));
        superAdminToken = tokenFor(user("super@test.com", UserRole.SUPER_ADMIN, null));
        adminCToken = tokenFor(user("admin.c@test.com", UserRole.ADMIN, tenantC));
    }

    private Restaurant active(String name) {
        return Restaurant.builder().name(name).address("1 Test St").active(true).build();
    }

    private Restaurant suspended(String name) {
        return Restaurant.builder().name(name).address("1 Test St").active(false).build();
    }

    private User user(String email, UserRole role, Long restaurantId) {
        return userRepository.save(User.builder()
                .email(email).password(passwordEncoder.encode("pw"))
                .firstName("T").lastName("U").role(role).active(true).restaurantId(restaurantId)
                .build());
    }

    private String tokenFor(User user) {
        return jwtUtil.generateAccessToken(UserPrincipal.create(user));
    }

    @Test
    @DisplayName("no token → the chain rejects (401/403), never reaches the controller")
    void unauthenticated_isRejected() throws Exception {
        int status = mvc.perform(get(USERS)).andReturn().getResponse().getStatus();
        assertThat(status).isIn(401, 403);
    }

    @Test
    @DisplayName("@PreAuthorize enforces roles through the real chain: ADMIN is 403 on a SUPER_ADMIN endpoint, SUPER_ADMIN is allowed")
    void methodSecurity_enforcesRole() throws Exception {
        mvc.perform(get(SMS).header(AUTH, bearer(adminAToken))).andExpect(status().isForbidden());
        mvc.perform(get(SMS).header(AUTH, bearer(superAdminToken))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("tenant enforcement blocks a cross-tenant restaurantId (403) but allows the caller's own tenant")
    void tenantEnforcement_blocksCrossTenant() throws Exception {
        mvc.perform(get(USERS).param("restaurantId", String.valueOf(tenantB))
                .header(AUTH, bearer(adminAToken))).andExpect(status().isForbidden());
        mvc.perform(get(USERS).param("restaurantId", String.valueOf(tenantA))
                .header(AUTH, bearer(adminAToken))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("subscription gate returns 402 for a suspended tenant's staff, 200 for an active tenant")
    void subscriptionGate_402sSuspendedTenant() throws Exception {
        mvc.perform(get(USERS).header(AUTH, bearer(adminCToken)))
                .andExpect(status().isPaymentRequired());   // 402
        mvc.perform(get(USERS).header(AUTH, bearer(adminAToken)))
                .andExpect(status().isOk());
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
