-- Change packaging rules to reference inventory ingredients instead of products
ALTER TABLE packaging_rules
    DROP CONSTRAINT IF EXISTS packaging_rules_packaging_product_id_fkey;

ALTER TABLE packaging_rules
    RENAME COLUMN packaging_product_id TO packaging_ingredient_id;

ALTER TABLE packaging_rules
    ADD CONSTRAINT packaging_rules_packaging_ingredient_id_fkey
    FOREIGN KEY (packaging_ingredient_id) REFERENCES inventory_ingredients(id);

COMMENT ON COLUMN packaging_rules.packaging_ingredient_id IS 'Inventory ingredient to auto-add (bag, bowl, spoon, etc.)';
