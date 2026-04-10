-- Add batch deduction quantity to product variants
-- Defines how much to deduct from a production batch per unit ordered
-- e.g., "1 Portion" variant with batchDeductionQuantity=5 deducts 5 pieces per order
ALTER TABLE product_variants
    ADD COLUMN batch_deduction_quantity DECIMAL(10,4);

COMMENT ON COLUMN product_variants.batch_deduction_quantity
    IS 'How much to deduct from production batch per unit ordered. NULL = use order quantity or weight.';
