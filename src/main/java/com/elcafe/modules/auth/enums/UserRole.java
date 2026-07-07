package com.elcafe.modules.auth.enums;

import java.util.List;

/**
 * System-wide user roles for authentication and authorization.
 * These roles are used in @PreAuthorize annotations across controllers.
 */
public enum UserRole {
    // Platform-level role
    SUPER_ADMIN,     // Platform super administrator — superset of ADMIN

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
    CASHIER,         // POS cashier with cash drawer and payment access

    // Customer roles
    CUSTOMER,        // Registered customer

    // Legacy/compatibility roles
    RESTAURANT,      // Restaurant-level access (for notifications)
    KITCHEN,         // Kitchen access (for notifications)

    // Sentinel for a role string in the database that this enum does not
    // recognise. UserRoleConverter maps unknown values here instead of
    // throwing during entity hydration (which would surface as a 500 on
    // login). A user resolved to UNKNOWN is treated as disabled and holds
    // no authorities — never authenticate an account we can't classify.
    UNKNOWN;

    /**
     * True for roles with full administrative privileges. SUPER_ADMIN is a
     * strict superset of ADMIN, so anywhere ADMIN is granted blanket access
     * (e.g. cross-restaurant data), SUPER_ADMIN must be granted it too.
     */
    public boolean isAdminLevel() {
        return this == ADMIN || this == SUPER_ADMIN;
    }

    /**
     * Whether an account holding this role may be authenticated. UNKNOWN
     * (an unrecognised database value) must not be able to log in.
     */
    public boolean isUsable() {
        return this != UNKNOWN;
    }

    /**
     * Role names to grant as Spring Security authorities (each becomes a
     * {@code ROLE_<name>} authority). Defined here so the SUPER_ADMIN →
     * ADMIN superset lives in exactly one place and every principal
     * implementation stays in sync.
     */
    public List<String> grantedRoleNames() {
        if (this == SUPER_ADMIN) {
            // Superset of ADMIN: carry ROLE_ADMIN so every existing
            // hasRole('ADMIN') / hasAnyRole('ADMIN', …) check passes.
            return List.of(SUPER_ADMIN.name(), ADMIN.name());
        }
        if (this == UNKNOWN) {
            return List.of();
        }
        return List.of(name());
    }
}
