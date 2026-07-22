-- Add new order lifecycle fields to orders table
-- Based on CLIENT_RESTAURANT_FLOW.md documentation

-- Add payment fields
ALTER TABLE orders ADD COLUMN IF NOT EXISTS payment_method VARCHAR(50);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS payment_status VARCHAR(20);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS payment_intent_id VARCHAR(255);

-- Add cancellation fields
ALTER TABLE orders ADD COLUMN IF NOT EXISTS cancellation_reason TEXT;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS cancelled_by VARCHAR(20);

-- Add order type
ALTER TABLE orders ADD COLUMN IF NOT EXISTS order_type VARCHAR(20) DEFAULT 'DELIVERY';

-- Add status timestamps
ALTER TABLE orders ADD COLUMN IF NOT EXISTS placed_at TIMESTAMP;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS accepted_at TIMESTAMP;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS preparing_at TIMESTAMP;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS ready_at TIMESTAMP;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS picked_up_at TIMESTAMP;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS completed_at TIMESTAMP;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS cancelled_at TIMESTAMP;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS rejected_at TIMESTAMP;

-- Add check constraint for payment_status
ALTER TABLE orders DROP CONSTRAINT IF EXISTS chk_payment_status;
ALTER TABLE orders ADD CONSTRAINT chk_payment_status CHECK (
    payment_status IS NULL OR
    payment_status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'REFUNDED')
);

-- Add check constraint for cancelled_by
ALTER TABLE orders DROP CONSTRAINT IF EXISTS chk_cancelled_by;
ALTER TABLE orders ADD CONSTRAINT chk_cancelled_by CHECK (
    cancelled_by IS NULL OR
    cancelled_by IN ('CONSUMER', 'ADMIN', 'SYSTEM')
);

-- Add check constraint for order_type
ALTER TABLE orders DROP CONSTRAINT IF EXISTS chk_order_type;
ALTER TABLE orders ADD CONSTRAINT chk_order_type CHECK (
    order_type IN ('DELIVERY', 'PICKUP', 'DINE_IN')
);

-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_orders_payment_status ON orders(payment_status);
CREATE INDEX IF NOT EXISTS idx_orders_order_type ON orders(order_type);
CREATE INDEX IF NOT EXISTS idx_orders_placed_at ON orders(placed_at);
CREATE INDEX IF NOT EXISTS idx_orders_completed_at ON orders(completed_at);

-- Add comment
COMMENT ON COLUMN orders.payment_method IS 'Payment method used: CARD, CASH, etc.';
COMMENT ON COLUMN orders.payment_status IS 'Payment status: PENDING, COMPLETED, FAILED, REFUNDED';
COMMENT ON COLUMN orders.payment_intent_id IS 'Payment gateway intent/transaction ID';
COMMENT ON COLUMN orders.cancellation_reason IS 'Reason for order cancellation';
COMMENT ON COLUMN orders.cancelled_by IS 'Who cancelled the order: CONSUMER, ADMIN, SYSTEM';
COMMENT ON COLUMN orders.order_type IS 'Order type: DELIVERY, PICKUP, DINE_IN';
COMMENT ON COLUMN orders.placed_at IS 'Timestamp when order was placed (payment completed)';
COMMENT ON COLUMN orders.accepted_at IS 'Timestamp when restaurant accepted the order';
COMMENT ON COLUMN orders.preparing_at IS 'Timestamp when kitchen started preparing';
COMMENT ON COLUMN orders.ready_at IS 'Timestamp when order was ready';
COMMENT ON COLUMN orders.picked_up_at IS 'Timestamp when driver picked up the order';
COMMENT ON COLUMN orders.completed_at IS 'Timestamp when order was completed/delivered';
COMMENT ON COLUMN orders.cancelled_at IS 'Timestamp when order was cancelled';
COMMENT ON COLUMN orders.rejected_at IS 'Timestamp when order was rejected';
