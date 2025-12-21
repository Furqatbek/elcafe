-- Valuation Settings Table
CREATE TABLE valuation_settings (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT NOT NULL REFERENCES restaurants(id),
    valuation_method VARCHAR(20) NOT NULL,
    effective_from TIMESTAMP NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_valuation_restaurant ON valuation_settings(restaurant_id);
CREATE INDEX idx_valuation_active ON valuation_settings(is_active);

-- Ingredient Cost History Table
CREATE TABLE ingredient_cost_history (
    id BIGSERIAL PRIMARY KEY,
    ingredient_id BIGINT NOT NULL REFERENCES inventory_ingredients(id),
    cost_per_unit DECIMAL(15, 4) NOT NULL,
    previous_cost DECIMAL(15, 4),
    effective_from TIMESTAMP NOT NULL,
    effective_to TIMESTAMP,
    reason VARCHAR(50) NOT NULL,
    source_reference VARCHAR(100),
    notes TEXT,
    created_by VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_cost_history_ingredient ON ingredient_cost_history(ingredient_id);
CREATE INDEX idx_cost_history_date ON ingredient_cost_history(effective_from);

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
