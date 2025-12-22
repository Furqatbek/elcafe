-- Create price change logs table for pricing audit
CREATE TABLE price_change_logs (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    previous_price DECIMAL(10, 2),
    new_price DECIMAL(10, 2) NOT NULL,
    price_change DECIMAL(10, 2),
    price_change_percentage DECIMAL(5, 2),
    previous_cost_price DECIMAL(10, 2),
    new_cost_price DECIMAL(10, 2),
    previous_margin_percentage DECIMAL(5, 2),
    new_margin_percentage DECIMAL(5, 2),
    pricing_strategy VARCHAR(50),
    change_reason VARCHAR(500),
    changed_by VARCHAR(200),
    is_system_generated BOOLEAN DEFAULT FALSE,
    recommendation_accepted BOOLEAN,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_price_change_restaurant ON price_change_logs(restaurant_id);
CREATE INDEX idx_price_change_product ON price_change_logs(product_id);
CREATE INDEX idx_price_change_created ON price_change_logs(created_at);
