-- Add missing timestamps to payments table
-- Add table and waiter relationships to orders table

-- Add payment timestamps for lifecycle tracking
ALTER TABLE payments ADD COLUMN IF NOT EXISTS completed_at TIMESTAMP;
ALTER TABLE payments ADD COLUMN IF NOT EXISTS refunded_at TIMESTAMP;

-- Add order source tracking
ALTER TABLE orders ADD COLUMN IF NOT EXISTS order_source VARCHAR(50);

-- Add table and waiter relationships for dine-in orders
ALTER TABLE orders ADD COLUMN IF NOT EXISTS table_id BIGINT;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS waiter_id BIGINT;

-- Add foreign key constraints
ALTER TABLE orders DROP CONSTRAINT IF EXISTS fk_orders_table;
ALTER TABLE orders ADD CONSTRAINT fk_orders_table
    FOREIGN KEY (table_id) REFERENCES tables(id) ON DELETE SET NULL;

ALTER TABLE orders DROP CONSTRAINT IF EXISTS fk_orders_waiter;
ALTER TABLE orders ADD CONSTRAINT fk_orders_waiter
    FOREIGN KEY (waiter_id) REFERENCES waiters(id) ON DELETE SET NULL;

-- Add check constraint for order_source
ALTER TABLE orders DROP CONSTRAINT IF EXISTS chk_order_source;
ALTER TABLE orders ADD CONSTRAINT chk_order_source CHECK (
    order_source IS NULL OR
    order_source IN ('WEB', 'MOBILE', 'PHONE', 'WAITER', 'KIOSK', 'POS')
);

-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_orders_table_id ON orders(table_id);
CREATE INDEX IF NOT EXISTS idx_orders_waiter_id ON orders(waiter_id);
CREATE INDEX IF NOT EXISTS idx_orders_order_source ON orders(order_source);
CREATE INDEX IF NOT EXISTS idx_payments_completed_at ON payments(completed_at);
CREATE INDEX IF NOT EXISTS idx_payments_refunded_at ON payments(refunded_at);

-- Add comments
COMMENT ON COLUMN payments.completed_at IS 'Timestamp when payment was completed successfully';
COMMENT ON COLUMN payments.refunded_at IS 'Timestamp when payment was refunded';
COMMENT ON COLUMN orders.order_source IS 'Source of the order: WEB, MOBILE, PHONE, WAITER, KIOSK, POS';
COMMENT ON COLUMN orders.table_id IS 'Reference to table for dine-in orders';
COMMENT ON COLUMN orders.waiter_id IS 'Reference to waiter assigned to the order';
