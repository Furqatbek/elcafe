-- User-defined ingredient categories per restaurant
CREATE TABLE ingredient_categories (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT NOT NULL REFERENCES restaurants(id),
    name VARCHAR(100) NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_ingredient_cat_name ON ingredient_categories(restaurant_id, name);
CREATE INDEX idx_ingredient_cat_restaurant ON ingredient_categories(restaurant_id);

COMMENT ON TABLE ingredient_categories IS 'User-defined categories for grouping inventory ingredients';

-- Add category reference to ingredients
ALTER TABLE inventory_ingredients
    ADD COLUMN category_id BIGINT REFERENCES ingredient_categories(id);

CREATE INDEX idx_ingredients_category ON inventory_ingredients(category_id);
