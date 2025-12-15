-- Add RFM tracking columns for customer activity analysis

-- Add registration_source column to customers table
ALTER TABLE customers
ADD COLUMN IF NOT EXISTS registration_source VARCHAR(50) DEFAULT 'ADMIN_PANEL';

-- Add order_source column to orders table
ALTER TABLE orders
ADD COLUMN IF NOT EXISTS order_source VARCHAR(50) DEFAULT 'ADMIN_PANEL';

-- Add comments for documentation
COMMENT ON COLUMN customers.registration_source IS 'Source where the customer registered from (TELEGRAM_BOT, WEBSITE, ADMIN_PANEL, MOBILE_APP, PHONE_CALL, WALK_IN, OTHER)';
COMMENT ON COLUMN orders.order_source IS 'Source where the order was placed from (TELEGRAM_BOT, WEBSITE, ADMIN_PANEL, MOBILE_APP, PHONE_CALL, WALK_IN, OTHER)';
