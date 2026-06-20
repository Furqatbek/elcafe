package com.elcafe.security;

import com.elcafe.common.tenant.TenantContext;
import com.elcafe.modules.waiter.entity.Waiter;
import com.elcafe.modules.waiter.repository.WaiterRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Lazy;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;
    private final WaiterRepository waiterRepository;

    public JwtAuthenticationFilter(JwtUtil jwtUtil, @Lazy UserDetailsService userDetailsService,
                                   @Lazy WaiterRepository waiterRepository) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
        this.waiterRepository = waiterRepository;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        final String username;

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        jwt = authHeader.substring(7);
        try {
            username = jwtUtil.extractUsername(jwt);
            logger.debug("Extracted username from JWT: " + username);

            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                // Check if this is a waiter token
                Claims claims = jwtUtil.extractAllClaims(jwt);
                String tokenType = claims.get("type", String.class);

                if ("waiter".equals(tokenType)) {
                    // Handle waiter authentication
                    String role = claims.get("role", String.class);
                    Long waiterId = claims.get("waiterId", Long.class);
                    logger.debug("Processing waiter token - role: " + role + ", waiterId: " + waiterId);

                    // §3.5: waiter access tokens are long-lived (30d) with no refresh flow, so
                    // revocation is enforced per request. Load the waiter and reject the token if the
                    // waiter is gone, deactivated, or its tokenVersion was bumped (PIN change /
                    // deactivation in WaiterService).
                    Waiter waiter = (waiterId != null) ? waiterRepository.findById(waiterId).orElse(null) : null;

                    if (role != null && waiter != null && jwtUtil.isTokenExpired(jwt) == false
                            && Boolean.TRUE.equals(waiter.getActive())
                            && tokenVersionMatches(claims, waiter)) {
                        // Create UserDetails for waiter — always grant ROLE_WAITER
                        // regardless of specific waiter role (JUNIOR_WAITER, SENIOR_WAITER, etc.)
                        List<SimpleGrantedAuthority> authorities = new java.util.ArrayList<>();
                        authorities.add(new SimpleGrantedAuthority("ROLE_WAITER"));
                        authorities.add(new SimpleGrantedAuthority("ROLE_" + role));

                        UserDetails waiterDetails = User.builder()
                                .username(username)
                                .password("{noop}token-auth") // Dummy — not used for token auth
                                .authorities(authorities)
                                .build();

                        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                                waiterDetails,
                                null,
                                waiterDetails.getAuthorities()
                        );
                        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authToken);
                        logger.debug("Waiter authentication set successfully");

                        // §3.6: bind this waiter request to its restaurant so the tenant backstop
                        // (TenantFilterInterceptor) scopes its queries. restaurant_id is NOT NULL since
                        // V151, so prefer the live value over the (possibly stale) claim. Cleared per
                        // request by TenantEnforcementFilter's finally.
                        if (waiter.getRestaurantId() != null) {
                            TenantContext.setRestaurantId(waiter.getRestaurantId());
                        }
                    } else if (waiterId != null) {
                        logger.warn("Waiter token rejected (revoked, inactive, or unknown waiter) for waiterId="
                                + waiterId);
                    }
                } else if ("consumer".equals(tokenType)) {
                    // Handle consumer/customer authentication
                    Long customerId = claims.get("customerId", Long.class);
                    logger.debug("Processing consumer token - customerId: " + customerId);

                    if (customerId != null && jwtUtil.isTokenExpired(jwt) == false) {
                        // Build a CustomerPrincipal carrying the customer id from the token, so consumer
                        // endpoints can derive the authenticated customer via @AuthenticationPrincipal and
                        // never trust a client-supplied id. getUsername() stays the phone (auth.getName()
                        // and the ROLE_CUSTOMER authority are unchanged), so existing lookups keep working.
                        CustomerPrincipal consumerDetails = CustomerPrincipal.create(username, customerId);

                        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                                consumerDetails,
                                null,
                                consumerDetails.getAuthorities()
                        );
                        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authToken);
                        logger.debug("Consumer authentication set successfully for customerId: " + customerId);

                        // V150: bind this consumer request to its restaurant so the tenant backstop
                        // (TenantFilterInterceptor) scopes its queries — same mechanism as waiters
                        // (§3.6). Cleared per request by TenantEnforcementFilter's finally.
                        Long restaurantId = claims.get("restaurantId", Long.class);
                        if (restaurantId != null) {
                            TenantContext.setRestaurantId(restaurantId);
                        }
                    }
                } else {
                    // Handle regular user authentication
                    logger.debug("Processing regular user token");
                    UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                    if (jwtUtil.validateToken(jwt, userDetails)) {
                        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,
                                userDetails.getAuthorities()
                        );
                        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authToken);
                        logger.debug("User authentication set successfully for: " + username);
                    } else {
                        logger.warn("Token validation failed for user: " + username);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Cannot set user authentication: " + e.getMessage(), e);
        }

        filterChain.doFilter(request, response);
    }

    /**
     * §3.5: a waiter token is valid only while its embedded version matches the waiter's current one.
     * A missing claim counts as 0, so tokens issued before V152 stay valid until the first bump.
     */
    private boolean tokenVersionMatches(Claims claims, Waiter waiter) {
        Integer claimVersion = claims.get("tokenVersion", Integer.class);
        int current = waiter.getTokenVersion() == null ? 0 : waiter.getTokenVersion();
        return (claimVersion == null ? 0 : claimVersion) == current;
    }
}
