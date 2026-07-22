-- Add is_available column to product_variants table
ALTER TABLE product_variants ADD COLUMN IF NOT EXISTS is_available BOOLEAN DEFAULT TRUE;
