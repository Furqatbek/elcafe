-- Allow payroll entries for waiters (who don't have user accounts)
ALTER TABLE financial_payroll_entries ALTER COLUMN employee_id DROP NOT NULL;
