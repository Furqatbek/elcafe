package com.elcafe.common.tenant;

/**
 * Confidence in a row's heuristic tenant (restaurant) assignment produced by the Phase 0 backfills
 * (waiters V148, customers V150).
 *
 * <p>{@code HIGH} — the assigned restaurant is corroborated by real activity (an order, or for
 * waiters a performance record). {@code LOW} — no such evidence exists, so the assignment came from
 * the no-evidence "oldest restaurant" fallback and is essentially a guess. LOW rows are surfaced to a
 * platform admin (SUPER_ADMIN) for review/reassignment via the tenant-review endpoints; confirming or
 * reassigning a row marks it HIGH.
 */
public enum AssignmentConfidence {
    HIGH,
    LOW
}
