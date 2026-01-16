-- Add bundle fields to order_items table for tracking bundle/combo sales
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS bundle_id BIGINT;
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS bundle_name VARCHAR(200);
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS is_bundle BOOLEAN DEFAULT FALSE;

-- Add index for bundle queries
CREATE INDEX IF NOT EXISTS idx_order_items_bundle_id ON order_items(bundle_id);

-- Add foreign key constraint to bundles table
ALTER TABLE order_items
    ADD CONSTRAINT fk_order_items_bundle
    FOREIGN KEY (bundle_id) REFERENCES bundles(id)
    ON DELETE SET NULL;

COMMENT ON COLUMN order_items.bundle_id IS 'Reference to bundle if this item was sold as part of a combo';
COMMENT ON COLUMN order_items.bundle_name IS 'Name of the bundle at time of sale';
COMMENT ON COLUMN order_items.is_bundle IS 'True if this item represents a bundle/combo purchase';
