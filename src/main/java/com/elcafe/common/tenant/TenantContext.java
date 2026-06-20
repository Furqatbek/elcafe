package com.elcafe.common.tenant;

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
 */
public final class TenantContext {

    private static final ThreadLocal<Long> CURRENT_RESTAURANT_ID = new ThreadLocal<>();

    private TenantContext() {
    }

    /** Sets the restaurant id the current request is scoped to (may be {@code null}). */
    public static void setRestaurantId(Long restaurantId) {
        CURRENT_RESTAURANT_ID.set(restaurantId);
    }

    /**
     * @return the restaurant id for the current request, or {@code null} if unscoped
     *         (unauthenticated, a SUPER_ADMIN aggregate query, or a token type not yet
     *         tenant-bound such as waiter/consumer).
     */
    public static Long getRestaurantId() {
        return CURRENT_RESTAURANT_ID.get();
    }

    /** Removes the thread-bound value. MUST be called at the end of every request. */
    public static void clear() {
        CURRENT_RESTAURANT_ID.remove();
    }
}
