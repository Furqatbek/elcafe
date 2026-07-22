-- Add purchase_order_id column to financial_expenses table
-- to link expenses with purchase orders

ALTER TABLE financial_expenses
ADD COLUMN IF NOT EXISTS purchase_order_id BIGINT;

-- Add index for better query performance
CREATE INDEX IF NOT EXISTS idx_expense_purchase_order
ON financial_expenses(purchase_order_id);

-- Add foreign key reference (optional - allows for data integrity)
-- Note: We don't add FK constraint to allow for flexibility in case PO is deleted
