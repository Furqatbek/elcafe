-- Add all missing columns for valuation feature
-- This ensures all entity columns are present in the database

-- ============================================
-- inventory_transactions: Add cost tracking columns
-- ============================================
ALTER TABLE inventory_transactions
ADD COLUMN IF NOT EXISTS batch_id BIGINT REFERENCES inventory_batches(id);

ALTER TABLE inventory_transactions
ADD COLUMN IF NOT EXISTS cost_per_unit DECIMAL(15, 4);

ALTER TABLE inventory_transactions
ADD COLUMN IF NOT EXISTS total_cost DECIMAL(15, 4);

ALTER TABLE inventory_transactions
ADD COLUMN IF NOT EXISTS valuation_method VARCHAR(20);

CREATE INDEX IF NOT EXISTS idx_inventory_transactions_batch ON inventory_transactions(batch_id);

-- ============================================
-- inventory_ingredients: Add supplier reference
-- ============================================
ALTER TABLE inventory_ingredients
ADD COLUMN IF NOT EXISTS supplier_id BIGINT REFERENCES suppliers(id);

CREATE INDEX IF NOT EXISTS idx_inventory_ingredients_supplier ON inventory_ingredients(supplier_id);
