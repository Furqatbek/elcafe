package com.elcafe.common.tenant;

import org.slf4j.MDC;

/**
 * Holds the tenant (restaurant) the current request is authoritatively scoped to, derived from
 * the authenticated principal — never from a client-supplied path/query value.
 *
 * <p>Populated by {@link TenantEnforcementFilter} early in the security filter chain and cleared
 * at the end of every request. This is the foundation for centralized tenant isolation: the
 * controller guard ({@code RestaurantAuthorizationService}) and, later, a Hibernate tenant
 * filter (Phase 0 §3.4) read from here instead of trusting request parameters.
 *
 * <p>Thread-bound via {@link ThreadLocal} (one request = one thread under the standard servlet
 * model). The filter's {@code finally} block guarantees cleanup so values never leak across
 * pooled threads.
 *
 * <p>The tenant id is mirrored into the SLF4J {@link MDC} as {@code tenantId} so every log line a
 * scoped request emits carries its tenant (plain patterns can render {@code %X{tenantId}}; the JSON
 * console format includes the MDC wholesale). The mirror follows this holder's lifecycle exactly:
 * set here, removed on {@link #clear()}.
 */
public final class TenantContext {

    /** MDC key under which the current tenant id appears in log output. */
    public static final String MDC_TENANT_KEY = "tenantId";

    private static final ThreadLocal<Long> CURRENT_RESTAURANT_ID = new ThreadLocal<>();

    /**
     * The authenticated waiter's id for the current request, taken from the JWT {@code waiterId} claim
     * (never a client header). Waiter tokens are plain {@code UserDetails} so the id can't ride on the
     * principal; endpoints that act "as the current waiter" read it here instead of trusting a
     * client-supplied {@code X-Waiter-Id}. Null for non-waiter requests. Cleared with the tenant id.
     */
    private static final ThreadLocal<Long> CURRENT_WAITER_ID = new ThreadLocal<>();

    /**
     * A restaurant id that intentionally matches no real row (restaurant ids are positive). Bound as
     * the request's tenant for a tenant-scoped caller that has <em>no assigned restaurant</em>, so the
     * Hibernate {@code @Filter} and query-layer scoping resolve to "see/affect nothing" under
     * {@code enforce} instead of falling through to an unscoped "see everything". Set by
     * {@code TenantEnforcementFilter}; honored by {@code TenantFilterInterceptor},
     * {@code TenantInsertGuard}, and {@code RestaurantAuthorizationService.currentTenantReadScope()}.
     */
    public static final Long NO_ACCESS = -1L;

    private TenantContext() {
    }

    /** Sets the restaurant id the current request is scoped to (may be {@code null}). */
    public static void setRestaurantId(Long restaurantId) {
        CURRENT_RESTAURANT_ID.set(restaurantId);
        if (restaurantId != null) {
            MDC.put(MDC_TENANT_KEY, String.valueOf(restaurantId));
        } else {
            MDC.remove(MDC_TENANT_KEY);
        }
    }

    /**
     * @return the restaurant id for the current request, or {@code null} if unscoped
     *         (unauthenticated, a SUPER_ADMIN aggregate query, or a token type not yet
     *         tenant-bound such as waiter/consumer).
     */
    public static Long getRestaurantId() {
        return CURRENT_RESTAURANT_ID.get();
    }

    /** Sets the authenticated waiter id for the current request (from the JWT, not a client header). */
    public static void setWaiterId(Long waiterId) {
        CURRENT_WAITER_ID.set(waiterId);
    }

    /** @return the authenticated waiter id for the current request, or {@code null} if not a waiter. */
    public static Long getWaiterId() {
        return CURRENT_WAITER_ID.get();
    }

    /** Removes the thread-bound values. MUST be called at the end of every request. */
    public static void clear() {
        CURRENT_RESTAURANT_ID.remove();
        CURRENT_WAITER_ID.remove();
        MDC.remove(MDC_TENANT_KEY);
    }
}
