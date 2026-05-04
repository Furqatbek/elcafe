CREATE TABLE shift_rules (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT NOT NULL REFERENCES restaurants(id),
    max_shift_hours INTEGER NOT NULL DEFAULT 8,
    max_weekly_hours INTEGER NOT NULL DEFAULT 40,
    overtime_multiplier DECIMAL(3,2) NOT NULL DEFAULT 1.50,
    min_break_after_hours INTEGER NOT NULL DEFAULT 4,
    min_break_duration_minutes INTEGER NOT NULL DEFAULT 30,
    notify_overtime_at_hours INTEGER NOT NULL DEFAULT 7,
    auto_clock_out_after_hours INTEGER NOT NULL DEFAULT 12,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_shift_rules_restaurant ON shift_rules(restaurant_id);

COMMENT ON TABLE shift_rules IS 'Per-restaurant overtime and labor rules configuration';
