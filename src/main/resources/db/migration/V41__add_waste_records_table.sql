-- Waste Management Feature
-- Tracks inventory waste by reason with cost analysis

-- Waste Records table
CREATE TABLE inventory_waste_records (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT NOT NULL REFERENCES restaurants(id),
    ingredient_id BIGINT NOT NULL REFERENCES inventory_ingredients(id),
    batch_id BIGINT REFERENCES inventory_batches(id),
    waste_date DATE NOT NULL,
    quantity DECIMAL(10,3) NOT NULL,
    unit_cost DECIMAL(15,2),
    total_cost DECIMAL(15,2),
    waste_reason VARCHAR(30) NOT NULL,
    recorded_by VARCHAR(100),
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Indexes for performance
CREATE INDEX idx_waste_records_restaurant ON inventory_waste_records(restaurant_id);
CREATE INDEX idx_waste_records_ingredient ON inventory_waste_records(ingredient_id);
CREATE INDEX idx_waste_records_date ON inventory_waste_records(waste_date);
CREATE INDEX idx_waste_records_reason ON inventory_waste_records(waste_reason);
CREATE INDEX idx_waste_records_batch ON inventory_waste_records(batch_id);
