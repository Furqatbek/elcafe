-- PER_SHIFT salary configs now pay on clock-out (one PayrollEntry per
-- closed shift) instead of being batched by the 08:00 cron. To stop the
-- cron from double-paying a shift that's already been settled at
-- clock-out we need a per-shift "already paid for salary" flag.
--
-- Defaults FALSE so the daily batcher continues to pick up shifts that
-- existed before this migration. Once a shift is settled (either via the
-- clock-out hook or by the batcher itself), the flag flips to TRUE and
-- no further auto-pay will fire for that shift.

ALTER TABLE employee_shifts
    ADD COLUMN IF NOT EXISTS paid_for_salary BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_employee_shifts_paid_for_salary
    ON employee_shifts (restaurant_id, shift_date)
    WHERE paid_for_salary = FALSE;
