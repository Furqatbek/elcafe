-- Add flag to products to indicate they use production batch flow
-- instead of direct per-portion recipe deduction
ALTER TABLE products
    ADD COLUMN uses_production_batch BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN products.uses_production_batch IS 'When true, orders deduct from prepared inventory (production batches) instead of raw ingredients';
