package com.elcafe.modules.auth.enums;

/**
 * System-wide user roles for authentication and authorization.
 * These roles are used in @PreAuthorize annotations across controllers.
 */
public enum UserRole {
    // Primary roles
    ADMIN,           // System administrator with full access
    OWNER,           // Restaurant owner with management access
    MANAGER,         // Restaurant manager with operational access
    OPERATOR,        // Back-office operator

    // Staff roles
    WAITER,          // Front-of-house staff
    SUPERVISOR,      // Waiter supervisor
    HEAD_WAITER,     // Head waiter with additional permissions
    KITCHEN_STAFF,   // Kitchen staff
    COURIER,         // Delivery courier

    // Customer roles
    CUSTOMER,        // Registered customer

    // Legacy/compatibility roles
    RESTAURANT,      // Restaurant-level access (for notifications)
    KITCHEN          // Kitchen access (for notifications)
}
