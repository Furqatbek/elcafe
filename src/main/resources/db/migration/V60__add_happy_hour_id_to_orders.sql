-- Add happy_hour_id column to orders table for tracking happy hour discounts
ALTER TABLE orders ADD COLUMN IF NOT EXISTS happy_hour_id BIGINT;

-- Add foreign key constraint
ALTER TABLE orders ADD CONSTRAINT fk_orders_happy_hour
    FOREIGN KEY (happy_hour_id) REFERENCES happy_hours(id) ON DELETE SET NULL;

-- Add index for better query performance
CREATE INDEX IF NOT EXISTS idx_orders_happy_hour_id ON orders(happy_hour_id);
