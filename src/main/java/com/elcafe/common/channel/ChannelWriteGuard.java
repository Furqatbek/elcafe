package com.elcafe.common.channel;

import com.elcafe.exception.BadRequestException;

/**
 * The write-side tenant guard shared by every per-tenant channel create. A channel template, campaign,
 * automation rule, or bot config belongs to exactly one restaurant, so a platform / SUPER_ADMIN account
 * (which resolves to a {@code null} restaurant scope) cannot create one. Each of those services used to
 * reimplement the identical null-check and the identical
 * "{subject} belongs to a restaurant. Sign in with a restaurant-scoped account {action}." refusal; this
 * puts that one rule, and its wording, in a single place.
 *
 * <p>Kept as a pure static function over an already-resolved restaurant id — rather than a method on
 * {@code RestaurantAuthorizationService} — so callers still resolve the tenant through
 * {@code currentTenantScopeStrict()} exactly as before (their tests mock that call, not this guard).
 */
public final class ChannelWriteGuard {

    private ChannelWriteGuard() {
    }

    /**
     * Return {@code restaurantId} if the caller has one, else refuse the create.
     *
     * @param restaurantId the caller's resolved restaurant scope (from {@code currentTenantScopeStrict()})
     * @param subject      the thing being created, as a capitalised noun phrase, e.g. {@code "An SMS template"}
     * @param action       the trailing call to action, e.g. {@code "to create one"}
     * @throws BadRequestException if {@code restaurantId} is {@code null}
     */
    public static Long requireRestaurant(Long restaurantId, String subject, String action) {
        if (restaurantId == null) {
            throw new BadRequestException(
                    subject + " belongs to a restaurant. Sign in with a restaurant-scoped account " + action + ".");
        }
        return restaurantId;
    }
}
