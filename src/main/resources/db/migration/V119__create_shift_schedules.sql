CREATE TABLE shift_schedules (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT NOT NULL REFERENCES restaurants(id),
    employee_id BIGINT NOT NULL REFERENCES users(id),
    shift_date DATE NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    role VARCHAR(50),
    notes TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    created_by BIGINT REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_shift_schedule_restaurant ON shift_schedules(restaurant_id, shift_date);
CREATE INDEX idx_shift_schedule_employee ON shift_schedules(employee_id, shift_date);
CREATE UNIQUE INDEX idx_shift_schedule_unique ON shift_schedules(employee_id, shift_date, start_time);

COMMENT ON TABLE shift_schedules IS 'Planned shift assignments for specific dates';
COMMENT ON COLUMN shift_schedules.status IS 'SCHEDULED, CONFIRMED, CANCELLED';
