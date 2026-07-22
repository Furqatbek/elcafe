-- Add bundle support to self-service cart items

-- Make product_id nullable to allow bundle-only cart items
ALTER TABLE self_service_cart_items ALTER COLUMN product_id DROP NOT NULL;

-- Add bundle fields
ALTER TABLE self_service_cart_items ADD COLUMN IF NOT EXISTS bundle_id BIGINT;
ALTER TABLE self_service_cart_items ADD COLUMN IF NOT EXISTS bundle_name VARCHAR(255);
ALTER TABLE self_service_cart_items ADD COLUMN IF NOT EXISTS is_bundle BOOLEAN DEFAULT FALSE;

-- Add index for bundle lookups
CREATE INDEX IF NOT EXISTS idx_self_service_cart_items_bundle_id ON self_service_cart_items(bundle_id);
