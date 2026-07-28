package com.elcafe.common.security.service;

import com.elcafe.common.tenant.TenantContext;
import com.elcafe.common.tenant.TenantEnforcementMode;
import com.elcafe.modules.auth.enums.UserRole;
import com.elcafe.security.UserPrincipal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Security service for restaurant-level (tenant) authorization.
 * Validates that users can only access data for the restaurant they are associated with.
 *
 * <p>Cross-restaurant access is granted to {@link UserRole#SUPER_ADMIN} only (the platform
 * operator). Every other role — including the tenant-scoped {@link UserRole#ADMIN} — is
 * confined to its own {@code restaurantId}.
 */
@Slf4j
@Service
public class RestaurantAuthorizationService {

    @Value("${app.security.tenant-enforcement.mode:shadow}")
    private String enforcementMode;

    /**
     * Mode-aware tenant check for controllers being retrofitted (Phase 0 §3.3).
     *
     * <p>Governed by {@code app.security.tenant-enforcement.mode}, so the entire new
     * enforcement surface flips from observe to block with a single config change:
     * <ul>
     *   <li>{@code off} — no-op.</li>
     *   <li>{@code shadow} (default) — log a {@code [tenant-shadow]} warning on a cross-tenant
     *       attempt but allow it through.</li>
     *   <li>{@code enforce} — throw {@link AccessDeniedException}, exactly like
     *       {@link #validateRestaurantAccess(Long)}.</li>
     * </ul>
     *
     * <p>Unlike {@link #validateRestaurantAccess(Long)} (which always throws and is used by the
     * handful of already-hard-enforced endpoints), this method is for NEW guard calls that
     * should be observed before they start blocking.
     *
     * <p>This guards a <em>specific</em> restaurant's resource, so a null {@code restaurantId} fails
     * closed (it means the caller could not determine the owner) — under {@code enforce} it throws,
     * under {@code shadow} it is observed but allowed, via {@link #validateRestaurantAccess(Long)}. Do
     * not use it for endpoints where an omitted restaurant is a legitimate "no filter" request — those
     * must use {@link #checkAccessIfPresent(Long)}.
     */
    public void checkAccess(Long restaurantId) {
        switch (TenantEnforcementMode.from(enforcementMode)) {
            case OFF -> { /* enforcement disabled */ }
            case ENFORCE -> validateRestaurantAccess(restaurantId);
            case SHADOW -> {
                try {
                    validateRestaurantAccess(restaurantId);
                } catch (AccessDeniedException e) {
                    log.warn("[tenant-shadow] controller-guard violation for restaurantId={}: {}",
                            restaurantId, e.getMessage());
                }
            }
        }
    }

    /**
     * Access guard for an <em>optional</em> restaurant filter: endpoints where omitting the restaurant
     * is a legitimate request meaning "no explicit filter" — a {@link UserRole#SUPER_ADMIN} listing
     * across all restaurants, or a tenant caller who will be narrowed to their own rows by the tenant
     * {@code @Filter} / {@link #currentTenantReadScopeStrict()} downstream. When a specific
     * {@code restaurantId} IS supplied it is guarded exactly like {@link #checkAccess(Long)}; when it
     * is {@code null} the call is allowed (scoping is deferred to that downstream layer).
     *
     * <p>This is the deliberate, named counterpart to {@link #checkAccess(Long)}, which fails closed on
     * null. Use it ONLY for optional list/aggregate filters — never as the guard for a write or a
     * fetch-by-id, where a null restaurant is a caller bug that must be rejected.
     */
    public void checkAccessIfPresent(Long restaurantId) {
        if (restaurantId == null) {
            return; // optional filter omitted — scoping handled by the tenant @Filter / role resolution
        }
        checkAccess(restaurantId);
    }

    /**
     * The restaurant a tenant-scoped <em>listing or write</em> should be constrained to, or
     * {@code null} to apply no constraint (the caller may see/affect all rows). Honors the
     * enforcement mode so it flips together with the rest of Phase 0:
     * <ul>
     *   <li>{@code off}/{@code shadow} → {@code null} (preserves current, pre-enforcement behavior);</li>
     *   <li>{@code enforce} + cross-tenant {@link UserRole#SUPER_ADMIN} → {@code null};</li>
     *   <li>{@code enforce} + tenant-scoped caller → the caller's own {@code restaurantId}.</li>
     * </ul>
     *
     * <p>For endpoints over entities the Hibernate backstop cannot scope — notably {@code User},
     * which is deliberately not {@code @Filter}ed (see {@code User.java}) — this is how
     * cross-tenant enumeration is constrained at the query layer instead.
     */
    public Long currentTenantScopeOrNull() {
        if (TenantEnforcementMode.from(enforcementMode) != TenantEnforcementMode.ENFORCE) {
            return null;
        }
        UserPrincipal principal = getCurrentUserPrincipal();
        if (principal == null || principal.getRole() == UserRole.SUPER_ADMIN) {
            return null;
        }
        return principal.getRestaurantId();
    }

    /**
     * The restaurant a tenant-scoped <em>read listing over a {@code @Filter}-excluded entity</em>
     * (notably {@code User}) must be constrained to. Identical to {@link #currentTenantScopeOrNull()}
     * except that a tenant-scoped caller with <em>no assigned restaurant</em> resolves to the deny-all
     * sentinel {@link TenantContext#NO_ACCESS} (which matches no row) instead of {@code null} —
     * otherwise that principal would bind a null tenant and the {@code null → list-all} branch in
     * these listings would leak every tenant's rows under {@code enforce}. SUPER_ADMIN and
     * {@code off}/{@code shadow} stay {@code null} (unscoped), preserving pre-enforcement behaviour.
     */
    public Long currentTenantReadScope() {
        if (TenantEnforcementMode.from(enforcementMode) != TenantEnforcementMode.ENFORCE) {
            return null;
        }
        UserPrincipal principal = getCurrentUserPrincipal();
        if (principal == null || principal.getRole() == UserRole.SUPER_ADMIN) {
            return null;
        }
        Long tenant = principal.getRestaurantId();
        return tenant != null ? tenant : TenantContext.NO_ACCESS;
    }

    /**
     * Like {@link #currentTenantReadScope()} but scopes <em>regardless of enforcement mode</em>. For
     * high-sensitivity listings where cross-tenant visibility is never legitimate and must not wait for
     * the shadow→enforce flip (e.g. admin user management): a non-SUPER_ADMIN caller always resolves to
     * its own {@code restaurantId} (or the deny-all sentinel if it has none); SUPER_ADMIN stays
     * {@code null} (unscoped, cross-tenant).
     */
    public Long currentTenantReadScopeStrict() {
        UserPrincipal principal = getCurrentUserPrincipal();
        if (principal == null || principal.getRole() == UserRole.SUPER_ADMIN) {
            return null;
        }
        Long tenant = principal.getRestaurantId();
        return tenant != null ? tenant : TenantContext.NO_ACCESS;
    }

    /**
     * The restaurant to bind a newly-created tenant-owned row to, <em>regardless of enforcement mode</em>:
     * the caller's own {@code restaurantId}, or {@code null} for a SUPER_ADMIN (platform account) / an
     * unassigned caller. The write-time twin of {@link #currentTenantReadScopeStrict()} — they must agree
     * so a row created under a tenant admin is also visible to the same admin's (strictly-scoped) listing.
     * Unlike {@link #currentTenantScopeOrNull()} this does not short-circuit to {@code null} in shadow.
     */
    public Long currentTenantScopeStrict() {
        UserPrincipal principal = getCurrentUserPrincipal();
        if (principal == null || principal.getRole() == UserRole.SUPER_ADMIN) {
            return null;
        }
        return principal.getRestaurantId();
    }

    /**
     * Validates that the current user has access to the specified restaurant.
     *
     * <p><b>A null {@code restaurantId} fails closed</b> for every tenant-scoped caller — only the
     * cross-tenant {@link UserRole#SUPER_ADMIN} may act without a specific restaurant (which is also
     * what lets the operator reach platform / null-restaurant accounts). This used to be a blanket
     * {@code return} ("allow, aggregate query"), which silently turned every guard built on this method
     * — and on the mode-aware {@link #checkAccess(Long)} wrapper — into a no-op the moment the id
     * resolved to null (e.g. {@code checkAccess(request.getRestaurantId())} with the field omitted:
     * exactly the loyalty-config hole). Endpoints that legitimately accept an <em>optional</em>
     * restaurant filter must call {@link #checkAccessIfPresent(Long)} instead, which allows null and
     * defers scoping to the tenant {@code @Filter} / role resolution.
     *
     * @param restaurantId the restaurant ID to validate access for
     * @throws AccessDeniedException if the user doesn't have access to the restaurant, or if it is null
     *                               and the caller is not the platform operator
     */
    public void validateRestaurantAccess(Long restaurantId) {
        if (restaurantId == null) {
            UserPrincipal p = getCurrentUserPrincipal();
            if (p != null && p.getRole() == UserRole.SUPER_ADMIN) {
                return; // platform operator: unconstrained (aggregate loads, platform accounts)
            }
            throw new AccessDeniedException("Access denied: no restaurant specified");
        }

        UserPrincipal principal = getCurrentUserPrincipal();
        if (principal == null) {
            // Waiter (ROLE_WAITER) and consumer (ROLE_CUSTOMER) tokens are plain UserDetails, not
            // UserPrincipal. Their tenant is bound in TenantContext by the JWT filter (§3.6 waiter /
            // V150 consumer), so enforce it just like a UserPrincipal. Previously this branch allowed
            // ANY restaurantId, so the tenant guard was a silent no-op for these principals — a
            // cross-tenant read/write hole that even the enforce flip did not close.
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()
                    && auth.getPrincipal() instanceof org.springframework.security.core.userdetails.UserDetails) {
                Long boundTenant = TenantContext.getRestaurantId();
                if (boundTenant != null && boundTenant.equals(restaurantId)) {
                    return;
                }
                log.warn("Non-staff token (tenant {}) attempted access to restaurant {}", boundTenant, restaurantId);
                throw new AccessDeniedException("Access denied: token is not bound to this restaurant");
            }
            throw new AccessDeniedException("User not authenticated");
        }

        // SUPER_ADMIN (platform operator) is the only role with cross-restaurant access.
        if (principal.getRole() == UserRole.SUPER_ADMIN) {
            log.debug("Platform operator {} accessing restaurant {}", principal.getEmail(), restaurantId);
            return;
        }

        // For all other roles, verify restaurant ownership
        Long userRestaurantId = principal.getRestaurantId();
        if (userRestaurantId == null) {
            log.warn("User {} has no assigned restaurant but attempted to access restaurant {}",
                    principal.getEmail(), restaurantId);
            throw new AccessDeniedException("User is not assigned to any restaurant");
        }

        if (!userRestaurantId.equals(restaurantId)) {
            log.warn("User {} (restaurant {}) attempted unauthorized access to restaurant {}",
                    principal.getEmail(), userRestaurantId, restaurantId);
            throw new AccessDeniedException("Access denied: You don't have permission to access this restaurant's data");
        }

        log.debug("User {} authorized for restaurant {}", principal.getEmail(), restaurantId);
    }

    /**
     * Gets the restaurant ID for the current user.
     * Returns null for ADMIN users if no specific restaurant is assigned.
     *
     * @return the user's restaurant ID or null
     */
    public Long getCurrentUserRestaurantId() {
        UserPrincipal principal = getCurrentUserPrincipal();
        if (principal == null) {
            return null;
        }
        return principal.getRestaurantId();
    }

    /**
     * Checks if the current user is the cross-tenant platform operator (SUPER_ADMIN).
     *
     * @return true if the user has the SUPER_ADMIN role
     */
    public boolean isAdmin() {
        UserPrincipal principal = getCurrentUserPrincipal();
        return principal != null && principal.getRole() == UserRole.SUPER_ADMIN;
    }

    /**
     * Resolves the effective restaurant ID to use for queries.
     * - If restaurantId is provided and user has access, returns it
     * - If restaurantId is null and user is ADMIN, returns null (all restaurants)
     * - If restaurantId is null and user is not ADMIN, returns user's restaurant
     *
     * @param requestedRestaurantId the restaurant ID from the request (may be null)
     * @return the effective restaurant ID to use, or null for all restaurants
     * @throws AccessDeniedException if user doesn't have access to the requested restaurant
     */
    public Long resolveRestaurantId(Long requestedRestaurantId) {
        UserPrincipal principal = getCurrentUserPrincipal();
        if (principal == null) {
            throw new AccessDeniedException("User not authenticated");
        }

        // If a specific restaurant is requested, validate access
        if (requestedRestaurantId != null) {
            validateRestaurantAccess(requestedRestaurantId);
            return requestedRestaurantId;
        }

        // If no restaurant specified, SUPER_ADMIN can query all, others get their own
        if (principal.getRole() == UserRole.SUPER_ADMIN) {
            return null; // SUPER_ADMIN can see aggregate data across all restaurants
        }

        // Non-admin users must have an assigned restaurant
        Long userRestaurantId = principal.getRestaurantId();
        if (userRestaurantId == null) {
            throw new AccessDeniedException("User is not assigned to any restaurant");
        }

        return userRestaurantId;
    }

    private UserPrincipal getCurrentUserPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof UserPrincipal) {
            return (UserPrincipal) principal;
        }

        return null;
    }
}
