-- Create ingredients table
CREATE TABLE ingredients (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    unit VARCHAR(50) NOT NULL,
    current_stock DECIMAL(10, 3) NOT NULL DEFAULT 0,
    minimum_stock DECIMAL(10, 3) NOT NULL DEFAULT 0,
    reorder_level DECIMAL(10, 3) NOT NULL DEFAULT 0,
    cost_per_unit DECIMAL(10, 2),
    supplier VARCHAR(100),
    sku VARCHAR(50),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    track_inventory BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_ingredient_per_restaurant UNIQUE (restaurant_id, name)
);

CREATE INDEX idx_ingredients_restaurant ON ingredients(restaurant_id);
CREATE INDEX idx_ingredients_active ON ingredients(active);
CREATE INDEX idx_ingredients_low_stock ON ingredients(restaurant_id, current_stock, minimum_stock);

-- Create product_ingredients table (recipe/composition)
CREATE TABLE product_ingredients (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    ingredient_id BIGINT NOT NULL REFERENCES ingredients(id) ON DELETE CASCADE,
    quantity_required DECIMAL(10, 3) NOT NULL,
    unit VARCHAR(50),
    notes TEXT,
    optional BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_product_ingredient UNIQUE (product_id, ingredient_id)
);

CREATE INDEX idx_product_ingredients_product ON product_ingredients(product_id);
CREATE INDEX idx_product_ingredients_ingredient ON product_ingredients(ingredient_id);

-- Create inventory_transactions table
CREATE TABLE inventory_transactions (
    id BIGSERIAL PRIMARY KEY,
    ingredient_id BIGINT NOT NULL REFERENCES ingredients(id) ON DELETE CASCADE,
    type VARCHAR(50) NOT NULL,
    quantity DECIMAL(10, 3) NOT NULL,
    balance_before DECIMAL(10, 3) NOT NULL,
    balance_after DECIMAL(10, 3) NOT NULL,
    reference_type VARCHAR(50),
    reference_id BIGINT,
    notes TEXT,
    performed_by VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_inventory_transactions_ingredient ON inventory_transactions(ingredient_id);
CREATE INDEX idx_inventory_transactions_type ON inventory_transactions(type);
CREATE INDEX idx_inventory_transactions_reference ON inventory_transactions(reference_type, reference_id);
CREATE INDEX idx_inventory_transactions_created_at ON inventory_transactions(created_at);

-- Add comments
COMMENT ON TABLE ingredients IS 'Stores all ingredients used in restaurant products';
COMMENT ON TABLE product_ingredients IS 'Junction table linking products to their required ingredients (recipe/BOM)';
COMMENT ON TABLE inventory_transactions IS 'Audit trail for all inventory movements';

COMMENT ON COLUMN ingredients.current_stock IS 'Current available stock quantity';
COMMENT ON COLUMN ingredients.minimum_stock IS 'Minimum stock level before alert';
COMMENT ON COLUMN ingredients.reorder_level IS 'Stock level that triggers reorder';
COMMENT ON COLUMN ingredients.track_inventory IS 'Whether to track inventory for this ingredient';

COMMENT ON COLUMN product_ingredients.quantity_required IS 'Quantity of ingredient needed per product unit';
COMMENT ON COLUMN product_ingredients.optional IS 'Whether ingredient is optional (not deducted from stock)';

COMMENT ON COLUMN inventory_transactions.type IS 'PURCHASE, ORDER_DEDUCTION, ADJUSTMENT, WASTE, etc.';
COMMENT ON COLUMN inventory_transactions.reference_type IS 'Type of related entity (ORDER, PURCHASE, etc.)';
COMMENT ON COLUMN inventory_transactions.reference_id IS 'ID of related entity';
