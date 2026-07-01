package com.elcafe.modules.billing.interceptor;

import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.modules.billing.PlanFeatures;
import com.elcafe.modules.billing.service.PlanGateService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;

/**
 * Backend feature gating (mini-phase A4b). Maps each paid module's API path to its feature code and,
 * for a staff request whose plan lacks that code, throws
 * {@link com.elcafe.exception.ForbiddenException}{@code ("plan.feature_required:<code>")} → HTTP 403.
 *
 * <p>Two deliberate skips keep this safe:
 * <ul>
 *   <li><b>No tenant</b> ({@code getCurrentUserRestaurantId()} is null) → pass. Consumer, courier-app,
 *       webhook and public requests carry no staff tenant, so they are never gated here — which also
 *       sidesteps the one service shared with POS takeaway.</li>
 *   <li><b>No plan</b> → pass (handled in {@link PlanGateService#requireFeatureIfPlanned}). Production
 *       restaurants always have a plan (plan_id NOT NULL since V156); a null plan only occurs in
 *       tests / unseeded setups, where gating must not fire.</li>
 * </ul>
 *
 * <p>Core modules (POS, basic orders, menu, restaurant, customers, staff, settings) and consumer-facing
 * features whose backend is public (reservations booking, self-service) are intentionally <em>not</em>
 * mapped — they are hidden in the sidebar (A4c) rather than 403'd here. Frontend hiding is UX; this is
 * the independent backend enforcement.
 */
@Component
@RequiredArgsConstructor
public class PlanFeatureGuardInterceptor implements HandlerInterceptor {

    /** A path→code rule. {@code prefix} matches {@code uri.startsWith}; otherwise {@code uri.contains}. */
    private record Rule(String token, boolean prefix, String code) {}

    // Ordered: first match wins, so more specific paths precede the general ones.
    private static final List<Rule> RULES = List.of(
            new Rule("/api/v1/inventory/production-batches", true, PlanFeatures.KITCHEN_PRODUCTION),
            new Rule("/api/v1/inventory/po-suggestions", true, PlanFeatures.INVENTORY_PO_SUGGESTIONS),
            new Rule("/api/v1/inventory", true, PlanFeatures.INVENTORY),
            new Rule("/api/v1/ingredients", true, PlanFeatures.INVENTORY),
            new Rule("/api/v1/kitchen/stations", true, PlanFeatures.KITCHEN_STATIONS),
            new Rule("/api/v1/kitchen/orders", true, PlanFeatures.KITCHEN),
            new Rule("/api/v1/dashboard", true, PlanFeatures.ANALYTICS),
            new Rule("/api/v1/analytics", true, PlanFeatures.ANALYTICS),
            new Rule("/api/v1/menu-collections", true, PlanFeatures.MENU_COLLECTIONS),
            new Rule("/api/v1/couriers", true, PlanFeatures.COURIERS),
            new Rule("/api/v1/reviews", true, PlanFeatures.REVIEWS),
            new Rule("/api/v1/loyalty", true, PlanFeatures.LOYALTY),
            new Rule("/api/v1/sms", true, PlanFeatures.MARKETING_SMS),
            new Rule("/api/v1/telegram/subscribers", true, PlanFeatures.TELEGRAM_SUBSCRIBERS),
            new Rule("/api/v1/telegram", true, PlanFeatures.MARKETING_TELEGRAM),
            new Rule("/api/v1/instagram/config", true, PlanFeatures.MARKETING_INSTAGRAM),
            new Rule("/api/v1/instagram/subscribers", true, PlanFeatures.MARKETING_INSTAGRAM),
            new Rule("/api/v1/financial/payroll", true, PlanFeatures.PAYROLL),
            new Rule("/api/v1/financial/salary-config", true, PlanFeatures.PAYROLL),
            new Rule("/api/v1/financial/expenses", true, PlanFeatures.FINANCE),
            new Rule("/api/v1/financial/purchase-orders", true, PlanFeatures.FINANCE),
            new Rule("/api/v1/financial/reports", true, PlanFeatures.FINANCE),
            new Rule("/api/v1/pricing", true, PlanFeatures.FINANCE),
            new Rule("/api/v1/waiter-performance", true, PlanFeatures.STAFF_PERFORMANCE),
            new Rule("/api/v1/qr-codes", true, PlanFeatures.MARKETING),
            // Controllers based at /api/v1 with {restaurantId} in the path — match by segment.
            // The promotion-analytics dashboard is Pro-only (marketing.analytics) — it must precede the
            // general /promotions (Advance) rule so the dashboard endpoints aren't gated one tier too low.
            new Rule("/promotions/analytics", false, PlanFeatures.MARKETING_ANALYTICS),
            new Rule("/promotions", false, PlanFeatures.MARKETING),
            new Rule("/coupons", false, PlanFeatures.MARKETING),
            new Rule("/happy-hours", false, PlanFeatures.MARKETING),
            new Rule("/bundles", false, PlanFeatures.MARKETING),
            new Rule("/milestones", false, PlanFeatures.MARKETING_MILESTONES),
            new Rule("/referrals", false, PlanFeatures.MARKETING_REFERRALS),
            new Rule("/employee-consumptions", false, PlanFeatures.STAFF_CONSUMPTION),
            new Rule("/consumption-allowances", false, PlanFeatures.STAFF_CONSUMPTION));

    private final PlanGateService planGateService;
    private final RestaurantAuthorizationService authorizationService;

    /** The feature code required for a request URI, or {@code null} if the path is not gated. */
    static String featureFor(String uri) {
        if (uri == null) {
            return null;
        }
        for (Rule rule : RULES) {
            boolean match = rule.prefix() ? uri.startsWith(rule.token()) : uri.contains(rule.token());
            if (match) {
                return rule.code();
            }
        }
        return null;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String code = featureFor(request.getRequestURI());
        if (code == null) {
            return true; // not a gated path
        }
        if (authorizationService.isAdmin()) {
            return true; // SUPER_ADMIN (platform operator) is never gated
        }
        Long restaurantId = authorizationService.getCurrentUserRestaurantId();
        if (restaurantId == null) {
            return true; // non-staff (consumer / courier / webhook / unauthenticated)
        }
        // Throws ForbiddenException("plan.feature_required:<code>") (→ 403) if the plan lacks it;
        // no-op when the restaurant has no plan (see PlanGateService#requireFeatureIfPlanned).
        planGateService.requireFeatureIfPlanned(restaurantId, code);
        return true;
    }
}
