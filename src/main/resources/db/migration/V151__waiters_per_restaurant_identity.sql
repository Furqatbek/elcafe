-- V151: Per-restaurant waiter identity (Phase 0 §3.6 hardening).
--
-- Waiters became tenant-bound in V148 (restaurant_id added + backfilled, left NULLABLE). This
-- finishes the job: pin_code and email become unique PER RESTAURANT instead of globally, and
-- restaurant_id is made NOT NULL now that V148's backfill (most-orders -> performance -> oldest
-- restaurant fallback) has populated every row. Waiter login is correspondingly scoped to a
-- restaurant in code (findByRestaurantIdAndPinCode), so two restaurants can reuse a PIN.
--
-- Safety: pin_code and email were GLOBALLY unique, so no duplicate can already exist within a single
-- restaurant — the composite uniques below cannot fail on existing data. The migration runs in one
-- transaction; if SET NOT NULL fails, an un-backfilled waiter row exists and the whole migration
-- rolls back (surfacing it) rather than enforcing a half-populated column.

-- 1) Drop the global uniqueness (V15 inline UNIQUE -> Postgres default constraint names).
ALTER TABLE waiters DROP CONSTRAINT IF EXISTS waiters_pin_code_key;
ALTER TABLE waiters DROP CONSTRAINT IF EXISTS waiters_email_key;

-- 2) Enforce the tenant column (every row backfilled by V148).
ALTER TABLE waiters ALTER COLUMN restaurant_id SET NOT NULL;

-- 3) Per-restaurant uniqueness. NULL email is allowed many times per restaurant (unique ignores
--    nulls), matching the old global behaviour for waiters without an email.
ALTER TABLE waiters ADD CONSTRAINT uq_waiters_restaurant_pin UNIQUE (restaurant_id, pin_code);
ALTER TABLE waiters ADD CONSTRAINT uq_waiters_restaurant_email UNIQUE (restaurant_id, email);
