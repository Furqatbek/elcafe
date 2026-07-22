-- Add commission type and fixed amount fields to waiters table
-- This allows for both percentage-based and fixed amount commission calculations

-- Add commission_type column (PERCENTAGE or FIXED_AMOUNT)
ALTER TABLE waiters ADD COLUMN IF NOT EXISTS commission_type VARCHAR(20) DEFAULT 'PERCENTAGE';

-- Add fixed_commission_amount column for fixed amount commissions
ALTER TABLE waiters ADD COLUMN IF NOT EXISTS fixed_commission_amount DECIMAL(10, 2) DEFAULT 0.00;

-- Update existing records to have default PERCENTAGE type
UPDATE waiters SET commission_type = 'PERCENTAGE' WHERE commission_type IS NULL;
