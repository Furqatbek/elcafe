-- V48: Payment Processing Enhancements
-- Adds support for multiple payments, tips, and refunds

-- Add new columns to payments table for multi-payment and refund support
ALTER TABLE payments ADD COLUMN IF NOT EXISTS tip_amount DECIMAL(10,2) DEFAULT 0.00;
ALTER TABLE payments ADD COLUMN IF NOT EXISTS refunded_amount DECIMAL(10,2) DEFAULT 0.00;
ALTER TABLE payments ADD COLUMN IF NOT EXISTS amount_tendered DECIMAL(10,2);
ALTER TABLE payments ADD COLUMN IF NOT EXISTS change_due DECIMAL(10,2);
ALTER TABLE payments ADD COLUMN IF NOT EXISTS refund_reason VARCHAR(500);
ALTER TABLE payments ADD COLUMN IF NOT EXISTS processed_by VARCHAR(100);

-- Drop the unique constraint on order_id to allow multiple payments per order
-- First, check if constraint exists before dropping
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'payments_order_id_key'
        OR conname = 'uk_payments_order_id'
        OR conname = 'payments_order_id_unique'
    ) THEN
        ALTER TABLE payments DROP CONSTRAINT IF EXISTS payments_order_id_key;
        ALTER TABLE payments DROP CONSTRAINT IF EXISTS uk_payments_order_id;
        ALTER TABLE payments DROP CONSTRAINT IF EXISTS payments_order_id_unique;
    END IF;
END $$;

-- Add index for order_id since it's no longer unique but still commonly queried
CREATE INDEX IF NOT EXISTS idx_payments_order_id ON payments(order_id);

-- Add tip and grand total to orders table
ALTER TABLE orders ADD COLUMN IF NOT EXISTS tip_amount DECIMAL(10,2) DEFAULT 0.00;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS grand_total DECIMAL(10,2);

-- Update grand_total for existing orders (set to total if not set)
UPDATE orders SET grand_total = total WHERE grand_total IS NULL;

-- Add void_reason column for voided orders
ALTER TABLE orders ADD COLUMN IF NOT EXISTS void_reason VARCHAR(500);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS voided_at TIMESTAMP;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS voided_by VARCHAR(100);

-- Add VOIDED and PARTIALLY_REFUNDED to payment_status if using enum type
-- Note: This may need adjustment depending on how the enum is stored
-- For PostgreSQL with VARCHAR storage, no changes needed as enum values are stored as strings

-- Create index for payment status queries
CREATE INDEX IF NOT EXISTS idx_payments_status ON payments(status);
CREATE INDEX IF NOT EXISTS idx_payments_method ON payments(method);

-- Add comment
COMMENT ON COLUMN payments.tip_amount IS 'Tip amount included in this payment';
COMMENT ON COLUMN payments.refunded_amount IS 'Amount refunded from this payment';
COMMENT ON COLUMN payments.amount_tendered IS 'For cash payments: amount customer gave';
COMMENT ON COLUMN payments.change_due IS 'For cash payments: change returned to customer';
COMMENT ON COLUMN orders.tip_amount IS 'Total tip amount for the order';
COMMENT ON COLUMN orders.grand_total IS 'Total + tip amount';
