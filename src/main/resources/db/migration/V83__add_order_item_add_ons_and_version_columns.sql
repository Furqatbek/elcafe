-- V83: Add proper add-on tracking and optimistic locking support
-- This migration:
-- 1. Creates order_item_add_ons table to replace TEXT column storage
-- 2. Adds version columns for optimistic locking on critical entities

-- ============================================
-- 1. Create order_item_add_ons table
-- ============================================
-- Replaces the TEXT-based add_ons column in order_items with a proper relational structure.
-- Benefits:
--   - Query capability (e.g., find all orders with "extra cheese")
--   - Price integrity (modifier prices captured at order time)
--   - Reporting accuracy (can aggregate modifier usage and revenue)

CREATE TABLE order_item_add_ons (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_item_id BIGINT NOT NULL,
    add_on_id BIGINT NULL,
    add_on_name VARCHAR(200) NOT NULL,
    add_on_price DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    quantity INT NOT NULL DEFAULT 1,

    CONSTRAINT fk_order_item_add_ons_order_item
        FOREIGN KEY (order_item_id) REFERENCES order_items(id) ON DELETE CASCADE,

    -- Optional reference to the source add-on for analytics
    -- NULL if the add-on was deleted or is a custom modifier
    CONSTRAINT fk_order_item_add_ons_add_on
        FOREIGN KEY (add_on_id) REFERENCES addons(id) ON DELETE SET NULL
);

-- Index for efficient lookups by order item
CREATE INDEX idx_order_item_add_ons_order_item ON order_item_add_ons(order_item_id);

-- Index for querying add-on usage across orders (e.g., "find all orders with add-on X")
CREATE INDEX idx_order_item_add_ons_add_on ON order_item_add_ons(add_on_id);

-- Index for searching by add-on name (useful when add_on_id is NULL)
CREATE INDEX idx_order_item_add_ons_name ON order_item_add_ons(add_on_name);


-- ============================================
-- 2. Add version columns for optimistic locking
-- ============================================
-- Prevents race conditions in high-traffic POS scenarios:
--   - Two cashiers processing payments simultaneously
--   - Race conditions on remainingBalance calculations
--   - Lost updates on order modifications

-- Add version column to orders table
ALTER TABLE orders ADD COLUMN version BIGINT DEFAULT 0;

-- Add version column to payments table
ALTER TABLE payments ADD COLUMN version BIGINT DEFAULT 0;

-- Add version column to restaurant_tables table
ALTER TABLE restaurant_tables ADD COLUMN version BIGINT DEFAULT 0;


-- ============================================
-- 3. Migrate existing add_ons data (optional)
-- ============================================
-- Note: Existing add_ons are stored as comma-separated strings (e.g., "Cheese, Extra Onions")
-- without price information. We cannot fully migrate this data, but we preserve it in the
-- original column for backward compatibility. New orders will use the new structure.
-- The deprecated add_ons column will be removed in a future migration after verifying
-- all client code has been updated.
