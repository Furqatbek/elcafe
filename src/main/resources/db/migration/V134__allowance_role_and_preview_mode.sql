-- Two follow-ups to the consumption allowances rolled out in V133.
--
-- 1. Role-based scope. The first cut only let an allowance target an
--    individual employee, an individual waiter, or "all employees".
--    Many shops want a middle tier — "all waiters get 3 free coffees a
--    day, head waiters get 5". A nullable role string covers both User
--    and Waiter role spaces (we match by case-insensitive role name at
--    lookup time, so misspellings simply don't match).
--
-- 2. Preview-only / manual-approval mode. Some operators want to see
--    the over-limit warning at record time but settle the charge
--    themselves rather than have the system auto-post a salary
--    advance. bill_overflow defaults TRUE so every existing allowance
--    keeps its current behaviour; setting it FALSE makes the
--    consumption stamp charged_to_employee + charged_amount but skip
--    the PayrollEntry advance — the operator can post one manually
--    later if they want.

ALTER TABLE consumption_allowances
    ADD COLUMN IF NOT EXISTS role VARCHAR(50);

ALTER TABLE consumption_allowances
    ADD COLUMN IF NOT EXISTS bill_overflow BOOLEAN NOT NULL DEFAULT TRUE;

-- A rule can target an employee, a waiter, a role, or none of the
-- above (restaurant-wide). It cannot mix employee/waiter with role —
-- specificity gets ambiguous and we'd rather make the operator pick.
ALTER TABLE consumption_allowances
    DROP CONSTRAINT IF EXISTS consumption_allowances_single_subject;
ALTER TABLE consumption_allowances
    ADD CONSTRAINT consumption_allowances_single_subject CHECK (
        (CASE WHEN employee_id IS NOT NULL THEN 1 ELSE 0 END)
        + (CASE WHEN waiter_id   IS NOT NULL THEN 1 ELSE 0 END)
        + (CASE WHEN role        IS NOT NULL THEN 1 ELSE 0 END)
        <= 1
    );

CREATE INDEX IF NOT EXISTS idx_consumption_allowances_role
    ON consumption_allowances (restaurant_id, role)
    WHERE active = TRUE AND role IS NOT NULL;
