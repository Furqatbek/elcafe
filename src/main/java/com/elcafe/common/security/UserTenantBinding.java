package com.elcafe.common.security;

import com.elcafe.exception.BadRequestException;
import com.elcafe.modules.auth.enums.UserRole;

/**
 * The rule that says which accounts must belong to a restaurant, in one place.
 *
 * <p><b>Why this exists.</b> Every role except {@link UserRole#SUPER_ADMIN} is tenant-scoped, so an
 * account carrying one of them and {@code restaurant_id IS NULL} is not "unassigned" — it is
 * <em>broken</em>. {@code TenantEnforcementFilter} binds {@link com.elcafe.common.tenant.TenantContext}
 * to the deny-all sentinel for exactly that combination, which is correct as a security measure (the
 * account cannot read another tenant's data) and catastrophic as an experience: the person logs in
 * successfully, every screen is empty, and it looks precisely like the database was wiped.
 *
 * <p>Five separate write paths used to be able to produce that state — the system-user console, the
 * operator and courier creators, a role change that left the binding behind, and {@code /auth/register},
 * whose every output was an account that could never work. Each had its own local reasoning about when
 * a null was acceptable. Centralising the rule is the point: a sixth path added later inherits the
 * decision instead of re-deriving it.
 *
 * <p>SUPER_ADMIN is the sole exception, and its null is meaningful rather than missing — the platform
 * operator is deliberately outside every tenant, which is what lets it provision them.
 */
public final class UserTenantBinding {

    private UserTenantBinding() {
    }

    /** True for every role that only makes sense inside one restaurant — i.e. all but SUPER_ADMIN. */
    public static boolean requiresRestaurant(UserRole role) {
        return role != null && role != UserRole.SUPER_ADMIN;
    }

    /**
     * Reject an account that would be created or left in the deny-all state.
     *
     * @param role         the role the account will have after this write
     * @param restaurantId the binding it will have after this write
     * @param context      what the caller was doing, so the message names the actual action
     * @throws BadRequestException when a tenant-scoped role has no restaurant
     */
    public static void require(UserRole role, Long restaurantId, String context) {
        if (requiresRestaurant(role) && restaurantId == null) {
            throw new BadRequestException(String.format(
                    "%s: a %s account must belong to a restaurant. Choose one — an unassigned %s can "
                            + "sign in but sees no data at all, which looks like data loss rather than a "
                            + "permissions problem. Only SUPER_ADMIN may exist outside a restaurant.",
                    context, role, role));
        }
    }

    /** True when this account is in the broken state — used by the boot-time audit. */
    public static boolean isUnbound(UserRole role, Long restaurantId) {
        return requiresRestaurant(role) && restaurantId == null;
    }
}
