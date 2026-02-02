-- Add is_available column to products table
-- This column was already defined in Product.java but missing from migrations
ALTER TABLE products ADD COLUMN IF NOT EXISTS is_available BOOLEAN DEFAULT TRUE;

-- Create index for faster filtering by availability
CREATE INDEX IF NOT EXISTS idx_products_is_available ON products(is_available);

COMMENT ON COLUMN products.is_available IS 'Indicates whether the product is available for ordering';
