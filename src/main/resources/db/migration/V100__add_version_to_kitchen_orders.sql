-- Add version column to kitchen_orders for optimistic locking
-- This prevents race conditions when multiple kitchen staff update the same order

ALTER TABLE kitchen_orders
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

COMMENT ON COLUMN kitchen_orders.version IS 'Optimistic locking version to prevent concurrent modification conflicts';
