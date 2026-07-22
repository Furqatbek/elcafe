-- Add split_number field to payments table for tracking which split a payment belongs to
ALTER TABLE payments ADD COLUMN IF NOT EXISTS split_number INTEGER;

-- Add index for efficient queries by order_id and split_number
CREATE INDEX IF NOT EXISTS idx_payments_order_split ON payments(order_id, split_number);
