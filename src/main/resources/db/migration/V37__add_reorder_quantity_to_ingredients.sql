-- Add reorder_quantity field to inventory_ingredients table
-- This specifies how much to order when stock falls below reorder level

ALTER TABLE inventory_ingredients
ADD COLUMN reorder_quantity DECIMAL(10,2);

-- Set default reorder_quantity based on reorder_level for existing ingredients
UPDATE inventory_ingredients
SET reorder_quantity = reorder_level * 2
WHERE reorder_quantity IS NULL AND reorder_level IS NOT NULL;
