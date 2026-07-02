package com.elcafe.modules.billing.interceptor;

import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.modules.billing.PlanFeatures;
import com.elcafe.modules.billing.service.PlanGateService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.UrlPathHelper;

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
 * <p>Core modules (POS, basic orders, menu, restaurant, customers, staff, settings) are intentionally
 * <em>not</em> mapped. The consumer/public API ({@code /api/v1/public/**}) is categorically never
 * gated — even when a staff token happens to be attached — so consumer booking/tracking can't break.
 * Self-service stays UI-only gated (it shares its endpoint with core POS takeaway), but the
 * <em>staff-facing</em> reservations management API is gated: consumer booking is public, managing the
 * reservation book is the paid Advance feature. Frontend hiding is UX; this is the independent backend
 * enforcement.
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
            // RFM segments data. Safe to gate: its only consumer is the segments page (the core
            // Customers page uses /customers, not /customers/activity).
            new Rule("/api/v1/customers/activity", true, PlanFeatures.CUSTOMER_SEGMENTS),
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
            new Rule("/consumption-allowances", false, PlanFeatures.STAFF_CONSUMPTION),
            // Staff reservations management (list/create/confirm/cancel). Consumer booking lives under
            // /api/v1/public/** which featureFor never gates (see the guard below), and consumer intake
            // is additionally plan-checked at the source (reservable list + createReservation).
            // /reservation-settings is deliberately NOT gated: the enable/disable off-switch is a core
            // safety valve every tier keeps.
            new Rule("/reservations", false, PlanFeatures.RESERVATIONS));

    private final PlanGateService planGateService;
    private final RestaurantAuthorizationService authorizationService;

    /** The feature code required for a request URI, or {@code null} if the path is not gated. */
    static String featureFor(String uri) {
        if (uri == null) {
            return null;
        }
        // The consumer/public API is never plan-gated, whoever calls it — a staff token attached to a
        // public booking/tracking call must not turn a consumer feature into a paid staff one.
        if (uri.startsWith("/api/v1/public/")) {
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
        // Match on the DECODED path Spring routes by, not the raw request URI — otherwise
        // percent-encoding a letter (/re%73ervations) slips past every rule while MVC still
        // dispatches to the gated handler.
        String path = UrlPathHelper.defaultInstance.getLookupPathForRequest(request);
        String code = featureFor(path);
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
