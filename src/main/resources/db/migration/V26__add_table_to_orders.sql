-- Add table_id column to orders table for dine-in orders
ALTER TABLE orders ADD COLUMN table_id BIGINT REFERENCES restaurant_tables(id) ON DELETE SET NULL;

-- Create index for table lookups
CREATE INDEX idx_orders_table ON orders(table_id);

-- Add comment
COMMENT ON COLUMN orders.table_id IS 'Table ID for dine-in orders (null for delivery/takeout orders)';
