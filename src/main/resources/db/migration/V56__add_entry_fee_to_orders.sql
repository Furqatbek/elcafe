-- Add entry fee column to orders table
ALTER TABLE orders ADD COLUMN IF NOT EXISTS entry_fee DECIMAL(10, 2) DEFAULT 0;
