package com.elcafe.common.tenant;

import com.elcafe.modules.auth.enums.UserRole;
import com.elcafe.security.UserPrincipal;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class TenantEnforcementFilterTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    private void authenticateAs(UserRole role, Long restaurantId) {
        UserPrincipal principal = new UserPrincipal(1L, "user@test.com", "pw", role, true, restaurantId);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private MockHttpServletRequest get(String uri) {
        return new MockHttpServletRequest("GET", uri);
    }

    @Test
    @DisplayName("same-tenant path → allowed, no violation, context cleared")
    void sameTenantPath_allowed() throws Exception {
        authenticateAs(UserRole.ADMIN, 1L);
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse response = new MockHttpServletResponse();

        new TenantEnforcementFilter("shadow").doFilter(get("/api/v1/restaurants/1/orders"), response, chain);

        verify(chain, times(1)).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(TenantContext.getRestaurantId()).isNull(); // cleared in finally
    }

    @Test
    @DisplayName("cross-tenant path in SHADOW → allowed through, not blocked")
    void crossTenantPath_shadow_allowsThrough() throws Exception {
        authenticateAs(UserRole.ADMIN, 1L);
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse response = new MockHttpServletResponse();

        new TenantEnforcementFilter("shadow").doFilter(get("/api/v1/restaurants/2/orders"), response, chain);

        verify(chain, times(1)).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("cross-tenant path in ENFORCE → 403, chain not called")
    void crossTenantPath_enforce_blocks() throws Exception {
        authenticateAs(UserRole.ADMIN, 1L);
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse response = new MockHttpServletResponse();

        new TenantEnforcementFilter("enforce").doFilter(get("/api/v1/restaurants/2/orders"), response, chain);

        verify(chain, never()).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("TENANT_ACCESS_DENIED");
    }

    @Test
    @DisplayName("cross-tenant query param in ENFORCE → 403")
    void crossTenantQueryParam_enforce_blocks() throws Exception {
        authenticateAs(UserRole.MANAGER, 1L);
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockHttpServletRequest request = get("/api/v1/financial/reports");
        request.setParameter("restaurantId", "9");

        new TenantEnforcementFilter("enforce").doFilter(request, response, chain);

        verify(chain, never()).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("SUPER_ADMIN cross-tenant → allowed (platform operator)")
    void superAdmin_crossTenant_allowed() throws Exception {
        authenticateAs(UserRole.SUPER_ADMIN, 1L);
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse response = new MockHttpServletResponse();

        new TenantEnforcementFilter("enforce").doFilter(get("/api/v1/restaurants/2/orders"), response, chain);

        verify(chain, times(1)).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("caller with no assigned restaurant accessing a tenant in ENFORCE → 403")
    void noTenantCaller_enforce_blocks() throws Exception {
        authenticateAs(UserRole.OWNER, null); // self-registered owner, no restaurant yet
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse response = new MockHttpServletResponse();

        new TenantEnforcementFilter("enforce").doFilter(get("/api/v1/restaurants/5/menu"), response, chain);

        verify(chain, never()).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("unauthenticated request → not evaluated, passes through")
    void unauthenticated_passesThrough() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse response = new MockHttpServletResponse();

        new TenantEnforcementFilter("enforce").doFilter(get("/api/v1/restaurants/2/orders"), response, chain);

        verify(chain, times(1)).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("OFF mode → no-op even on cross-tenant access")
    void offMode_noop() throws Exception {
        authenticateAs(UserRole.ADMIN, 1L);
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse response = new MockHttpServletResponse();

        new TenantEnforcementFilter("off").doFilter(get("/api/v1/restaurants/2/orders"), response, chain);

        verify(chain, times(1)).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("non-numeric restaurant path segment (e.g. /restaurants/active) is ignored")
    void nonNumericPath_ignored() throws Exception {
        authenticateAs(UserRole.ADMIN, 1L);
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse response = new MockHttpServletResponse();

        new TenantEnforcementFilter("enforce").doFilter(get("/api/v1/restaurants/active"), response, chain);

        verify(chain, times(1)).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("no-restaurant caller on a non-tenant path in ENFORCE → bound to deny-all sentinel")
    void noTenantCaller_enforce_bindsSentinel() throws Exception {
        authenticateAs(UserRole.ADMIN, null); // tenant-scoped role, but no restaurant assigned
        MockHttpServletResponse response = new MockHttpServletResponse();
        Long[] boundDuringChain = new Long[1];
        // The request carries no restaurantId, so there is no edge violation; capture what the
        // backstop would see for this caller mid-chain (the filter clears the context in finally).
        FilterChain chain = (req, res) -> boundDuringChain[0] = TenantContext.getRestaurantId();

        new TenantEnforcementFilter("enforce").doFilter(get("/api/v1/system-users"), response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(boundDuringChain[0]).isEqualTo(TenantContext.NO_ACCESS); // scoped to nothing
    }
}
