-- Allow shift_schedules to target either a User (operator/manager) or a
-- Waiter, mirroring how salary_configs already works. Existing rows are
-- left untouched and continue to reference a User via employee_id.
ALTER TABLE shift_schedules
    ADD COLUMN waiter_id BIGINT REFERENCES waiters(id);

ALTER TABLE shift_schedules
    ALTER COLUMN employee_id DROP NOT NULL;

CREATE INDEX IF NOT EXISTS idx_shift_schedule_waiter
    ON shift_schedules (waiter_id, shift_date);

-- Exactly one subject must be set per row.
ALTER TABLE shift_schedules
    ADD CONSTRAINT chk_shift_schedule_subject
    CHECK ((employee_id IS NOT NULL)::int + (waiter_id IS NOT NULL)::int = 1);
