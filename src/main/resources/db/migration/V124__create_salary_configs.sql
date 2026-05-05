CREATE TABLE salary_configs (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT NOT NULL REFERENCES restaurants(id),
    employee_id BIGINT NOT NULL REFERENCES users(id),
    monthly_salary DECIMAL(12, 2) NOT NULL,
    pay_day INTEGER NOT NULL CHECK (pay_day BETWEEN 1 AND 28),
    payment_method VARCHAR(20) DEFAULT 'CASH',
    auto_approve BOOLEAN DEFAULT TRUE,
    active BOOLEAN DEFAULT TRUE,
    last_paid_date DATE,
    notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE (restaurant_id, employee_id)
);

CREATE INDEX idx_salary_configs_restaurant ON salary_configs(restaurant_id);
CREATE INDEX idx_salary_configs_active_payday ON salary_configs(active, pay_day);
