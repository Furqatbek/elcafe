-- Add dining_table_id column to orders table for dine-in orders
-- Note: table_id already exists and references waiter module's tables
ALTER TABLE orders ADD COLUMN IF NOT EXISTS dining_table_id BIGINT REFERENCES restaurant_tables(id) ON DELETE SET NULL;

-- Create index for dining table lookups
CREATE INDEX IF NOT EXISTS idx_orders_dining_table ON orders(dining_table_id);

-- Add comment
COMMENT ON COLUMN orders.dining_table_id IS 'Restaurant table ID for dine-in orders (null for delivery/takeout orders)';
