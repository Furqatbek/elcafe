package com.elcafe.common.security.service;

import com.elcafe.modules.auth.enums.UserRole;
import com.elcafe.security.UserPrincipal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Security service for restaurant-level authorization.
 * Validates that users can only access data for restaurants they are associated with.
 */
@Slf4j
@Service
public class RestaurantAuthorizationService {

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
            throw new AccessDeniedException("User not authenticated");
        }

        // ADMIN users have access to all restaurants
        if (principal.getRole() == UserRole.ADMIN) {
            log.debug("Admin user {} accessing restaurant {}", principal.getEmail(), restaurantId);
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
     * Checks if the current user is an admin.
     *
     * @return true if the user has ADMIN role
     */
    public boolean isAdmin() {
        UserPrincipal principal = getCurrentUserPrincipal();
        return principal != null && principal.getRole() == UserRole.ADMIN;
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

        // If no restaurant specified, ADMIN can query all, others get their own
        if (principal.getRole() == UserRole.ADMIN) {
            return null; // ADMIN can see aggregate data across all restaurants
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
