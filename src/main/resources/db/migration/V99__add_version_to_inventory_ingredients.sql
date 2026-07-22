-- Add version column to inventory_ingredients for optimistic locking
-- This prevents race conditions when multiple transactions try to modify stock concurrently

ALTER TABLE inventory_ingredients
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

COMMENT ON COLUMN inventory_ingredients.version IS 'Optimistic locking version to prevent concurrent modification conflicts';
