package com.elcafe.security;

import com.elcafe.common.tenant.TenantContext;
import com.elcafe.modules.auth.enums.UserRole;
import com.elcafe.modules.waiter.entity.Waiter;
import com.elcafe.modules.waiter.repository.WaiterRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JwtAuthenticationFilterTest {

    @Mock private JwtUtil jwtUtil;
    @Mock private UserDetailsService userDetailsService;
    @Mock private WaiterRepository waiterRepository;
    @Mock private FilterChain filterChain;

    private JwtAuthenticationFilter filter;
    private static final String SECRET = "test-secret-key-that-is-at-least-32-characters-long-for-hs256";

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(jwtUtil, userDetailsService, waiterRepository);
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test @DisplayName("valid token — sets authentication in SecurityContext")
    void validToken_setsAuthentication() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtUtil.extractUsername("valid-token")).thenReturn("admin@test.com");
        Claims claims = Jwts.claims().subject("admin@test.com").build();
        when(jwtUtil.extractAllClaims("valid-token")).thenReturn(claims);

        UserPrincipal principal = new UserPrincipal(1L, "admin@test.com", "pass", UserRole.ADMIN, true, 1L);
        when(userDetailsService.loadUserByUsername("admin@test.com")).thenReturn(principal);
        when(jwtUtil.validateToken("valid-token", principal)).thenReturn(true);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("admin@test.com");
        verify(filterChain).doFilter(request, response);
    }

    @Test @DisplayName("no token — passes through without auth")
    void noToken_passesThrough() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test @DisplayName("invalid token — passes through without auth")
    void invalidToken_passesThrough() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer bad-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtUtil.extractUsername("bad-token")).thenThrow(new RuntimeException("Invalid token"));

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test @DisplayName("consumer token — sets CustomerPrincipal")
    void consumerToken_setsCustomerPrincipal() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer consumer-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtUtil.extractUsername("consumer-token")).thenReturn("+998901234567");
        when(jwtUtil.isTokenExpired("consumer-token")).thenReturn(false);
        // Build claims with type=consumer
        Claims claims = Jwts.claims()
                .subject("+998901234567")
                .add("type", "consumer")
                .add("customerId", 1L)
                .build();
        when(jwtUtil.extractAllClaims("consumer-token")).thenReturn(claims);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("+998901234567");
        // The principal must be a CustomerPrincipal carrying the token's customerId, so consumer
        // endpoints can derive identity via @AuthenticationPrincipal instead of trusting client input.
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        assertThat(principal).isInstanceOf(CustomerPrincipal.class);
        assertThat(((CustomerPrincipal) principal).getId()).isEqualTo(1L);
        verify(filterChain).doFilter(request, response);
    }

    @Test @DisplayName("waiter token — binds TenantContext to the waiter's restaurant (§3.6)")
    void waiterToken_bindsTenantContext() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer waiter-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtUtil.extractUsername("waiter-token")).thenReturn("waiter@test.com");
        when(jwtUtil.isTokenExpired("waiter-token")).thenReturn(false);
        Claims claims = Jwts.claims()
                .subject("waiter@test.com")
                .add("type", "waiter")
                .add("role", "WAITER")
                .add("waiterId", 9L)
                .add("restaurantId", 42L)
                .build();
        when(jwtUtil.extractAllClaims("waiter-token")).thenReturn(claims);
        when(waiterRepository.findById(9L)).thenReturn(Optional.of(activeWaiter(0)));

        try {
            filter.doFilterInternal(request, response, filterChain);

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
            assertThat(TenantContext.getRestaurantId()).isEqualTo(42L);
            verify(filterChain).doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    @Test @DisplayName("waiter token — stale tokenVersion is rejected (§3.5 revocation)")
    void waiterToken_staleVersion_rejected() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer waiter-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtUtil.extractUsername("waiter-token")).thenReturn("waiter@test.com");
        when(jwtUtil.isTokenExpired("waiter-token")).thenReturn(false);
        Claims claims = Jwts.claims().subject("waiter@test.com")
                .add("type", "waiter").add("role", "WAITER").add("waiterId", 9L)
                .add("restaurantId", 42L).add("tokenVersion", 1).build();
        when(jwtUtil.extractAllClaims("waiter-token")).thenReturn(claims);
        // Waiter's version has since been bumped past the token's (e.g. PIN changed).
        when(waiterRepository.findById(9L)).thenReturn(Optional.of(activeWaiter(2)));

        try {
            filter.doFilterInternal(request, response, filterChain);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            assertThat(TenantContext.getRestaurantId()).isNull();
            verify(filterChain).doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    @Test @DisplayName("waiter token — deactivated waiter is rejected")
    void waiterToken_inactiveWaiter_rejected() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer waiter-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtUtil.extractUsername("waiter-token")).thenReturn("waiter@test.com");
        when(jwtUtil.isTokenExpired("waiter-token")).thenReturn(false);
        Claims claims = Jwts.claims().subject("waiter@test.com")
                .add("type", "waiter").add("role", "WAITER").add("waiterId", 9L)
                .add("restaurantId", 42L).add("tokenVersion", 0).build();
        when(jwtUtil.extractAllClaims("waiter-token")).thenReturn(claims);
        Waiter inactive = activeWaiter(0);
        inactive.setActive(false);
        when(waiterRepository.findById(9L)).thenReturn(Optional.of(inactive));

        filter.doFilterInternal(request, response, filterChain);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test @DisplayName("waiter token — unknown waiterId is rejected")
    void waiterToken_unknownWaiter_rejected() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer waiter-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtUtil.extractUsername("waiter-token")).thenReturn("waiter@test.com");
        when(jwtUtil.isTokenExpired("waiter-token")).thenReturn(false);
        Claims claims = Jwts.claims().subject("waiter@test.com")
                .add("type", "waiter").add("role", "WAITER").add("waiterId", 9L).build();
        when(jwtUtil.extractAllClaims("waiter-token")).thenReturn(claims);
        when(waiterRepository.findById(9L)).thenReturn(Optional.empty());

        filter.doFilterInternal(request, response, filterChain);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    /** An active waiter (id 9, restaurant 42) at the given token version. */
    private static Waiter activeWaiter(int tokenVersion) {
        Waiter w = new Waiter();
        w.setId(9L);
        w.setActive(true);
        w.setRestaurantId(42L);
        w.setTokenVersion(tokenVersion);
        return w;
    }
}
