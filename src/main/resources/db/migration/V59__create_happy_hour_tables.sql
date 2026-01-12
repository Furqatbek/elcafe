-- Happy Hour / Time-Based Pricing Tables

-- Main happy hour definition
CREATE TABLE happy_hours (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    discount_percent DECIMAL(5, 2) NOT NULL,
    active BOOLEAN DEFAULT true,
    priority INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Happy hour schedules (when the happy hour is active)
CREATE TABLE happy_hour_schedules (
    id BIGSERIAL PRIMARY KEY,
    happy_hour_id BIGINT NOT NULL REFERENCES happy_hours(id) ON DELETE CASCADE,
    day_of_week VARCHAR(10) NOT NULL, -- MON, TUE, WED, THU, FRI, SAT, SUN
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    CONSTRAINT chk_day_of_week CHECK (day_of_week IN ('MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN'))
);

-- Happy hour product/category targeting (optional - if empty, applies to all)
CREATE TABLE happy_hour_products (
    id BIGSERIAL PRIMARY KEY,
    happy_hour_id BIGINT NOT NULL REFERENCES happy_hours(id) ON DELETE CASCADE,
    product_id BIGINT REFERENCES products(id) ON DELETE CASCADE,
    category_id BIGINT REFERENCES categories(id) ON DELETE CASCADE,
    CONSTRAINT chk_product_or_category CHECK (
        (product_id IS NOT NULL AND category_id IS NULL) OR
        (product_id IS NULL AND category_id IS NOT NULL) OR
        (product_id IS NULL AND category_id IS NULL)
    )
);

-- Indexes for performance
CREATE INDEX idx_happy_hours_restaurant ON happy_hours(restaurant_id);
CREATE INDEX idx_happy_hours_active ON happy_hours(restaurant_id, active);
CREATE INDEX idx_happy_hour_schedules_day ON happy_hour_schedules(happy_hour_id, day_of_week);
CREATE INDEX idx_happy_hour_products_happy_hour ON happy_hour_products(happy_hour_id);
CREATE INDEX idx_happy_hour_products_product ON happy_hour_products(product_id);
CREATE INDEX idx_happy_hour_products_category ON happy_hour_products(category_id);
