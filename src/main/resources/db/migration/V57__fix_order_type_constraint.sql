-- Fix order_type constraint to use TAKEAWAY instead of PICKUP
-- The OrderType enum uses TAKEAWAY, not PICKUP

ALTER TABLE orders DROP CONSTRAINT IF EXISTS chk_order_type;
ALTER TABLE orders ADD CONSTRAINT chk_order_type CHECK (
    order_type IN ('DELIVERY', 'TAKEAWAY', 'DINE_IN')
);

-- Update any existing PICKUP orders to TAKEAWAY (if any)
UPDATE orders SET order_type = 'TAKEAWAY' WHERE order_type = 'PICKUP';
