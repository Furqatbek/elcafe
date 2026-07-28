package com.elcafe.modules.auth.enums;

/**
 * System-wide user roles for authentication and authorization.
 * These roles are used in @PreAuthorize annotations across controllers.
 */
public enum UserRole {
    // Platform-operator role (cross-tenant). The ONLY role allowed to access data across
    // all restaurants. SUPER_ADMIN inherits every ADMIN authority via the RoleHierarchy
    // configured in SecurityConfig, so existing @PreAuthorize("hasRole('ADMIN')") checks
    // continue to authorize a SUPER_ADMIN without modification.
    SUPER_ADMIN,

    // Primary roles
    ADMIN,           // Restaurant administrator (tenant-scoped — see RestaurantAuthorizationService)
    OWNER,           // Restaurant owner. Inherits every MANAGER authority via the RoleHierarchy in
                     // SecurityConfig (ROLE_OWNER > ROLE_MANAGER), so an OWNER satisfies any
                     // hasAnyRole('...MANAGER...') gate without OWNER being listed explicitly.
    MANAGER,         // Restaurant manager with operational access
    OPERATOR,        // Back-office operator

    // Staff roles
    WAITER,          // Front-of-house staff
    SUPERVISOR,      // Waiter supervisor
    HEAD_WAITER,     // Head waiter with additional permissions
    KITCHEN_STAFF,   // Kitchen staff
    COURIER,         // Delivery courier
    CASHIER,         // POS cashier with cash drawer and payment access

    // Customer roles
    CUSTOMER,        // Registered customer

    // Legacy/compatibility roles
    RESTAURANT,      // Restaurant-level access (for notifications)
    KITCHEN          // Kitchen access (for notifications)
}
