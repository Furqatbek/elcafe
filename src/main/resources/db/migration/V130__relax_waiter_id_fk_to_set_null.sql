-- Relax NO-ACTION foreign keys on waiter_id columns so that an
-- intentional hard delete of a waiter (e.g. a GDPR purge from psql) is
-- not blocked by historical rows. The application path is still a
-- soft delete (Waiter.active = false), so these FKs only kick in for
-- direct database operations.
--
-- Three tables declared waiter_id with no ON DELETE clause when they
-- were introduced, which Postgres treats as NO ACTION and blocks the
-- parent delete:
--   * employee_shifts          (V87)
--   * employee_consumptions    (V129)
--   * salary_configs           (V124 / V126)
--
-- Switching them to ON DELETE SET NULL preserves the historical row
-- and merely breaks the link, matching how orders.waiter_id and
-- financial_payroll_entries.waiter_id already behave (V17 / V79).

ALTER TABLE employee_shifts
    DROP CONSTRAINT IF EXISTS employee_shifts_waiter_id_fkey;
ALTER TABLE employee_shifts
    ADD CONSTRAINT employee_shifts_waiter_id_fkey
    FOREIGN KEY (waiter_id) REFERENCES waiters(id) ON DELETE SET NULL;

ALTER TABLE employee_consumptions
    DROP CONSTRAINT IF EXISTS employee_consumptions_waiter_id_fkey;
ALTER TABLE employee_consumptions
    ADD CONSTRAINT employee_consumptions_waiter_id_fkey
    FOREIGN KEY (waiter_id) REFERENCES waiters(id) ON DELETE SET NULL;

ALTER TABLE salary_configs
    DROP CONSTRAINT IF EXISTS salary_configs_waiter_id_fkey;
ALTER TABLE salary_configs
    ADD CONSTRAINT salary_configs_waiter_id_fkey
    FOREIGN KEY (waiter_id) REFERENCES waiters(id) ON DELETE SET NULL;
