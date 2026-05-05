-- Allow null product_id for packaging items in order_items
ALTER TABLE order_items ALTER COLUMN product_id DROP NOT NULL;
