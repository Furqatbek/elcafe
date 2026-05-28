-- Late-arrival punishment for HOURLY salary configs.
-- late_grace_minutes: minutes of grace past scheduled_start before a
--                     shift counts as "late" (default 5).
-- late_penalty_amount: fixed cash deduction added to that period's
--                      other_deductions per late shift (default 0 = off).
ALTER TABLE salary_configs
    ADD COLUMN late_grace_minutes  INTEGER       NOT NULL DEFAULT 5,
    ADD COLUMN late_penalty_amount NUMERIC(12,2) NOT NULL DEFAULT 0;
