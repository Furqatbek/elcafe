-- Valuation Settings Table
CREATE TABLE valuation_settings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    restaurant_id BIGINT NOT NULL,
    valuation_method VARCHAR(20) NOT NULL,
    effective_from DATETIME NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by VARCHAR(100),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_valuation_restaurant (restaurant_id),
    INDEX idx_valuation_active (is_active),
    CONSTRAINT fk_valuation_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurants(id)
);

-- Ingredient Cost History Table
CREATE TABLE ingredient_cost_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ingredient_id BIGINT NOT NULL,
    cost_per_unit DECIMAL(15, 4) NOT NULL,
    previous_cost DECIMAL(15, 4),
    effective_from DATETIME NOT NULL,
    effective_to DATETIME,
    reason VARCHAR(50) NOT NULL,
    source_reference VARCHAR(100),
    notes TEXT,
    created_by VARCHAR(100),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_cost_history_ingredient (ingredient_id),
    INDEX idx_cost_history_date (effective_from),
    CONSTRAINT fk_cost_history_ingredient FOREIGN KEY (ingredient_id) REFERENCES inventory_ingredients(id)
);

-- Batch Consumption Table
CREATE TABLE batch_consumptions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ingredient_id BIGINT NOT NULL,
    batch_id BIGINT NOT NULL,
    transaction_id BIGINT,
    order_id BIGINT,
    order_item_id BIGINT,
    quantity DECIMAL(10, 3) NOT NULL,
    cost_per_unit DECIMAL(15, 4) NOT NULL,
    total_cost DECIMAL(15, 4) NOT NULL,
    valuation_method VARCHAR(20) NOT NULL,
    consumed_at DATETIME NOT NULL,
    batch_number VARCHAR(100),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_consumption_batch (batch_id),
    INDEX idx_consumption_transaction (transaction_id),
    INDEX idx_consumption_order (order_id),
    INDEX idx_consumption_ingredient (ingredient_id),
    INDEX idx_consumption_date (consumed_at),
    CONSTRAINT fk_consumption_ingredient FOREIGN KEY (ingredient_id) REFERENCES inventory_ingredients(id),
    CONSTRAINT fk_consumption_batch FOREIGN KEY (batch_id) REFERENCES inventory_batches(id),
    CONSTRAINT fk_consumption_transaction FOREIGN KEY (transaction_id) REFERENCES inventory_transactions(id)
);
