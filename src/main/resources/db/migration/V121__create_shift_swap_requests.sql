CREATE TABLE shift_swap_requests (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT NOT NULL REFERENCES restaurants(id),
    requesting_employee_id BIGINT NOT NULL REFERENCES users(id),
    target_employee_id BIGINT REFERENCES users(id),
    schedule_id BIGINT REFERENCES shift_schedules(id),
    shift_date DATE NOT NULL,
    reason TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    approved_by BIGINT REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_swap_requests_restaurant ON shift_swap_requests(restaurant_id, status);
CREATE INDEX idx_swap_requests_employee ON shift_swap_requests(requesting_employee_id);

COMMENT ON TABLE shift_swap_requests IS 'Employee shift swap/coverage requests';
COMMENT ON COLUMN shift_swap_requests.status IS 'PENDING, ACCEPTED, REJECTED, APPROVED';
