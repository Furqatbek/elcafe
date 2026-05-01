ALTER TABLE order_items
    ADD COLUMN is_packaging_item BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN order_items.is_packaging_item IS 'True if this item was auto-added as packaging for delivery/takeaway';
