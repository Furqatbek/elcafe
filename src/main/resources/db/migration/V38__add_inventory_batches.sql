-- Add expiry tracking fields to ingredients
ALTER TABLE inventory_ingredients ADD COLUMN track_expiry BOOLEAN DEFAULT FALSE;
ALTER TABLE inventory_ingredients ADD COLUMN default_shelf_life_days INTEGER;
ALTER TABLE inventory_ingredients ADD COLUMN expiry_alert_days INTEGER DEFAULT 7;

-- Create inventory_batches table for batch/lot tracking with expiry dates
CREATE TABLE inventory_batches (
    id BIGSERIAL PRIMARY KEY,
    ingredient_id BIGINT NOT NULL REFERENCES inventory_ingredients(id) ON DELETE CASCADE,
    batch_number VARCHAR(100) NOT NULL,
    quantity DECIMAL(10,3) NOT NULL DEFAULT 0,
    initial_quantity DECIMAL(10,3) NOT NULL DEFAULT 0,
    received_date DATE NOT NULL,
    expiry_date DATE,
    cost_per_unit DECIMAL(10,2),
    supplier_id BIGINT REFERENCES suppliers(id),
    po_reference VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_batch_status CHECK (status IN ('ACTIVE', 'EXPIRED', 'DEPLETED', 'WRITTEN_OFF'))
);

-- Create indexes for efficient queries
CREATE INDEX idx_batch_ingredient ON inventory_batches(ingredient_id);
CREATE INDEX idx_batch_expiry ON inventory_batches(expiry_date);
CREATE INDEX idx_batch_status ON inventory_batches(status);
CREATE INDEX idx_batch_ingredient_status ON inventory_batches(ingredient_id, status);
CREATE INDEX idx_batch_expiry_status ON inventory_batches(expiry_date, status) WHERE status = 'ACTIVE';

-- Add unique constraint for batch number per ingredient
CREATE UNIQUE INDEX idx_batch_unique ON inventory_batches(ingredient_id, batch_number);

COMMENT ON TABLE inventory_batches IS 'Tracks inventory batches/lots with expiry dates for FEFO management';
COMMENT ON COLUMN inventory_batches.batch_number IS 'Unique batch/lot number, can be from PO or auto-generated';
COMMENT ON COLUMN inventory_batches.quantity IS 'Current remaining quantity in this batch';
COMMENT ON COLUMN inventory_batches.initial_quantity IS 'Original quantity when batch was received';
COMMENT ON COLUMN inventory_batches.status IS 'ACTIVE=in use, EXPIRED=past expiry, DEPLETED=quantity is 0, WRITTEN_OFF=manually removed';
