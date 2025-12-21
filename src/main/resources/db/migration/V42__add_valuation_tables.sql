-- Valuation Settings Table
CREATE TABLE valuation_settings (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT NOT NULL REFERENCES restaurants(id),
    valuation_method VARCHAR(20) NOT NULL,
    effective_from DATE NOT NULL,
    effective_to DATE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    notes TEXT,
    created_by VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_valuation_restaurant ON valuation_settings(restaurant_id);
CREATE INDEX idx_valuation_active ON valuation_settings(is_active);

-- Ingredient Cost History Table
CREATE TABLE ingredient_cost_history (
    id BIGSERIAL PRIMARY KEY,
    ingredient_id BIGINT NOT NULL REFERENCES inventory_ingredients(id),
    previous_cost DECIMAL(15, 4) NOT NULL,
    new_cost DECIMAL(15, 4) NOT NULL,
    weighted_average_cost DECIMAL(15, 4),
    reason VARCHAR(30) NOT NULL,
    effective_from TIMESTAMP NOT NULL,
    effective_to TIMESTAMP,
    batch_id BIGINT REFERENCES inventory_batches(id),
    purchase_order_id BIGINT,
    notes TEXT,
    created_by VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_cost_history_ingredient ON ingredient_cost_history(ingredient_id);
CREATE INDEX idx_cost_history_effective ON ingredient_cost_history(effective_from);
CREATE INDEX idx_cost_history_batch ON ingredient_cost_history(batch_id);

-- Batch Consumption Table
CREATE TABLE batch_consumptions (
    id BIGSERIAL PRIMARY KEY,
    ingredient_id BIGINT NOT NULL REFERENCES inventory_ingredients(id),
    batch_id BIGINT NOT NULL REFERENCES inventory_batches(id),
    transaction_id BIGINT REFERENCES inventory_transactions(id),
    order_id BIGINT,
    order_item_id BIGINT,
    quantity DECIMAL(10, 3) NOT NULL,
    cost_per_unit DECIMAL(15, 4) NOT NULL,
    total_cost DECIMAL(15, 4) NOT NULL,
    valuation_method VARCHAR(20) NOT NULL,
    consumed_at TIMESTAMP NOT NULL,
    batch_number VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_consumption_batch ON batch_consumptions(batch_id);
CREATE INDEX idx_consumption_transaction ON batch_consumptions(transaction_id);
CREATE INDEX idx_consumption_order ON batch_consumptions(order_id);
CREATE INDEX idx_consumption_ingredient ON batch_consumptions(ingredient_id);
CREATE INDEX idx_consumption_date ON batch_consumptions(consumed_at);
