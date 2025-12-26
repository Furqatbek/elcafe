-- Add service fee columns to orders table
ALTER TABLE orders ADD COLUMN IF NOT EXISTS service_fee_percent DECIMAL(5, 2) DEFAULT 0;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS service_fee DECIMAL(10, 2) DEFAULT 0;
