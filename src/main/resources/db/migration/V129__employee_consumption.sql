CREATE TABLE employee_consumptions (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT NOT NULL REFERENCES restaurants(id),
    employee_shift_id BIGINT REFERENCES employee_shifts(id),
    waiter_id BIGINT REFERENCES waiters(id),
    employee_id BIGINT REFERENCES users(id),
    product_id BIGINT NOT NULL REFERENCES products(id),
    product_name VARCHAR(200) NOT NULL,
    quantity INTEGER NOT NULL DEFAULT 1,
    cost_price DECIMAL(10, 2) NOT NULL DEFAULT 0,
    total_cost DECIMAL(10, 2) NOT NULL DEFAULT 0,
    expense_id BIGINT REFERENCES financial_expenses(id),
    notes TEXT,
    consumed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_emp_consumption_restaurant ON employee_consumptions(restaurant_id);
CREATE INDEX idx_emp_consumption_shift ON employee_consumptions(employee_shift_id);
CREATE INDEX idx_emp_consumption_waiter ON employee_consumptions(waiter_id);
CREATE INDEX idx_emp_consumption_date ON employee_consumptions(consumed_at);

-- Add notification setting
ALTER TABLE owner_notification_settings ADD COLUMN IF NOT EXISTS notify_employee_consumption BOOLEAN DEFAULT true;
