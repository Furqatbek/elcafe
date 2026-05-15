-- Employee consumption limits.
--
-- A restaurant can grant employees and waiters a configurable allowance
-- of free items per period (DAILY / PER_SHIFT, with WEEKLY / MONTHLY
-- supported for future use). Each allowance caps either by item count,
-- by total cost amount, or both. When a consumption exceeds the
-- allowance the overflow value is automatically posted as an ADVANCE
-- PayrollEntry against the employee — the existing salary scheduler
-- nets ADVANCE entries against the next payout via
-- calculateUnpaidAdvances, so "deducted from salary and written as
-- paid" is a direct rebill, no new bookkeeping primitive required.
--
-- Subject precedence at lookup time:
--   1. employee_id / waiter_id + category_id    (most specific)
--   2. employee_id / waiter_id + any category
--   3. any subject + category_id
--   4. any subject + any category               (restaurant-wide default)

CREATE TABLE consumption_allowances (
    id              BIGSERIAL PRIMARY KEY,
    restaurant_id   BIGINT NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    category_id     BIGINT REFERENCES categories(id) ON DELETE CASCADE,
    employee_id     BIGINT REFERENCES users(id) ON DELETE CASCADE,
    waiter_id       BIGINT REFERENCES waiters(id) ON DELETE CASCADE,
    period          VARCHAR(20) NOT NULL,
    limit_count     INTEGER,
    limit_amount    NUMERIC(12, 2),
    notes           TEXT,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- At least one cap must be set; otherwise the rule means nothing.
    CONSTRAINT consumption_allowances_has_a_cap
        CHECK (limit_count IS NOT NULL OR limit_amount IS NOT NULL),
    -- A row can target at most one subject (employee or waiter); null
    -- on both means the rule applies restaurant-wide.
    CONSTRAINT consumption_allowances_single_subject
        CHECK (employee_id IS NULL OR waiter_id IS NULL)
);

CREATE INDEX idx_consumption_allowances_restaurant_active
    ON consumption_allowances (restaurant_id)
    WHERE active = TRUE;

-- Mark each consumption row with whether it was free or charged back to
-- the employee, and how much was charged. The Expense row remains the
-- company-side bookkeeping; charged_amount mirrors what was queued as
-- an ADVANCE on the PayrollEntry side.
ALTER TABLE employee_consumptions
    ADD COLUMN IF NOT EXISTS charged_to_employee BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE employee_consumptions
    ADD COLUMN IF NOT EXISTS charged_amount NUMERIC(12, 2);
