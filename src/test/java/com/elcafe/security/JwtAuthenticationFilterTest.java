package com.elcafe.security;

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

    @Test @DisplayName("disabled account (e.g. UNKNOWN role) — token is rejected, no auth set")
    void disabledAccount_tokenRejected() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtUtil.extractUsername("valid-token")).thenReturn("ghost@test.com");
        Claims claims = Jwts.claims().subject("ghost@test.com").build();
        when(jwtUtil.extractAllClaims("valid-token")).thenReturn(claims);

        // Disabled principal (active=false stands in for any isEnabled()==false,
        // including UNKNOWN role). validateToken would pass, but isEnabled gates it.
        UserPrincipal disabled = new UserPrincipal(9L, "ghost@test.com", "pw", UserRole.OPERATOR, false, 1L);
        when(userDetailsService.loadUserByUsername("ghost@test.com")).thenReturn(disabled);
        when(jwtUtil.validateToken("valid-token", disabled)).thenReturn(true);

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
        verify(filterChain).doFilter(request, response);
    }

    /* ------------------------------------------------------------------ */
    /* Waiter tokens                                                      */
    /* ------------------------------------------------------------------ */

    private MockHttpServletRequest waiterTokenRequest(Long waiterId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer waiter-token");
        when(jwtUtil.extractUsername("waiter-token")).thenReturn("shoxrux@qahvoon.uz");
        Claims claims = Jwts.claims()
                .subject("shoxrux@qahvoon.uz")
                .add("type", "waiter")
                .add("role", "HEAD_WAITER")
                .add("waiterId", waiterId)
                .build();
        when(jwtUtil.extractAllClaims("waiter-token")).thenReturn(claims);
        when(jwtUtil.isTokenExpired("waiter-token")).thenReturn(false);
        return request;
    }

    private static Waiter waiter(Long id, boolean active) {
        Waiter w = new Waiter();
        w.setId(id);
        w.setName("Shoxrux");
        w.setActive(active);
        return w;
    }

    @Test @DisplayName("waiter token — active waiter is authenticated")
    void waiterToken_activeWaiter_authenticated() throws ServletException, IOException {
        MockHttpServletRequest request = waiterTokenRequest(3L);
        when(waiterRepository.findById(3L)).thenReturn(Optional.of(waiter(3L, true)));

        filter.doFilterInternal(request, new MockHttpServletResponse(), filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting(Object::toString)
                .contains("ROLE_WAITER", "ROLE_HEAD_WAITER");
    }

    @Test @DisplayName("waiter token — deactivated waiter is rejected even with a valid token")
    void waiterToken_deactivatedWaiter_rejected() throws ServletException, IOException {
        // Waiter tokens are issued with a 10000-year expiry, so one minted
        // before the account was switched off stays structurally valid forever.
        // Authenticating from the claims alone left a deactivated waiter logged
        // in indefinitely.
        MockHttpServletRequest request = waiterTokenRequest(3L);
        when(waiterRepository.findById(3L)).thenReturn(Optional.of(waiter(3L, false)));

        filter.doFilterInternal(request, new MockHttpServletResponse(), filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(any(), any());
    }

    @Test @DisplayName("waiter token — deleted waiter is rejected")
    void waiterToken_missingWaiter_rejected() throws ServletException, IOException {
        MockHttpServletRequest request = waiterTokenRequest(3L);
        when(waiterRepository.findById(3L)).thenReturn(Optional.empty());

        filter.doFilterInternal(request, new MockHttpServletResponse(), filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
