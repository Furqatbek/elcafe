-- Add service fee, entry fee, tip, and dine-in columns to orders table

ALTER TABLE orders ADD COLUMN IF NOT EXISTS service_fee_percent DECIMAL(5, 2);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS service_fee DECIMAL(10, 2);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS entry_fee DECIMAL(10, 2);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS tip_amount DECIMAL(10, 2);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS grand_total DECIMAL(10, 2);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS guest_count INTEGER;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS table_ids VARCHAR(255);
