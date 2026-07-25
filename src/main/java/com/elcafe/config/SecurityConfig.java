package com.elcafe.config;

import com.elcafe.common.tenant.TenantEnforcementFilter;
import com.elcafe.modules.billing.enforcement.SubscriptionEnforcementFilter;
import com.elcafe.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final TenantEnforcementFilter tenantEnforcementFilter;
    private final SubscriptionEnforcementFilter subscriptionEnforcementFilter;
    private final UserDetailsService userDetailsService;
    private final com.elcafe.security.RestAuthenticationEntryPoint restAuthenticationEntryPoint;
    private final com.elcafe.security.RestAccessDeniedHandler restAccessDeniedHandler;

    @Value("${app.security.cors.allowed-origins}")
    private String allowedOrigins;

    @Value("${app.security.cors.allowed-methods}")
    private String allowedMethods;

    @Value("${app.security.cors.allowed-headers}")
    private String allowedHeaders;

    @Value("${app.security.cors.allow-credentials}")
    private boolean allowCredentials;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints
                        .requestMatchers(
                                "/api/v1/auth/**",
                                "/api/v1/consumer/auth/**",
                                "/api/v1/customer/public/**",
                                "/api/v1/waiters/auth",
                                "/api/v1/menu/public/**",
                                "/api/v1/courier/webhook/**",
                                "/api/v1/webhook/wallet/**",
                                // Meta calls this with no credentials of ours. It authenticates
                                // itself: the GET handshake must present a restaurant's verify
                                // token, and every POST must carry a valid X-Hub-Signature-256 HMAC
                                // under that restaurant's app secret. Both fail closed
                                // (InstagramWebhookController / InstagramWebhookService).
                                "/api/v1/instagram/webhook/**",
                                "/api/v1/instagram/webhook",
                                "/api/v1/self-service/**",  // Self-service ordering (QR code)
                                "/api/v1/public/**",        // Public reservation endpoints
                                "/api/public/**",           // Public order tracking endpoints
                                "/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                // health/** = the liveness/readiness probe groups the container
                                // healthcheck polls. Anonymous callers see status only (show-details
                                // is when-authorized, `never` in prod) — no dependency detail leaks.
                                "/actuator/health",
                                "/actuator/health/**",
                                "/actuator/info",
                                "/uploads/**",
                                "/ws-waiter/**",            // WebSocket endpoint for waiter updates
                                "/ws-print-agent/**",       // WebSocket endpoint for print agent
                                "/error"                    // Spring Boot error page
                        ).permitAll()
                        // Allow public read access to menu/categories/products for POS
                        .requestMatchers(HttpMethod.GET, "/api/v1/menu/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/categories").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/products/restaurant/**").permitAll()
                        // Allow GET requests to restaurants and tables for all authenticated users
                        .requestMatchers(HttpMethod.GET, "/api/v1/restaurants/**").authenticated()
                        // POS shift endpoints — open to all authenticated staff
                        .requestMatchers("/api/v1/restaurants/*/pos/**").authenticated()
                        // Employee consumptions — open to all authenticated staff
                        .requestMatchers("/api/v1/restaurants/*/employee-consumptions/**").authenticated()
                        // Promotion check-cart — open to all authenticated staff
                        .requestMatchers("/api/v1/restaurants/*/promotions/**").authenticated()
                        // Admin only endpoints
                        .requestMatchers(HttpMethod.POST, "/api/v1/restaurants/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/restaurants/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/restaurants/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/menu/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/menu/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/menu/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/financial/purchase-orders/**").hasAnyRole("ADMIN", "OPERATOR", "WAITER")
                        .requestMatchers("/api/v1/financial/expenses/**").hasAnyRole("ADMIN", "OPERATOR", "WAITER")
                        .requestMatchers("/api/v1/financial/payroll/**").hasRole("ADMIN")
                        // Financial reports accessible to authenticated users
                        .requestMatchers("/api/v1/financial/reports/**").authenticated()
                        // Authenticated endpoints
                        .anyRequest().authenticated()
                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                // EH-0.4: JSON envelope from the security boundary. Without these, unauthenticated
                // requests got the framework default (empty-body 403) instead of a 401 the client
                // can act on, and filter-layer denials had no body at all.
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(restAuthenticationEntryPoint)
                        .accessDeniedHandler(restAccessDeniedHandler)
                )
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                // Tenant isolation runs right after authentication, so the principal is available.
                .addFilterAfter(tenantEnforcementFilter, JwtAuthenticationFilter.class)
                // Phase 2 subscription gate runs after tenant resolution (principal + tenant available);
                // 402s a suspended tenant's staff when app.subscription.enforcement.mode=enforce.
                .addFilterAfter(subscriptionEnforcementFilter, TenantEnforcementFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Use allowedOriginPatterns instead of allowedOrigins when credentials are enabled
        configuration.setAllowedOriginPatterns(Arrays.asList(allowedOrigins.split(",")));
        configuration.setAllowedMethods(Arrays.asList(allowedMethods.split(",")));
        configuration.setAllowedHeaders(Arrays.asList(allowedHeaders.split(",")));
        configuration.setAllowCredentials(allowCredentials);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Role hierarchy: SUPER_ADMIN (the platform operator) inherits every ADMIN authority.
     *
     * <p>This is what lets the new SUPER_ADMIN role satisfy all pre-existing
     * {@code hasRole('ADMIN')} / {@code @PreAuthorize("hasRole('ADMIN')")} checks across the
     * ~85 controllers without editing any of them. Spring Security 6.3+ auto-applies this bean
     * to both web authorization and method security.
     *
     * <p>NOTE: this governs <em>endpoint authorization</em> only. Cross-tenant <em>data</em>
     * access remains gated by {@link com.elcafe.common.security.service.RestaurantAuthorizationService},
     * which grants the cross-restaurant bypass to SUPER_ADMIN exclusively.
     */
    @Bean
    static RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.fromHierarchy("ROLE_SUPER_ADMIN > ROLE_ADMIN");
    }

    @Bean
    static MethodSecurityExpressionHandler methodSecurityExpressionHandler(RoleHierarchy roleHierarchy) {
        DefaultMethodSecurityExpressionHandler handler = new DefaultMethodSecurityExpressionHandler();
        handler.setRoleHierarchy(roleHierarchy);
        return handler;
    }
}
