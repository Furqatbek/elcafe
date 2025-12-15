-- Migration V6: Enhanced Menu Management System
-- Adds ingredients, menu collections, linked items, and enhanced product fields

-- Ingredients Table
CREATE TABLE ingredients (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    unit VARCHAR(50) NOT NULL, -- kg, liter, piece, gram, etc.
    cost_per_unit DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    current_stock DECIMAL(10, 2) DEFAULT 0.00,
    minimum_stock DECIMAL(10, 2) DEFAULT 0.00,
    supplier VARCHAR(200),
    category VARCHAR(100), -- dairy, meat, vegetables, spices, etc.
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ingredients_name_unique UNIQUE (name)
);

-- Product Ingredients (Many-to-Many relationship)
CREATE TABLE product_ingredients (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL,
    ingredient_id BIGINT NOT NULL,
    quantity DECIMAL(10, 3) NOT NULL, -- How much of the ingredient is used
    unit VARCHAR(50) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
    FOREIGN KEY (ingredient_id) REFERENCES ingredients(id) ON DELETE CASCADE,
    CONSTRAINT product_ingredients_unique UNIQUE (product_id, ingredient_id)
);

-- Add new fields to products table
ALTER TABLE products
ADD COLUMN IF NOT EXISTS price_with_margin DECIMAL(10, 2),
ADD COLUMN IF NOT EXISTS item_type VARCHAR(100),
ADD COLUMN IF NOT EXISTS cost_price DECIMAL(10, 2) DEFAULT 0.00,
ADD COLUMN IF NOT EXISTS margin_percentage DECIMAL(5, 2) DEFAULT 0.00;

-- Menu Collections (e.g., "Winter Menu", "Summer Specials")
CREATE TABLE menu_collections (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT NOT NULL,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    image_url VARCHAR(500),
    is_active BOOLEAN DEFAULT TRUE,
    start_date DATE,
    end_date DATE,
    sort_order INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (restaurant_id) REFERENCES restaurants(id) ON DELETE CASCADE
);

-- Menu Collection Items (Products in a menu collection)
CREATE TABLE menu_collection_items (
    id BIGSERIAL PRIMARY KEY,
    menu_collection_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    sort_order INTEGER DEFAULT 0,
    is_featured BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (menu_collection_id) REFERENCES menu_collections(id) ON DELETE CASCADE,
    FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
    CONSTRAINT menu_collection_items_unique UNIQUE (menu_collection_id, product_id)
);

-- Linked/Recommended Items (for cross-selling)
CREATE TABLE linked_items (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL,
    linked_product_id BIGINT NOT NULL,
    link_type VARCHAR(50) NOT NULL, -- RECOMMENDED, FREQUENTLY_BOUGHT_TOGETHER, SIMILAR, UPSELL
    sort_order INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
    FOREIGN KEY (linked_product_id) REFERENCES products(id) ON DELETE CASCADE,
    CONSTRAINT linked_items_unique UNIQUE (product_id, linked_product_id, link_type),
    CONSTRAINT linked_items_check CHECK (product_id != linked_product_id)
);

-- Indexes for performance
CREATE INDEX idx_ingredients_name ON ingredients(name);
CREATE INDEX idx_ingredients_category ON ingredients(category);
CREATE INDEX idx_ingredients_is_active ON ingredients(is_active);

CREATE INDEX idx_product_ingredients_product ON product_ingredients(product_id);
CREATE INDEX idx_product_ingredients_ingredient ON product_ingredients(ingredient_id);

CREATE INDEX idx_products_item_type ON products(item_type);
CREATE INDEX idx_products_cost_price ON products(cost_price);

CREATE INDEX idx_menu_collections_restaurant ON menu_collections(restaurant_id);
CREATE INDEX idx_menu_collections_is_active ON menu_collections(is_active);
CREATE INDEX idx_menu_collections_dates ON menu_collections(start_date, end_date);

CREATE INDEX idx_menu_collection_items_collection ON menu_collection_items(menu_collection_id);
CREATE INDEX idx_menu_collection_items_product ON menu_collection_items(product_id);

CREATE INDEX idx_linked_items_product ON linked_items(product_id);
CREATE INDEX idx_linked_items_linked_product ON linked_items(linked_product_id);
CREATE INDEX idx_linked_items_type ON linked_items(link_type);

-- Comments
COMMENT ON TABLE ingredients IS 'Ingredients used in menu items';
COMMENT ON TABLE product_ingredients IS 'Links products to their ingredients with quantities';
COMMENT ON TABLE menu_collections IS 'Named collections of menu items (e.g., Winter Menu)';
COMMENT ON TABLE menu_collection_items IS 'Products included in menu collections';
COMMENT ON TABLE linked_items IS 'Linked/recommended items for cross-selling';

COMMENT ON COLUMN products.price_with_margin IS 'Selling price including margin';
COMMENT ON COLUMN products.item_type IS 'Type of item: BURGER, PIZZA, DRINK, etc.';
COMMENT ON COLUMN products.cost_price IS 'Cost to produce the item (sum of ingredients)';
COMMENT ON COLUMN products.margin_percentage IS 'Profit margin percentage';
