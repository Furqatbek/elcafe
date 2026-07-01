package com.elcafe.modules.billing.enforcement;

import com.elcafe.common.tenant.TenantContext;
import com.elcafe.modules.auth.enums.UserRole;
import com.elcafe.modules.billing.service.SubscriptionAccessService;
import com.elcafe.security.UserPrincipal;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SubscriptionEnforcementFilterTest {

    private final SubscriptionAccessService accessService = mock(SubscriptionAccessService.class);

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    private void authenticateAs(UserRole role, Long restaurantId) {
        UserPrincipal p = new UserPrincipal(1L, "user@test.com", "pw", role, true, restaurantId);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities()));
    }

    /** A waiter session: plain UserDetails with ROLE_WAITER, tenant carried in TenantContext. */
    private void authenticateAsWaiter(Long restaurantId) {
        var waiter = User.builder().username("waiter@1").password("x")
                .authorities(new SimpleGrantedAuthority("ROLE_WAITER")).build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(waiter, null, waiter.getAuthorities()));
        if (restaurantId != null) {
            TenantContext.setRestaurantId(restaurantId);
        }
    }

    private void authenticateAsConsumer(Long restaurantId) {
        var consumer = User.builder().username("+998900000000").password("x")
                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")).build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(consumer, null, consumer.getAuthorities()));
        if (restaurantId != null) {
            TenantContext.setRestaurantId(restaurantId);
        }
    }

    private MockHttpServletRequest get(String uri) {
        return new MockHttpServletRequest("GET", uri);
    }

    private SubscriptionEnforcementFilter filter(String mode) {
        return new SubscriptionEnforcementFilter(mode, accessService);
    }

    @Test
    @DisplayName("OFF → no-op even for a suspended tenant")
    void off_noop() throws Exception {
        authenticateAs(UserRole.ADMIN, 5L);
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter("off").doFilter(get("/api/v1/orders"), res, chain);

        verify(chain, times(1)).doFilter(any(), any());
        assertThat(res.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("ENFORCE + suspended staff → 402 SUBSCRIPTION_INACTIVE, chain not called")
    void enforce_suspended_blocks() throws Exception {
        authenticateAs(UserRole.ADMIN, 5L);
        when(accessService.isSuspended(5L)).thenReturn(true);
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter("enforce").doFilter(get("/api/v1/orders"), res, chain);

        verify(chain, never()).doFilter(any(), any());
        assertThat(res.getStatus()).isEqualTo(402);
        assertThat(res.getContentAsString()).contains("SUBSCRIPTION_INACTIVE");
    }

    @Test
    @DisplayName("ENFORCE + active staff → passes")
    void enforce_active_passes() throws Exception {
        authenticateAs(UserRole.ADMIN, 5L);
        when(accessService.isSuspended(5L)).thenReturn(false);
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter("enforce").doFilter(get("/api/v1/orders"), res, chain);

        verify(chain, times(1)).doFilter(any(), any());
        assertThat(res.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("ENFORCE + SUPER_ADMIN → passes (platform operator)")
    void enforce_superAdmin_passes() throws Exception {
        authenticateAs(UserRole.SUPER_ADMIN, null);
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter("enforce").doFilter(get("/api/v1/orders"), res, chain);

        verify(chain, times(1)).doFilter(any(), any());
        assertThat(res.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("ENFORCE + billing path → passes even when suspended (self-remediation)")
    void enforce_allowlist_passes() throws Exception {
        authenticateAs(UserRole.ADMIN, 5L);
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter("enforce").doFilter(get("/api/v1/billing/me"), res, chain);

        verify(chain, times(1)).doFilter(any(), any());
        assertThat(res.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("ENFORCE + no staff principal (consumer / unauthenticated) → passes")
    void enforce_noPrincipal_passes() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter("enforce").doFilter(get("/api/v1/orders"), res, chain);

        verify(chain, times(1)).doFilter(any(), any());
        assertThat(res.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("SHADOW + suspended → logs but passes through")
    void shadow_suspended_passes() throws Exception {
        authenticateAs(UserRole.ADMIN, 5L);
        when(accessService.isSuspended(5L)).thenReturn(true);
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter("shadow").doFilter(get("/api/v1/orders"), res, chain);

        verify(chain, times(1)).doFilter(any(), any());
        assertThat(res.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("ENFORCE + suspended waiter → 402 (POS is cut off too)")
    void enforce_suspendedWaiter_blocks() throws Exception {
        authenticateAsWaiter(5L);
        when(accessService.isSuspended(5L)).thenReturn(true);
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter("enforce").doFilter(get("/api/v1/pos/orders"), res, chain);

        verify(chain, never()).doFilter(any(), any());
        assertThat(res.getStatus()).isEqualTo(402);
        assertThat(res.getContentAsString()).contains("SUBSCRIPTION_INACTIVE");
    }

    @Test
    @DisplayName("ENFORCE + active waiter → passes")
    void enforce_activeWaiter_passes() throws Exception {
        authenticateAsWaiter(5L);
        when(accessService.isSuspended(5L)).thenReturn(false);
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter("enforce").doFilter(get("/api/v1/pos/orders"), res, chain);

        verify(chain, times(1)).doFilter(any(), any());
        assertThat(res.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("ENFORCE + consumer (tenant-bound) → passes; consumers aren't gated")
    void enforce_consumer_passes() throws Exception {
        authenticateAsConsumer(5L);
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter("enforce").doFilter(get("/api/v1/orders"), res, chain);

        verify(chain, times(1)).doFilter(any(), any());
        assertThat(res.getStatus()).isEqualTo(200);
    }
}
