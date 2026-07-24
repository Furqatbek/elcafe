-- Composite index to serve the paginated, date-windowed expense query
--   WHERE restaurant_id = ? AND expense_date BETWEEN ? AND ? ORDER BY expense_date DESC
-- The pre-existing single-column indexes (idx_expense_restaurant,
-- idx_expense_date) can't satisfy the combined predicate + ordering as
-- efficiently as a leading-restaurant composite. Kept additive; the older
-- single-column indexes are left in place to avoid disrupting other queries.

CREATE INDEX IF NOT EXISTS idx_expense_restaurant_date
    ON financial_expenses (restaurant_id, expense_date);
