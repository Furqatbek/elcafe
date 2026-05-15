-- Add multi-frequency payroll support to salary_configs.
--
-- Before this migration the only supported cadence was MONTHLY: the
-- entity stored `monthly_salary` + `pay_day` (1..31) and the daily
-- scheduler matched pay_day to today's day-of-month. Small food
-- environments commonly pay daily or per-shift in cash, which the
-- previous shape couldn't represent.
--
-- After this migration:
--   * pay_frequency  — enum string DAILY / WEEKLY / BIWEEKLY / MONTHLY /
--                      PER_SHIFT / HOURLY. Defaults to MONTHLY so every
--                      existing row keeps its current behaviour.
--   * base_amount    — canonical pay amount. For MONTHLY it equals
--                      monthly_salary (backfilled below); for DAILY it's
--                      per-day, for PER_SHIFT per-shift, for WEEKLY per
--                      week, for HOURLY per hour.
--   * pay_day_of_week — 1..7 (Mon..Sun) for WEEKLY/BIWEEKLY; ignored
--                       otherwise.
--
-- `monthly_salary` is kept for backwards compatibility with the existing
-- DTOs and the auto-pay code paths that still reference it; new writes
-- should populate base_amount.

ALTER TABLE salary_configs
    ADD COLUMN IF NOT EXISTS pay_frequency VARCHAR(20) NOT NULL DEFAULT 'MONTHLY';

ALTER TABLE salary_configs
    ADD COLUMN IF NOT EXISTS base_amount NUMERIC(12, 2);

ALTER TABLE salary_configs
    ADD COLUMN IF NOT EXISTS pay_day_of_week SMALLINT;

-- Backfill base_amount from the existing monthly_salary so every legacy
-- row has a usable canonical amount immediately.
UPDATE salary_configs
   SET base_amount = monthly_salary
 WHERE base_amount IS NULL;

-- pay_day was NOT NULL and that constraint no longer makes sense for
-- DAILY / PER_SHIFT / HOURLY configs (which don't have a single payday).
ALTER TABLE salary_configs
    ALTER COLUMN pay_day DROP NOT NULL;

-- monthly_salary likewise no longer needs to be required — DAILY configs
-- legitimately have no monthly figure.
ALTER TABLE salary_configs
    ALTER COLUMN monthly_salary DROP NOT NULL;

CREATE INDEX IF NOT EXISTS idx_salary_configs_frequency
    ON salary_configs (pay_frequency)
    WHERE active = true;
