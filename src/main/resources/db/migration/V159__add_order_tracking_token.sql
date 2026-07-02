-- Public order tracking secret (RBAC audit #17). Order numbers are human-friendly, sequential and
-- therefore enumerable, so they cannot authorize the public tracking endpoint on their own. Each order
-- gets an unguessable tracking_token; the public /public/orders/{orderNumber}/status endpoint now
-- requires it, so an attacker walking order numbers can no longer read arbitrary orders' PII.

ALTER TABLE orders ADD COLUMN tracking_token VARCHAR(64);

-- Backfill existing rows with a random 64-hex-char token (two md5s; no pgcrypto extension needed).
UPDATE orders
SET tracking_token = md5(random()::text || clock_timestamp()::text || id::text)
                   || md5(random()::text || clock_timestamp()::text || id::text || 'salt')
WHERE tracking_token IS NULL;

ALTER TABLE orders ALTER COLUMN tracking_token SET NOT NULL;
CREATE UNIQUE INDEX ux_orders_tracking_token ON orders (tracking_token);
