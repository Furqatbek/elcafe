package com.elcafe.modules.billing.enums;

/**
 * The lifecycle state of a restaurant's subscription (Phase 3 scaffolding). Persisted on
 * {@code Restaurant.subscription_status} and kept coherent by {@code BillingService.reconcile} (on
 * console actions) and the daily {@code SubscriptionLifecycleJob}.
 *
 * <p>Under the current no-payments scope, {@link #TRIAL}, {@link #ACTIVE}, {@link #EXPIRED} and
 * {@link #SUSPENDED} are <em>derived</em> from the restaurant's plan/expiry/active flags, while
 * {@link #CANCELLED} is an explicit, sticky operator action. {@link #PAST_DUE} is reserved for the
 * Phase 3 billing engine (a failed recurring charge) — nothing produces it until a payment provider
 * is wired, since prices are 0 and there is no charging.
 */
public enum SubscriptionStatus {
    /** In a time-boxed trial (e.g. the 14-day Pro trial), not yet expired. */
    TRIAL,
    /** Paid/free plan in good standing (includes the post-expiry grace window — full access). */
    ACTIVE,
    /** A recurring charge failed; access continues during dunning. Reserved for the billing engine. */
    PAST_DUE,
    /** Cut off by a platform operator (`Restaurant.active = false`). */
    SUSPENDED,
    /** Past expiry + grace with no renewal — read-only until a plan is set again. */
    EXPIRED,
    /** Subscription terminated (explicit, sticky — not auto-reverted by reconciliation). */
    CANCELLED
}
