ALTER TABLE orders ADD COLUMN shift_id BIGINT REFERENCES employee_shifts(id);

CREATE INDEX idx_orders_shift ON orders(shift_id);

COMMENT ON COLUMN orders.shift_id IS 'Links order to the active employee shift that created it';
