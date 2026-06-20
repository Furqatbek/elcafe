-- V154: flag low-confidence tenant assignments for admin review (Phase 0 §3.7 safeguard).
--
-- The waiter (V148) and customer (V150) backfills derived restaurant_id from order activity, falling
-- back to the oldest restaurant when there was no evidence. That fallback is a guess: it can attach a
-- customer/waiter to a restaurant they have no real relationship with. This adds a confidence flag so
-- a platform admin (SUPER_ADMIN) can review and correct those rows (see TenantReviewController).
--
-- "LOW" = the assigned restaurant is NOT corroborated by any order (for waiters, nor any
-- waiter_performance) record — i.e. it came from the fallback. Everything else stays "HIGH".
--
-- Runs on Postgres; the H2 test suite has flyway.enabled=false, so this is not exercised by tests.

ALTER TABLE customers ADD COLUMN IF NOT EXISTS tenant_assignment_confidence VARCHAR(10) NOT NULL DEFAULT 'HIGH';
ALTER TABLE waiters   ADD COLUMN IF NOT EXISTS tenant_assignment_confidence VARCHAR(10) NOT NULL DEFAULT 'HIGH';

-- Customers: LOW when no order ties the customer to its assigned restaurant (V150 fallback).
UPDATE customers c SET tenant_assignment_confidence = 'LOW'
WHERE NOT EXISTS (
    SELECT 1 FROM orders o WHERE o.customer_id = c.id AND o.restaurant_id = c.restaurant_id
);

-- Waiters: LOW when neither orders nor waiter_performance tie the waiter to its assigned restaurant
-- (V148 tier-3 oldest-restaurant fallback).
UPDATE waiters w SET tenant_assignment_confidence = 'LOW'
WHERE NOT EXISTS (
        SELECT 1 FROM orders o WHERE o.waiter_id = w.id AND o.restaurant_id = w.restaurant_id
    )
  AND NOT EXISTS (
        SELECT 1 FROM waiter_performance wp WHERE wp.waiter_id = w.id AND wp.restaurant_id = w.restaurant_id
    );
