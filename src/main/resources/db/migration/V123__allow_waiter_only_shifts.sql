-- Allow shifts without a linked user account (waiter-only shifts)
ALTER TABLE employee_shifts ALTER COLUMN employee_id DROP NOT NULL;
