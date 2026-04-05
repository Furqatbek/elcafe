-- Add SELF_SERVICE to order_source check constraint
-- The OrderSource enum includes SELF_SERVICE but the DB constraint was missing it,
-- causing self-service QR code orders to fail with check constraint violation.

ALTER TABLE orders DROP CONSTRAINT IF EXISTS chk_order_source;

ALTER TABLE orders ADD CONSTRAINT chk_order_source CHECK (
    order_source IS NULL OR
    order_source IN ('TELEGRAM_BOT', 'WEBSITE', 'ADMIN_PANEL', 'MOBILE_APP', 'PHONE_CALL', 'WALK_IN', 'WAITER', 'SELF_SERVICE', 'OTHER')
);

COMMENT ON COLUMN orders.order_source IS 'Channel through which order was placed: TELEGRAM_BOT, WEBSITE, ADMIN_PANEL, MOBILE_APP, PHONE_CALL, WALK_IN, WAITER, SELF_SERVICE, OTHER';
