-- Add support for multi-table orders
-- table_ids stores comma-separated table IDs for orders spanning multiple tables
-- guest_count stores the number of guests for dine-in orders

ALTER TABLE orders ADD COLUMN table_ids VARCHAR(255);

ALTER TABLE orders ADD COLUMN guest_count INTEGER;

-- Add index for querying orders by table_ids
CREATE INDEX idx_orders_table_ids ON orders(table_ids);
