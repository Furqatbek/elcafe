-- Make recipe links structurally undeletable via an ingredient.
--
-- inventory_product_ingredients.ingredient_id was created ON DELETE CASCADE, so
-- deleting a single ingredient silently destroyed every recipe referencing it,
-- across all products, with no warning and no trace. The API now deactivates a
-- referenced ingredient instead of deleting it, but that guard only covers one
-- code path — manual SQL, a future endpoint, or a script would still cascade.
--
-- Switching the constraint to ON DELETE RESTRICT pushes the rule into the
-- database: an ingredient that is used by any recipe simply cannot be deleted.
-- The delete must first remove the ingredient from the recipes that use it,
-- which is an explicit, visible act.
--
-- inventory_batches.ingredient_id keeps CASCADE on purpose — batches are stock
-- records belonging to the ingredient, not shared references like recipes.

DO $$
DECLARE
    fk_name text;
BEGIN
    -- Find the existing FK on ingredient_id whatever Postgres auto-named it.
    SELECT con.conname INTO fk_name
    FROM pg_constraint con
    JOIN pg_attribute att
      ON att.attrelid = con.conrelid
     AND att.attnum = con.conkey[1]
    WHERE con.conrelid = 'inventory_product_ingredients'::regclass
      AND con.contype = 'f'
      AND array_length(con.conkey, 1) = 1
      AND att.attname = 'ingredient_id';

    IF fk_name IS NOT NULL THEN
        EXECUTE format('ALTER TABLE inventory_product_ingredients DROP CONSTRAINT %I', fk_name);
    END IF;

    ALTER TABLE inventory_product_ingredients
        ADD CONSTRAINT fk_ipi_ingredient_restrict
        FOREIGN KEY (ingredient_id)
        REFERENCES inventory_ingredients(id)
        ON DELETE RESTRICT;
END $$;
