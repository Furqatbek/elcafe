package com.elcafe.modules.billing.interceptor;

import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.modules.billing.service.PlanGateService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

/**
 * Read-only mode (mini-phase A5). Once a restaurant's plan has lapsed past the grace window its staff
 * may still read but not create or accept new work. This interceptor enforces that on every
 * state-changing request by calling {@link PlanGateService#requireWriteAccess(Long)}, which throws
 * {@link com.elcafe.exception.ForbiddenException}{@code ("plan.read_only")} — mapped to HTTP 403 by
 * {@code GlobalExceptionHandler} — when the tenant is read-only.
 *
 * <p>Scope is deliberately narrow:
 * <ul>
 *   <li>Only mutating methods (POST/PUT/PATCH/DELETE); GETs always pass.</li>
 *   <li>{@code /auth} and {@code /billing} are always allowed, so an expired tenant can still log in
 *       and view/renew its plan; {@code /actuator} is infra.</li>
 *   <li>SUPER_ADMIN (platform operator) is never gated.</li>
 *   <li>Only authenticated staff carry a resolvable restaurant here; unauthenticated, waiter,
 *       consumer and webhook requests resolve to {@code null} and pass through.</li>
 * </ul>
 *
 * <p>Inert until a restaurant actually has a lapsed plan ({@code isReadOnly} is false when there is no
 * plan), so registering it unconditionally is a no-op for every restaurant today.
 */
@Component
@RequiredArgsConstructor
public class PlanWriteGuardInterceptor implements HandlerInterceptor {

    private static final Set<String> MUTATING_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final PlanGateService planGateService;
    private final RestaurantAuthorizationService authorizationService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!MUTATING_METHODS.contains(request.getMethod())) {
            return true;
        }
        String uri = request.getRequestURI();
        if (uri != null && (uri.contains("/auth/") || uri.contains("/billing/") || uri.contains("/actuator/"))) {
            return true;
        }
        // SUPER_ADMIN (platform operator) is never plan-gated.
        if (authorizationService.isAdmin()) {
            return true;
        }
        // Only staff (UserPrincipal) resolve a restaurant here; waiter/consumer/unauth/webhook → null.
        Long restaurantId = authorizationService.getCurrentUserRestaurantId();
        if (restaurantId == null) {
            return true;
        }
        // Throws ForbiddenException("plan.read_only") (→ 403) when the plan has lapsed past grace.
        planGateService.requireWriteAccess(restaurantId);
        return true;
    }
}
