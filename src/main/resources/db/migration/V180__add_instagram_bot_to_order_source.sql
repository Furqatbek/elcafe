-- Add INSTAGRAM_BOT to order_source check constraint
-- Wave 7 lets a registered Instagram subscriber place an order directly in the DM conversation,
-- producing a real Order with orderSource = INSTAGRAM_BOT. Order.orderSource is
-- @Enumerated(EnumType.STRING) on a VARCHAR column, so the enum value plus this CHECK update is all
-- the persistence needs — mirrors V108 (SELF_SERVICE) exactly; no native Postgres enum type.

ALTER TABLE orders DROP CONSTRAINT IF EXISTS chk_order_source;

ALTER TABLE orders ADD CONSTRAINT chk_order_source CHECK (
    order_source IS NULL OR
    order_source IN ('TELEGRAM_BOT', 'WEBSITE', 'ADMIN_PANEL', 'MOBILE_APP', 'PHONE_CALL', 'WALK_IN', 'WAITER', 'SELF_SERVICE', 'INSTAGRAM_BOT', 'OTHER')
);

COMMENT ON COLUMN orders.order_source IS 'Channel through which order was placed: TELEGRAM_BOT, WEBSITE, ADMIN_PANEL, MOBILE_APP, PHONE_CALL, WALK_IN, WAITER, SELF_SERVICE, INSTAGRAM_BOT, OTHER';
