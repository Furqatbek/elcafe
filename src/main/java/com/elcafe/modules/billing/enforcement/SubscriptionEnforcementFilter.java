package com.elcafe.modules.billing.enforcement;

import com.elcafe.common.tenant.TenantContext;
import com.elcafe.modules.auth.enums.UserRole;
import com.elcafe.modules.billing.service.SubscriptionAccessService;
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
import java.util.List;

/**
 * Phase 2 subscription access gate. Runs after tenant resolution; for an authenticated staff request
 * ({@link UserPrincipal}) whose tenant a SUPER_ADMIN has suspended, it returns
 * {@code 402 SUBSCRIPTION_INACTIVE} — making the platform-console "Suspend" actually cut the tenant off
 * (today it only drops the tenant from public listings).
 *
 * <p>Governed by {@code app.subscription.enforcement.mode} ({@link SubscriptionEnforcementMode}); ships
 * {@code off}. In {@code shadow} it logs would-be blocks without acting. Always passes: SUPER_ADMIN, an
 * allowlist (auth, billing, platform, health) so a suspended admin can still log in and see billing, and
 * anything without a tenant (consumer / public / unauthenticated). Fail-open on its own errors so a bug
 * here can never take down request processing.
 *
 * <p>Scope note: expired-not-suspended plans stay in read-only mode (not gated here). Both tenant-bound
 * staff types are gated — regular staff ({@link UserPrincipal}) and waiters ({@code ROLE_WAITER}, whose
 * restaurant lives in {@link TenantContext}) — so suspension cuts off the POS too. Consumers are not
 * gated (their booking/ordering backends are public and the suspended restaurant already drops from
 * listings).
 */
@Component
public class SubscriptionEnforcementFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionEnforcementFilter.class);

    /** Paths a suspended tenant's staff must still reach (log in, view billing, health). */
    private static final List<String> ALLOWLIST = List.of(
            "/api/v1/auth/", "/api/v1/billing/", "/api/v1/platform/", "/actuator/");

    private final SubscriptionEnforcementMode mode;
    private final SubscriptionAccessService accessService;

    public SubscriptionEnforcementFilter(
            @Value("${app.subscription.enforcement.mode:off}") String mode,
            SubscriptionAccessService accessService) {
        this.mode = SubscriptionEnforcementMode.from(mode);
        this.accessService = accessService;
        log.info("SubscriptionEnforcementFilter initialised in {} mode", this.mode);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (mode == SubscriptionEnforcementMode.OFF) {
            chain.doFilter(request, response);
            return;
        }
        boolean suspended = false;
        try {
            suspended = shouldBlock(request);
        } catch (Exception e) {
            // Never let a filter bug break the request.
            log.error("SubscriptionEnforcementFilter error; allowing request to proceed", e);
        }

        if (suspended) {
            if (mode == SubscriptionEnforcementMode.ENFORCE) {
                writePaymentRequired(request, response);
                return;
            }
            log.warn("[subscription-shadow] would block {} {} for suspended tenant",
                    request.getMethod(), request.getRequestURI());
        }
        chain.doFilter(request, response);
    }

    private boolean shouldBlock(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri != null) {
            for (String allowed : ALLOWLIST) {
                if (uri.startsWith(allowed)) {
                    return false;
                }
            }
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return false;
        }
        Long tenant = tenantToGate(auth);
        return tenant != null && accessService.isSuspended(tenant);
    }

    /**
     * The tenant to check for suspension, or {@code null} for callers this gate doesn't apply to.
     * Covers regular staff ({@link UserPrincipal}, tenant on the principal) and waiters ({@code
     * ROLE_WAITER}, tenant in {@link TenantContext} — still populated here because the tenant filter
     * clears it only after the downstream chain returns). SUPER_ADMIN, consumers, and public /
     * unauthenticated requests are not gated.
     */
    private Long tenantToGate(Authentication auth) {
        if (auth.getPrincipal() instanceof UserPrincipal staff) {
            return staff.getRole() == UserRole.SUPER_ADMIN ? null : staff.getRestaurantId();
        }
        boolean isWaiter = auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_WAITER".equals(a.getAuthority()));
        return isWaiter ? TenantContext.getRestaurantId() : null;
    }

    private void writePaymentRequired(HttpServletRequest request, HttpServletResponse response) throws IOException {
        log.warn("[subscription-enforce] BLOCKED {} {} for suspended tenant",
                request.getMethod(), request.getRequestURI());
        response.setStatus(HttpServletResponse.SC_PAYMENT_REQUIRED); // 402
        response.setContentType("application/json");
        response.getWriter().write(
                "{\"error\":\"SUBSCRIPTION_INACTIVE\",\"status\":\"SUSPENDED\","
                        + "\"message\":\"This restaurant's access has been suspended. Please contact support.\"}");
    }
}
