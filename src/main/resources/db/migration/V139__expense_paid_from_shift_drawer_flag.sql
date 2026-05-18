-- An expense is "paid from the shift cash drawer" only when the
-- operator explicitly says so. Inferring it from
-- "operator was on shift + paymentMethod = CASH" was wrong: a cashier
-- can pay a vendor in cash from owner-provided budget, petty cash, or
-- their own pocket while on shift, and the till should not be debited
-- for those.
--
-- The Telegram daily report's "🪙 Из кассы смены" line will now sum
-- only rows with this flag set to true. Existing rows default to false
-- so historical reports recompute as "🏦 Прочие" until an operator
-- re-classifies them.
ALTER TABLE financial_expenses
    ADD COLUMN IF NOT EXISTS paid_from_shift_drawer BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_expense_paid_from_shift_drawer
    ON financial_expenses(paid_from_shift_drawer);
