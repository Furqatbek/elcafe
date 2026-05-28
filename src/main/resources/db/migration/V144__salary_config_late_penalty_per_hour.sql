-- Adds an hourly late-arrival fine on top of the existing flat
-- per-shift fine. The flat amount (late_penalty_amount) still fires
-- once when a shift is late beyond grace; this new column adds an
-- extra charge per started hour past the grace window:
--   penalty = late_penalty_amount + late_penalty_per_hour * ceil((minutesLate - grace) / 60)
-- Set either or both. Zero disables this component.
ALTER TABLE salary_configs
    ADD COLUMN late_penalty_per_hour NUMERIC(12,2) NOT NULL DEFAULT 0;
