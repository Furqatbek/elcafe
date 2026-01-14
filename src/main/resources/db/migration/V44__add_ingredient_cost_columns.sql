-- Add valuation-related columns to inventory_ingredients table

ALTER TABLE inventory_ingredients
ADD COLUMN IF NOT EXISTS weighted_average_cost DECIMAL(15, 4);

ALTER TABLE inventory_ingredients
ADD COLUMN IF NOT EXISTS last_cost_update TIMESTAMP;
