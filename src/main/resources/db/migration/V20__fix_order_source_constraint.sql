-- Fix order_source constraint to match OrderSource enum
-- Updates constraint values from WEB, MOBILE, etc. to WEBSITE, MOBILE_APP, etc.

-- First, normalize existing order_source values to uppercase
UPDATE orders SET order_source = UPPER(order_source) WHERE order_source IS NOT NULL;

-- Map old values to new enum values
UPDATE orders SET order_source = 'WEBSITE' WHERE order_source = 'WEB';
UPDATE orders SET order_source = 'MOBILE_APP' WHERE order_source = 'MOBILE';
UPDATE orders SET order_source = 'PHONE_CALL' WHERE order_source = 'PHONE';
UPDATE orders SET order_source = 'OTHER' WHERE order_source = 'KIOSK';
UPDATE orders SET order_source = 'OTHER' WHERE order_source = 'POS';
-- WAITER stays the same

-- Convert any remaining invalid values to 'OTHER'
UPDATE orders SET order_source = 'OTHER'
WHERE order_source IS NOT NULL
  AND order_source NOT IN ('TELEGRAM_BOT', 'WEBSITE', 'ADMIN_PANEL', 'MOBILE_APP', 'PHONE_CALL', 'WALK_IN', 'WAITER', 'OTHER');

-- Drop old constraint
ALTER TABLE orders DROP CONSTRAINT IF EXISTS chk_order_source;

-- Add new constraint with correct enum values
ALTER TABLE orders ADD CONSTRAINT chk_order_source CHECK (
    order_source IS NULL OR
    order_source IN ('TELEGRAM_BOT', 'WEBSITE', 'ADMIN_PANEL', 'MOBILE_APP', 'PHONE_CALL', 'WALK_IN', 'WAITER', 'OTHER')
);

-- Update column comment to reflect new values
COMMENT ON COLUMN orders.order_source IS 'Channel through which order was placed: TELEGRAM_BOT, WEBSITE, ADMIN_PANEL, MOBILE_APP, PHONE_CALL, WALK_IN, WAITER, OTHER';
