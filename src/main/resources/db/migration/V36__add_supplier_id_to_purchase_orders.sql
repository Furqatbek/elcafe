-- Add supplier_id foreign key to financial_purchase_orders table
-- This links purchase orders to the suppliers table for better tracking

ALTER TABLE financial_purchase_orders
ADD COLUMN supplier_id BIGINT REFERENCES suppliers(id);

-- Create index for faster lookups
CREATE INDEX IF NOT EXISTS idx_purchase_order_supplier_id ON financial_purchase_orders(supplier_id);

-- Update existing purchase orders to link to suppliers by name (if matching supplier exists)
UPDATE financial_purchase_orders po
SET supplier_id = s.id
FROM suppliers s
WHERE po.supplier_name = s.name
  AND po.restaurant_id = s.restaurant_id
  AND po.supplier_id IS NULL;
