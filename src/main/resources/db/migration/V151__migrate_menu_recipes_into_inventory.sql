-- Migrate menu recipes into the inventory tables (split-brain fix).
--
-- THE PROBLEM
-- Two independent recipe systems exist:
--   menu:      product_ingredients            -> ingredients
--   inventory: inventory_product_ingredients  -> inventory_ingredients
-- Stock deduction (InventoryService) reads ONLY the inventory pair. A product
-- whose recipe was authored in the menu module therefore sells without ever
-- touching stock — the platform count stays high while the kitchen consumes,
-- which is a prime source of platform/kitchen divergence.
--
-- WHAT THIS DOES
--   1. Creates an inventory ingredient for each menu ingredient referenced by a
--      product that has no inventory recipe, in that product's restaurant
--      (products -> categories.restaurant_id), matched by name so existing
--      inventory ingredients are reused rather than duplicated.
--   2. Copies those menu recipe rows into inventory_product_ingredients.
--
-- WHY IT IS SAFE
--   * Only products with NO existing inventory recipe are touched, so a
--     half-migrated product can never end up with a doubled recipe.
--   * Newly created ingredients get track_inventory = FALSE. Deduction skips
--     untracked ingredients, so migrated recipes are INERT: they become visible
--     and costable but cannot refuse a sale for stock nobody has entered yet.
--     Stock the ingredient, then flip track_inventory on to start deducting.
--   * Idempotent: re-running inserts nothing (guards + ON CONFLICT DO NOTHING).
--   * Nothing is deleted; the menu tables are left untouched as the record of
--     origin.

-- 1. Create the missing inventory ingredients.
INSERT INTO inventory_ingredients (
    restaurant_id, name, description, unit,
    current_stock, minimum_stock, cost_per_unit, supplier,
    active, track_inventory
)
SELECT DISTINCT ON (c.restaurant_id, lower(trim(i.name)))
       c.restaurant_id,
       i.name,
       i.description,
       COALESCE(NULLIF(trim(i.unit), ''), 'pcs'),
       COALESCE(i.current_stock, 0),
       COALESCE(i.minimum_stock, 0),
       COALESCE(i.cost_per_unit, 0),
       i.supplier,
       TRUE,
       FALSE   -- inert until someone stocks it and enables tracking
FROM product_ingredients mpi
JOIN ingredients i  ON i.id = mpi.ingredient_id
JOIN products    p  ON p.id = mpi.product_id
JOIN categories  c  ON c.id = p.category_id
WHERE NOT EXISTS (
        SELECT 1 FROM inventory_product_ingredients x WHERE x.product_id = p.id
      )
  AND NOT EXISTS (
        SELECT 1 FROM inventory_ingredients ii
        WHERE ii.restaurant_id = c.restaurant_id
          AND lower(trim(ii.name)) = lower(trim(i.name))
      )
ORDER BY c.restaurant_id, lower(trim(i.name)), i.id
ON CONFLICT ON CONSTRAINT unique_inventory_ingredient_per_restaurant DO NOTHING;

-- 2. Copy the recipe rows, remapping menu ingredient ids to inventory ones.
INSERT INTO inventory_product_ingredients (
    product_id, ingredient_id, quantity_required, unit, notes, optional
)
SELECT mpi.product_id,
       ii.id,
       mpi.quantity,
       COALESCE(NULLIF(trim(mpi.unit), ''), ii.unit),
       'Migrated from menu recipe (product_ingredients) by V151',
       FALSE
FROM product_ingredients mpi
JOIN ingredients i  ON i.id = mpi.ingredient_id
JOIN products    p  ON p.id = mpi.product_id
JOIN categories  c  ON c.id = p.category_id
JOIN inventory_ingredients ii
       ON ii.restaurant_id = c.restaurant_id
      AND lower(trim(ii.name)) = lower(trim(i.name))
WHERE NOT EXISTS (
        SELECT 1 FROM inventory_product_ingredients x WHERE x.product_id = mpi.product_id
      )
ON CONFLICT ON CONSTRAINT unique_inventory_product_ingredient DO NOTHING;
