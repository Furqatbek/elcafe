-- Tag each expense with the shift it was created during so reports can
-- distinguish cash that left the shift drawer from cash spent elsewhere
-- (bank transfers, off-shift admin expenses, etc.). Nullable: legacy rows
-- and back-office expenses (POs, recurring rent, …) stay NULL.
ALTER TABLE financial_expenses
    ADD COLUMN IF NOT EXISTS employee_shift_id BIGINT REFERENCES employee_shifts(id);

CREATE INDEX IF NOT EXISTS idx_expense_shift ON financial_expenses(employee_shift_id);
