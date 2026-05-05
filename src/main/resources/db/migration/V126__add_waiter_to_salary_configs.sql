-- Add waiter_id to salary_configs for waiter-only salary configurations
ALTER TABLE salary_configs ADD COLUMN IF NOT EXISTS waiter_id BIGINT REFERENCES waiters(id);
ALTER TABLE salary_configs ALTER COLUMN employee_id DROP NOT NULL;
