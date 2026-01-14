-- V68: Create waiter performance and KPI configuration tables

-- KPI configuration table
CREATE TABLE waiter_kpi_configs (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    waiter_id BIGINT REFERENCES waiters(id) ON DELETE CASCADE,
    name VARCHAR(100),

    -- Daily targets
    target_orders_per_day INTEGER DEFAULT 20,
    target_revenue_per_day DECIMAL(12,2) DEFAULT 500.00,
    target_avg_ticket DECIMAL(10,2) DEFAULT 25.00,
    target_tables_per_shift INTEGER DEFAULT 10,

    -- Service quality targets
    target_avg_service_time_minutes INTEGER DEFAULT 45,
    max_complaint_rate_percent DECIMAL(5,2) DEFAULT 2.00,
    min_customer_rating DECIMAL(3,2) DEFAULT 4.00,

    -- Upselling targets
    target_upsell_rate_percent DECIMAL(5,2) DEFAULT 15.00,
    target_dessert_attach_rate_percent DECIMAL(5,2) DEFAULT 20.00,
    target_beverage_attach_rate_percent DECIMAL(5,2) DEFAULT 60.00,

    -- Bonus configuration
    bonus_threshold_percent DECIMAL(5,2) DEFAULT 100.00,
    bonus_amount_per_threshold DECIMAL(10,2) DEFAULT 50.00,

    active BOOLEAN DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Waiter daily performance table
CREATE TABLE waiter_performance (
    id BIGSERIAL PRIMARY KEY,
    waiter_id BIGINT NOT NULL REFERENCES waiters(id) ON DELETE CASCADE,
    restaurant_id BIGINT NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    performance_date DATE NOT NULL,

    -- Order metrics
    total_orders INTEGER DEFAULT 0,
    total_tables_served INTEGER DEFAULT 0,
    total_customers_served INTEGER DEFAULT 0,

    -- Revenue metrics
    total_revenue DECIMAL(12,2) DEFAULT 0.00,
    total_tips DECIMAL(10,2) DEFAULT 0.00,
    avg_ticket_value DECIMAL(10,2) DEFAULT 0.00,

    -- Service time metrics (in minutes)
    avg_service_time_minutes INTEGER DEFAULT 0,
    min_service_time_minutes INTEGER,
    max_service_time_minutes INTEGER,

    -- Quality metrics
    complaints_count INTEGER DEFAULT 0,
    compliments_count INTEGER DEFAULT 0,
    avg_customer_rating DECIMAL(3,2),
    ratings_count INTEGER DEFAULT 0,

    -- Upselling metrics
    upsell_attempts INTEGER DEFAULT 0,
    upsell_successes INTEGER DEFAULT 0,
    dessert_orders INTEGER DEFAULT 0,
    beverage_orders INTEGER DEFAULT 0,

    -- Void/discount metrics
    void_items_count INTEGER DEFAULT 0,
    void_items_value DECIMAL(10,2) DEFAULT 0.00,
    discounts_given INTEGER DEFAULT 0,
    discounts_value DECIMAL(10,2) DEFAULT 0.00,

    -- Shift info
    shift_start TIMESTAMP WITH TIME ZONE,
    shift_end TIMESTAMP WITH TIME ZONE,
    hours_worked DECIMAL(4,2),

    -- KPI achievement
    kpi_score DECIMAL(5,2),
    bonus_earned DECIMAL(10,2) DEFAULT 0.00,

    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT unique_waiter_performance_date UNIQUE (waiter_id, performance_date)
);

-- Indexes for performance queries
CREATE INDEX idx_waiter_kpi_configs_restaurant ON waiter_kpi_configs(restaurant_id);
CREATE INDEX idx_waiter_kpi_configs_waiter ON waiter_kpi_configs(waiter_id);
CREATE INDEX idx_waiter_kpi_configs_active ON waiter_kpi_configs(active) WHERE active = true;

CREATE INDEX idx_waiter_performance_waiter ON waiter_performance(waiter_id);
CREATE INDEX idx_waiter_performance_restaurant ON waiter_performance(restaurant_id);
CREATE INDEX idx_waiter_performance_date ON waiter_performance(performance_date);
CREATE INDEX idx_waiter_performance_waiter_date ON waiter_performance(waiter_id, performance_date DESC);

-- Comments
COMMENT ON TABLE waiter_kpi_configs IS 'KPI configuration and targets for waiter performance evaluation';
COMMENT ON TABLE waiter_performance IS 'Daily performance metrics for each waiter';
COMMENT ON COLUMN waiter_kpi_configs.waiter_id IS 'If null, applies as default for all waiters in the restaurant';
COMMENT ON COLUMN waiter_performance.kpi_score IS 'Percentage of KPI achievement (0-100+)';
