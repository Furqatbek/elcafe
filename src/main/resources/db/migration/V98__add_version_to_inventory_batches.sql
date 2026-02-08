-- Add version column to inventory_batches for optimistic locking
-- This prevents race conditions when multiple transactions try to consume from the same batch

ALTER TABLE inventory_batches
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- Update the FEFO index to include version for efficient queries
DROP INDEX IF EXISTS idx_batch_fefo;
CREATE INDEX idx_batch_fefo ON inventory_batches(ingredient_id, status, quantity, expiry_date);

COMMENT ON COLUMN inventory_batches.version IS 'Optimistic locking version to prevent concurrent modification conflicts';
