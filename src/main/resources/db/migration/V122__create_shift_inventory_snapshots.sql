CREATE TABLE shift_inventory_snapshots (
    id BIGSERIAL PRIMARY KEY,
    shift_id BIGINT NOT NULL REFERENCES employee_shifts(id),
    ingredient_id BIGINT NOT NULL REFERENCES inventory_ingredients(id),
    snapshot_type VARCHAR(10) NOT NULL,  -- START or END
    quantity DECIMAL(10,3) NOT NULL,
    expected_quantity DECIMAL(10,3),     -- calculated at END based on orders
    variance DECIMAL(10,3),             -- actual - expected
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_shift_inv_snapshot_shift ON shift_inventory_snapshots(shift_id, snapshot_type);
CREATE INDEX idx_shift_inv_snapshot_ingredient ON shift_inventory_snapshots(ingredient_id);

COMMENT ON TABLE shift_inventory_snapshots IS 'Records inventory levels at shift start/end for accountability';
