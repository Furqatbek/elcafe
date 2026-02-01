-- =====================================================
-- V85: Database Design Improvements
-- 1. Convert timestamps to timezone-aware (TIMESTAMP WITH TIME ZONE)
-- 2. Add soft delete support (deleted_at, deleted_by columns)
-- 3. Add missing indexes for common query patterns
-- 4. Verify decimal precision for financial fields
-- =====================================================

-- =====================================================
-- ORDERS TABLE IMPROVEMENTS
-- =====================================================

-- Convert timeline timestamps to TIMESTAMP WITH TIME ZONE
-- This prevents data corruption during DST transitions
ALTER TABLE orders
    ALTER COLUMN scheduled_for TYPE TIMESTAMP WITH TIME ZONE,
    ALTER COLUMN placed_at TYPE TIMESTAMP WITH TIME ZONE,
    ALTER COLUMN accepted_at TYPE TIMESTAMP WITH TIME ZONE,
    ALTER COLUMN preparing_at TYPE TIMESTAMP WITH TIME ZONE,
    ALTER COLUMN ready_at TYPE TIMESTAMP WITH TIME ZONE,
    ALTER COLUMN picked_up_at TYPE TIMESTAMP WITH TIME ZONE,
    ALTER COLUMN completed_at TYPE TIMESTAMP WITH TIME ZONE,
    ALTER COLUMN cancelled_at TYPE TIMESTAMP WITH TIME ZONE,
    ALTER COLUMN rejected_at TYPE TIMESTAMP WITH TIME ZONE,
    ALTER COLUMN voided_at TYPE TIMESTAMP WITH TIME ZONE,
    ALTER COLUMN created_at TYPE TIMESTAMP WITH TIME ZONE,
    ALTER COLUMN updated_at TYPE TIMESTAMP WITH TIME ZONE;

-- Add soft delete columns to orders
ALTER TABLE orders ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS deleted_by VARCHAR(100);

-- Add missing indexes for orders table
CREATE INDEX IF NOT EXISTS idx_order_restaurant_status ON orders(restaurant_id, status);
CREATE INDEX IF NOT EXISTS idx_order_restaurant_created ON orders(restaurant_id, created_at);
CREATE INDEX IF NOT EXISTS idx_order_customer ON orders(customer_id);
CREATE INDEX IF NOT EXISTS idx_order_dining_table ON orders(dining_table_id);
CREATE INDEX IF NOT EXISTS idx_order_waiter ON orders(waiter_id);
CREATE INDEX IF NOT EXISTS idx_order_status ON orders(status);
CREATE INDEX IF NOT EXISTS idx_order_created_at ON orders(created_at);
CREATE INDEX IF NOT EXISTS idx_order_payment_intent ON orders(payment_intent_id);
CREATE INDEX IF NOT EXISTS idx_order_deleted_at ON orders(deleted_at);

-- =====================================================
-- PAYMENTS TABLE IMPROVEMENTS
-- =====================================================

-- Convert timestamps to TIMESTAMP WITH TIME ZONE
ALTER TABLE payments
    ALTER COLUMN paid_at TYPE TIMESTAMP WITH TIME ZONE,
    ALTER COLUMN completed_at TYPE TIMESTAMP WITH TIME ZONE,
    ALTER COLUMN refunded_at TYPE TIMESTAMP WITH TIME ZONE,
    ALTER COLUMN created_at TYPE TIMESTAMP WITH TIME ZONE,
    ALTER COLUMN updated_at TYPE TIMESTAMP WITH TIME ZONE;

-- Add soft delete columns to payments (financial records should never be hard deleted)
ALTER TABLE payments ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE payments ADD COLUMN IF NOT EXISTS deleted_by VARCHAR(100);

-- Add missing indexes for payments table
CREATE INDEX IF NOT EXISTS idx_payment_order ON payments(order_id);
CREATE INDEX IF NOT EXISTS idx_payment_status ON payments(status);
CREATE INDEX IF NOT EXISTS idx_payment_transaction_id ON payments(transaction_id);
CREATE INDEX IF NOT EXISTS idx_payment_created_at ON payments(created_at);
CREATE INDEX IF NOT EXISTS idx_payment_deleted_at ON payments(deleted_at);

-- =====================================================
-- ORDER_ITEMS TABLE IMPROVEMENTS
-- =====================================================

-- Add soft delete columns to order_items
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS deleted_by VARCHAR(100);

-- Add missing indexes for order_items table
CREATE INDEX IF NOT EXISTS idx_order_item_order ON order_items(order_id);
CREATE INDEX IF NOT EXISTS idx_order_item_product ON order_items(product_id);
CREATE INDEX IF NOT EXISTS idx_order_item_deleted_at ON order_items(deleted_at);

-- =====================================================
-- VERIFY DECIMAL PRECISION FOR FINANCIAL FIELDS
-- All monetary fields should be DECIMAL(10,2)
-- =====================================================

-- Orders table financial fields
ALTER TABLE orders
    ALTER COLUMN subtotal TYPE DECIMAL(10,2),
    ALTER COLUMN delivery_fee TYPE DECIMAL(10,2),
    ALTER COLUMN tax TYPE DECIMAL(10,2),
    ALTER COLUMN discount TYPE DECIMAL(10,2),
    ALTER COLUMN bonus_used TYPE DECIMAL(10,2),
    ALTER COLUMN service_fee_percent TYPE DECIMAL(5,2),
    ALTER COLUMN service_fee TYPE DECIMAL(10,2),
    ALTER COLUMN entry_fee TYPE DECIMAL(10,2),
    ALTER COLUMN total TYPE DECIMAL(10,2),
    ALTER COLUMN tip_amount TYPE DECIMAL(10,2),
    ALTER COLUMN grand_total TYPE DECIMAL(10,2);

-- Payments table financial fields
ALTER TABLE payments
    ALTER COLUMN amount TYPE DECIMAL(10,2),
    ALTER COLUMN tip_amount TYPE DECIMAL(10,2),
    ALTER COLUMN refunded_amount TYPE DECIMAL(10,2),
    ALTER COLUMN amount_tendered TYPE DECIMAL(10,2),
    ALTER COLUMN change_due TYPE DECIMAL(10,2);

-- Order items table financial fields
ALTER TABLE order_items
    ALTER COLUMN unit_price TYPE DECIMAL(10,2),
    ALTER COLUMN total_price TYPE DECIMAL(10,2);

-- =====================================================
-- ADD COMMENTS FOR DOCUMENTATION
-- =====================================================

COMMENT ON COLUMN orders.deleted_at IS 'Soft delete timestamp - financial records should never be hard deleted';
COMMENT ON COLUMN orders.deleted_by IS 'User who performed the soft delete';
COMMENT ON COLUMN payments.deleted_at IS 'Soft delete timestamp - financial records should never be hard deleted';
COMMENT ON COLUMN payments.deleted_by IS 'User who performed the soft delete';
COMMENT ON COLUMN order_items.deleted_at IS 'Soft delete timestamp for audit trail';
COMMENT ON COLUMN order_items.deleted_by IS 'User who performed the soft delete';
