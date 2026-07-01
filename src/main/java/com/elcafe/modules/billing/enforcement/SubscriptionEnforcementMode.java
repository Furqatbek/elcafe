package com.elcafe.modules.billing.enforcement;

/**
 * Operating modes for the Phase 2 subscription access gate, flipped from one config value:
 * {@code app.subscription.enforcement.mode}. Mirrors {@code TenantEnforcementMode} so the gate can be
 * dark-launched and enabled the same way.
 *
 * <ul>
 *   <li>{@link #OFF} — disabled (no-op). The default: the gate ships dark.</li>
 *   <li>{@link #SHADOW} — log the requests it <em>would</em> block ({@code [subscription-shadow]}) but
 *       let them through. Use to gauge impact on real traffic before enforcing.</li>
 *   <li>{@link #ENFORCE} — return {@code 402 SUBSCRIPTION_INACTIVE} for a suspended tenant's staff.</li>
 * </ul>
 */
public enum SubscriptionEnforcementMode {
    OFF, SHADOW, ENFORCE;

    /** Lenient parse; unknown or {@code null} values default to the safe {@link #OFF} (ship dark). */
    public static SubscriptionEnforcementMode from(String raw) {
        if (raw == null) {
            return OFF;
        }
        return switch (raw.trim().toLowerCase()) {
            case "shadow", "observe", "log" -> SHADOW;
            case "enforce", "block", "strict", "on" -> ENFORCE;
            default -> OFF;
        };
    }
}
