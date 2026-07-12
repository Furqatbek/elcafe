package com.elcafe.common.tenant;

import com.elcafe.modules.auth.enums.UserRole;
import com.elcafe.security.UserPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Central tenant-isolation choke point (Phase 0 §3.3).
 *
 * <p>Runs immediately after JWT authentication. It derives the caller's tenant from the
 * authenticated {@link UserPrincipal} and compares it against every {@code restaurantId} the
 * request carries in its path ({@code /restaurants/{id}/...}) or query string
 * ({@code ?restaurantId=}). A mismatch means the caller is reaching into another tenant's data.
 *
 * <p>This is a broad, central net — it does not replace per-endpoint authorization
 * ({@code RestaurantAuthorizationService}), which remains the precise check. It exists so that
 * the ~104 controllers that still trust a client-supplied {@code restaurantId} are covered while
 * they are retrofitted one by one.
 *
 * <p>Behaviour is governed by {@code app.security.tenant-enforcement.mode}:
 * <ul>
 *   <li>{@code off} — disabled (no-op).</li>
 *   <li>{@code shadow} — logs violations under {@code [tenant-shadow]} but allows the request
 *       through. Use to observe production traffic before enforcing.</li>
 *   <li>{@code enforce} — additionally returns {@code 403 TENANT_ACCESS_DENIED}.</li>
 * </ul>
 *
 * <p>Token types not yet tenant-bound (waiter, consumer) and unauthenticated requests are not
 * evaluated here — that binding is future work (§3.6/§3.7). The filter is fail-open on its own
 * internal errors so a bug can never take down request processing; genuine violations are still
 * blocked in {@code enforce} mode.
 */
@Component
public class TenantEnforcementFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TenantEnforcementFilter.class);

    /** Matches /restaurant/{id} or /restaurants/{id} where id is numeric. */
    private static final Pattern RESTAURANT_PATH = Pattern.compile("/restaurants?/(\\d+)");

    private final TenantEnforcementMode mode;

    public TenantEnforcementFilter(
            @Value("${app.security.tenant-enforcement.mode:shadow}") String mode) {
        this.mode = TenantEnforcementMode.from(mode);
        log.info("TenantEnforcementFilter initialised in {} mode", this.mode);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            if (mode == TenantEnforcementMode.OFF) {
                chain.doFilter(request, response);
                return;
            }

            Violation violation = null;
            try {
                violation = detect(request);
            } catch (Exception e) {
                // Never let a filter bug break the request. Precise enforcement still happens
                // in the controller guard.
                log.error("TenantEnforcementFilter error; allowing request to proceed", e);
            }

            if (violation != null) {
                if (mode == TenantEnforcementMode.ENFORCE) {
                    writeForbidden(request, response, violation);
                    return;
                }
                log.warn("[tenant-shadow] user={} tenant={} attempted restaurantId={} on {} {}",
                        violation.email(), violation.callerTenant(), violation.offendingId(),
                        request.getMethod(), request.getRequestURI());
            }

            chain.doFilter(request, response);
        } finally {
            // Always clear, even in OFF mode: JwtAuthenticationFilter may populate TenantContext for
            // waiter tokens (§3.6) regardless of this filter's mode, and it must not leak across the
            // pooled request thread.
            TenantContext.clear();
        }
    }

    /**
     * Resolves the caller's tenant, populates {@link TenantContext}, and returns a
     * {@link Violation} if the request references a different restaurant — otherwise {@code null}.
     */
    private Violation detect(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal principal)) {
            // Unauthenticated, or a waiter/consumer token (plain UserDetails) which is not yet
            // tenant-bound. Not evaluated here.
            return null;
        }

        if (principal.getRole() == UserRole.SUPER_ADMIN) {
            // Platform operator: legitimately cross-tenant. Scope the context to whatever was
            // requested (may be null for aggregate queries).
            TenantContext.setRestaurantId(firstRestaurantId(request));
            return null;
        }

        Long callerTenant = principal.getRestaurantId();
        // A tenant-scoped caller with no assigned restaurant must not bind a *null* (unscoped)
        // context — the §3.4 backstop reads null as "see everything". Bind the deny-all sentinel so
        // the Hibernate filter and every TenantContext reader scope them to nothing under enforce.
        TenantContext.setRestaurantId(callerTenant != null ? callerTenant : TenantContext.NO_ACCESS);

        for (Long requested : requestedRestaurantIds(request)) {
            // A caller with no assigned restaurant (callerTenant == null) has no business
            // touching any restaurant's data — flag every referenced id.
            if (callerTenant == null || !callerTenant.equals(requested)) {
                return new Violation(principal.getEmail(), callerTenant, requested);
            }
        }
        return null;
    }

    private Set<Long> requestedRestaurantIds(HttpServletRequest request) {
        Set<Long> ids = new LinkedHashSet<>();

        String uri = request.getRequestURI();
        if (uri != null) {
            Matcher matcher = RESTAURANT_PATH.matcher(uri);
            while (matcher.find()) {
                parseLong(matcher.group(1)).ifPresent(ids::add);
            }
        }

        String param = request.getParameter("restaurantId");
        if (param != null && !param.isBlank()) {
            parseLong(param.trim()).ifPresent(ids::add);
        }
        return ids;
    }

    private Long firstRestaurantId(HttpServletRequest request) {
        Set<Long> ids = requestedRestaurantIds(request);
        return ids.isEmpty() ? null : ids.iterator().next();
    }

    private static java.util.Optional<Long> parseLong(String value) {
        try {
            return java.util.Optional.of(Long.parseLong(value));
        } catch (NumberFormatException e) {
            return java.util.Optional.empty();
        }
    }

    private void writeForbidden(HttpServletRequest request, HttpServletResponse response, Violation violation)
            throws IOException {
        log.warn("[tenant-enforce] BLOCKED user={} tenant={} attempted restaurantId={} on {} {}",
                violation.email(), violation.callerTenant(), violation.offendingId(),
                request.getMethod(), request.getRequestURI());
        com.elcafe.exception.ApiErrorWriter.write(response, HttpServletResponse.SC_FORBIDDEN,
                com.elcafe.exception.ErrorCode.TENANT_ACCESS_DENIED,
                "You do not have access to this restaurant's data.");
    }

    private record Violation(String email, Long callerTenant, Long offendingId) {
    }
}
