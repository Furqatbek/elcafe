-- Stock Counts/Audits Feature
-- Implements cycle counting, physical inventory workflow, and variance tracking

-- Stock Count (Audit) header
CREATE TABLE stock_counts (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT NOT NULL REFERENCES restaurants(id),
    count_number VARCHAR(50) NOT NULL,
    count_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    scheduled_date DATE,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    approved_at TIMESTAMP,
    initiated_by VARCHAR(100),
    counted_by VARCHAR(100),
    reviewed_by VARCHAR(100),
    approved_by VARCHAR(100),
    notes TEXT,
    total_items INTEGER DEFAULT 0,
    counted_items INTEGER DEFAULT 0,
    variance_count INTEGER DEFAULT 0,
    total_variance_value DECIMAL(15,2) DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Stock Count Items (individual ingredient counts)
CREATE TABLE stock_count_items (
    id BIGSERIAL PRIMARY KEY,
    stock_count_id BIGINT NOT NULL REFERENCES stock_counts(id) ON DELETE CASCADE,
    ingredient_id BIGINT NOT NULL REFERENCES inventory_ingredients(id),
    system_quantity DECIMAL(10,3) NOT NULL,
    counted_quantity DECIMAL(10,3),
    variance_quantity DECIMAL(10,3),
    variance_value DECIMAL(15,2),
    variance_reason VARCHAR(50),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    counted_by VARCHAR(100),
    counted_at TIMESTAMP,
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Variance History (for tracking patterns over time)
CREATE TABLE stock_variance_history (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT NOT NULL REFERENCES restaurants(id),
    ingredient_id BIGINT NOT NULL REFERENCES inventory_ingredients(id),
    stock_count_id BIGINT REFERENCES stock_counts(id),
    variance_date DATE NOT NULL,
    system_quantity DECIMAL(10,3) NOT NULL,
    actual_quantity DECIMAL(10,3) NOT NULL,
    variance_quantity DECIMAL(10,3) NOT NULL,
    variance_percentage DECIMAL(5,2),
    variance_value DECIMAL(15,2),
    variance_reason VARCHAR(50),
    adjustment_made BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Indexes for performance
CREATE INDEX idx_stock_counts_restaurant ON stock_counts(restaurant_id);
CREATE INDEX idx_stock_counts_status ON stock_counts(status);
CREATE INDEX idx_stock_counts_date ON stock_counts(scheduled_date);
CREATE INDEX idx_stock_count_items_count ON stock_count_items(stock_count_id);
CREATE INDEX idx_stock_count_items_ingredient ON stock_count_items(ingredient_id);
CREATE INDEX idx_stock_count_items_status ON stock_count_items(status);
CREATE INDEX idx_variance_history_restaurant ON stock_variance_history(restaurant_id);
CREATE INDEX idx_variance_history_ingredient ON stock_variance_history(ingredient_id);
CREATE INDEX idx_variance_history_date ON stock_variance_history(variance_date);
