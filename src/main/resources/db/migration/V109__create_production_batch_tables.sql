-- Production Batch System: tracks batch-cooked items (soups, stews, etc.)
-- Adds intermediate "prepared inventory" layer between raw ingredients and orders

-- Main production batch table
CREATE TABLE production_batches (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT NOT NULL REFERENCES restaurants(id),
    product_id BIGINT REFERENCES products(id),
    batch_number VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    output_quantity DECIMAL(10,3) NOT NULL,
    output_unit VARCHAR(20) NOT NULL,
    remaining_quantity DECIMAL(10,3) NOT NULL,
    total_input_cost DECIMAL(15,4) NOT NULL DEFAULT 0,
    cost_per_unit DECIMAL(15,4),
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    prepared_by VARCHAR(100),
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    expires_at TIMESTAMP,
    notes TEXT,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_production_batch_restaurant ON production_batches(restaurant_id);
CREATE INDEX idx_production_batch_product ON production_batches(product_id);
CREATE INDEX idx_production_batch_status ON production_batches(status);
CREATE INDEX idx_production_batch_expires ON production_batches(expires_at);
CREATE INDEX idx_production_batch_fefo ON production_batches(product_id, status, remaining_quantity, expires_at);

COMMENT ON TABLE production_batches IS 'Tracks batch-cooked prepared items (soups, stews) with yield and cost tracking';
COMMENT ON COLUMN production_batches.output_quantity IS 'Total yield after cooking (e.g. 10.000 L)';
COMMENT ON COLUMN production_batches.remaining_quantity IS 'Decremented as portions are sold';
COMMENT ON COLUMN production_batches.cost_per_unit IS 'total_input_cost / output_quantity — calculated on batch completion';

-- Ingredient inputs consumed to produce a batch
CREATE TABLE production_batch_inputs (
    id BIGSERIAL PRIMARY KEY,
    production_batch_id BIGINT NOT NULL REFERENCES production_batches(id),
    ingredient_id BIGINT NOT NULL REFERENCES inventory_ingredients(id),
    planned_quantity DECIMAL(10,3),
    actual_quantity DECIMAL(10,3) NOT NULL,
    unit VARCHAR(20) NOT NULL,
    cost_per_unit DECIMAL(15,4) NOT NULL,
    total_cost DECIMAL(15,4) NOT NULL,
    batch_consumption_id BIGINT REFERENCES batch_consumptions(id),
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_pb_input_batch ON production_batch_inputs(production_batch_id);
CREATE INDEX idx_pb_input_ingredient ON production_batch_inputs(ingredient_id);

COMMENT ON TABLE production_batch_inputs IS 'Raw ingredients consumed to produce a batch — planned vs actual quantities';

-- Consumption records: tracks portions sold from a production batch
CREATE TABLE production_batch_consumptions (
    id BIGSERIAL PRIMARY KEY,
    production_batch_id BIGINT NOT NULL REFERENCES production_batches(id),
    order_id BIGINT REFERENCES orders(id),
    order_item_id BIGINT,
    quantity DECIMAL(10,3) NOT NULL,
    cost_per_unit DECIMAL(15,4) NOT NULL,
    total_cost DECIMAL(15,4) NOT NULL,
    consumed_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_pb_consumption_batch ON production_batch_consumptions(production_batch_id);
CREATE INDEX idx_pb_consumption_order ON production_batch_consumptions(order_id);
CREATE INDEX idx_pb_consumption_date ON production_batch_consumptions(consumed_at);

COMMENT ON TABLE production_batch_consumptions IS 'Tracks portions sold from production batches — links to orders for COGS';
