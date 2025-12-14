-- Add bonus_used field to track how much bonus was used for payment
ALTER TABLE orders ADD COLUMN bonus_used DECIMAL(10, 2) DEFAULT 0;

-- Add comment for documentation
COMMENT ON COLUMN orders.bonus_used IS 'Amount of loyalty bonus used for this order payment';
