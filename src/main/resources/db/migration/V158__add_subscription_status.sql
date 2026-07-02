-- Phase 3 scaffolding: formalize the subscription lifecycle state on each restaurant.
-- Derived states (TRIAL/ACTIVE/EXPIRED/SUSPENDED) are reconciled by BillingService and the daily
-- SubscriptionLifecycleJob; CANCELLED is an explicit, sticky operator action; PAST_DUE is reserved for
-- the future billing engine (no charging yet — prices are 0, no payment provider).
ALTER TABLE restaurants ADD COLUMN subscription_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';

-- Backfill from current state. The 3-day window matches PlanGateService.GRACE_DAYS (read-only cutoff).

-- Expired: past expiry + grace, with no renewal.
UPDATE restaurants
   SET subscription_status = 'EXPIRED'
 WHERE plan_expires_at IS NOT NULL
   AND plan_expires_at < (now() - INTERVAL '3 days');

-- Trial: a flagged trial that hasn't lapsed past the grace window.
UPDATE restaurants
   SET subscription_status = 'TRIAL'
 WHERE is_trial = true
   AND (plan_expires_at IS NULL OR plan_expires_at >= (now() - INTERVAL '3 days'));

-- Suspended takes precedence: an inactive restaurant is cut off regardless of plan/expiry.
UPDATE restaurants
   SET subscription_status = 'SUSPENDED'
 WHERE active = false;
