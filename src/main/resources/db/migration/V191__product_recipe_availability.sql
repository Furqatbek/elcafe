-- V191: derive "can the kitchen actually make this?" from ingredient stock.
--
-- products.in_stock is a switch a person flips. It says nothing about whether the walk-in has the
-- ingredients, so a dish whose last kilo of beef went an hour ago stays on sale until somebody
-- notices. On our own screens that is survivable — the order is refused at the accept step and a
-- human sorts it out. On an aggregator it is not: their customer has already paid, and we find out
-- when the kitchen refuses an order we already took money for.
--
-- So this column is the OTHER half of availability, and the two are deliberately separate. in_stock
-- stays a human decision ("we are not serving this today"); recipe_available is computed from stock
-- and owned entirely by the system. An item is orderable only when both are true — which means a
-- manual 86 cannot be undone by a delivery arriving, and a restock cannot override a manager who
-- turned something off on purpose.
--
-- Defaults to true so nothing disappears on upgrade: a product with no recipe rows is always
-- makeable, and a product with a recipe is corrected the first time its ingredients move.

ALTER TABLE products
    ADD COLUMN recipe_available BOOLEAN NOT NULL DEFAULT true;

COMMENT ON COLUMN products.recipe_available IS
    'Computed (V191): false when a non-optional ingredient is short. ANDed with in_stock, which stays '
    'the manual switch. Never set by hand.';

-- The recompute writes this column and the partner menu reads it, both filtered by restaurant.
CREATE INDEX idx_products_recipe_available
    ON products(category_id) WHERE NOT recipe_available;
