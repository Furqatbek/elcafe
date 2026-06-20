package com.elcafe.common.tenant;

/**
 * The three operating modes for Phase 0 tenant isolation, shared by every enforcement surface so
 * they all flip together from one config value: {@code app.security.tenant-enforcement.mode}.
 *
 * <ul>
 *   <li>{@link #OFF} — disabled (no-op).</li>
 *   <li>{@link #SHADOW} — observe and log cross-tenant access, but allow it through.</li>
 *   <li>{@link #ENFORCE} — block cross-tenant access: the edge {@link TenantEnforcementFilter}
 *       returns 403, the controller guard throws, and the Hibernate backstop
 *       ({@link TenantFilterInterceptor}) scopes every query to the caller's restaurant.</li>
 * </ul>
 */
public enum TenantEnforcementMode {
    OFF, SHADOW, ENFORCE;

    /** Lenient parse; unknown or {@code null} values default to the safe-to-observe {@link #SHADOW}. */
    public static TenantEnforcementMode from(String raw) {
        if (raw == null) {
            return SHADOW;
        }
        return switch (raw.trim().toLowerCase()) {
            case "off", "disabled", "false" -> OFF;
            case "enforce", "block", "strict" -> ENFORCE;
            default -> SHADOW;
        };
    }
}
