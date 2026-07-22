-- Add promotion_name column to orders table for storing promotion display names
ALTER TABLE orders ADD COLUMN IF NOT EXISTS promotion_name VARCHAR(200);

-- Add comment for documentation
COMMENT ON COLUMN orders.promotion_name IS 'Display name of the promotion/happy hour applied to this order';
