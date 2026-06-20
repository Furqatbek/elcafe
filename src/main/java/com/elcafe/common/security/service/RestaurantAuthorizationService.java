package com.elcafe.common.security.service;

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
     */
    public void checkAccess(Long restaurantId) {
        String mode = enforcementMode == null ? "shadow" : enforcementMode.trim().toLowerCase();
        switch (mode) {
            case "off", "disabled", "false" -> { /* enforcement disabled */ }
            case "enforce", "block", "strict" -> validateRestaurantAccess(restaurantId);
            default -> { // shadow
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
     * Validates that the current user has access to the specified restaurant.
     *
     * @param restaurantId the restaurant ID to validate access for
     * @throws AccessDeniedException if the user doesn't have access to the restaurant
     */
    public void validateRestaurantAccess(Long restaurantId) {
        if (restaurantId == null) {
            return; // Allow null restaurantId for aggregate queries (controlled by role-based access)
        }

        UserPrincipal principal = getCurrentUserPrincipal();
        if (principal == null) {
            // Waiter tokens use plain UserDetails, not UserPrincipal.
            // If authenticated, allow access — role security handled by @PreAuthorize.
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()
                    && auth.getPrincipal() instanceof org.springframework.security.core.userdetails.UserDetails) {
                return;
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
