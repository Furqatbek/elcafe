-- Customer Tiers (VIP levels)
CREATE TABLE customer_tiers (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    level INTEGER NOT NULL UNIQUE,
    min_total_spend DECIMAL(10, 2) DEFAULT 0,
    min_order_count INTEGER DEFAULT 0,
    bonus_multiplier DECIMAL(3, 2) DEFAULT 1.0,
    benefits_description TEXT,
    color VARCHAR(20),
    icon VARCHAR(50),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Loyalty Configuration (per restaurant or global)
CREATE TABLE loyalty_config (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT REFERENCES restaurants(id) ON DELETE CASCADE,
    bonus_rate_type VARCHAR(20) NOT NULL DEFAULT 'PERCENTAGE',
    bonus_rate_value DECIMAL(10, 2) NOT NULL DEFAULT 5.0,
    max_bonus_payment_percentage INTEGER NOT NULL DEFAULT 50,
    min_order_amount_for_bonus DECIMAL(10, 2) DEFAULT 0,
    birthday_bonus_amount DECIMAL(10, 2) DEFAULT 0,
    first_order_bonus_amount DECIMAL(10, 2) DEFAULT 0,
    reactivation_bonus_amount DECIMAL(10, 2) DEFAULT 0,
    reactivation_days_threshold INTEGER DEFAULT 30,
    bonus_expiry_days INTEGER,
    enabled BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT check_bonus_rate_type CHECK (bonus_rate_type IN ('PERCENTAGE', 'FIXED_AMOUNT')),
    CONSTRAINT check_max_bonus_payment CHECK (max_bonus_payment_percentage >= 0 AND max_bonus_payment_percentage <= 100)
);

-- Customer Loyalty (balance and tier per customer)
CREATE TABLE customer_loyalty (
    id BIGSERIAL PRIMARY KEY,
    customer_id BIGINT NOT NULL UNIQUE REFERENCES customers(id) ON DELETE CASCADE,
    current_balance DECIMAL(10, 2) NOT NULL DEFAULT 0,
    lifetime_earned DECIMAL(10, 2) NOT NULL DEFAULT 0,
    lifetime_spent DECIMAL(10, 2) NOT NULL DEFAULT 0,
    tier_id BIGINT REFERENCES customer_tiers(id) ON DELETE SET NULL,
    total_spent DECIMAL(10, 2) NOT NULL DEFAULT 0,
    order_count INTEGER NOT NULL DEFAULT 0,
    last_order_date TIMESTAMP,
    birthday_bonus_claimed_year INTEGER,
    first_order_bonus_claimed BOOLEAN DEFAULT false,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT check_balance CHECK (current_balance >= 0)
);

-- Bonus Transactions (ledger for all bonus movements)
CREATE TABLE bonus_transactions (
    id BIGSERIAL PRIMARY KEY,
    customer_loyalty_id BIGINT NOT NULL REFERENCES customer_loyalty(id) ON DELETE CASCADE,
    transaction_type VARCHAR(30) NOT NULL,
    amount DECIMAL(10, 2) NOT NULL,
    balance_after DECIMAL(10, 2) NOT NULL,
    order_id BIGINT REFERENCES orders(id) ON DELETE SET NULL,
    description TEXT,
    idempotency_key VARCHAR(255) UNIQUE,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT check_transaction_type CHECK (transaction_type IN (
        'EARNED', 'SPENT', 'REFUNDED', 'EXPIRED', 'ADJUSTMENT',
        'BIRTHDAY_BONUS', 'FIRST_ORDER_BONUS', 'REACTIVATION_BONUS',
        'PROMOTION_BONUS', 'ADMIN_ADJUSTMENT'
    ))
);

-- Tier History (track tier changes)
CREATE TABLE tier_history (
    id BIGSERIAL PRIMARY KEY,
    customer_loyalty_id BIGINT NOT NULL REFERENCES customer_loyalty(id) ON DELETE CASCADE,
    from_tier_id BIGINT REFERENCES customer_tiers(id) ON DELETE SET NULL,
    to_tier_id BIGINT NOT NULL REFERENCES customer_tiers(id) ON DELETE CASCADE,
    reason TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Loyalty Promotions (time-limited bonus campaigns)
CREATE TABLE loyalty_promotions (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT REFERENCES restaurants(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    promotion_type VARCHAR(30) NOT NULL,
    multiplier_value DECIMAL(3, 2),
    fixed_bonus_amount DECIMAL(10, 2),
    start_date TIMESTAMP NOT NULL,
    end_date TIMESTAMP,
    days_of_week VARCHAR(255),
    min_order_amount DECIMAL(10, 2),
    max_bonus_per_order DECIMAL(10, 2),
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT check_promotion_type CHECK (promotion_type IN ('BONUS_MULTIPLIER', 'FIXED_BONUS', 'PERCENTAGE_BOOST'))
);

-- Create indexes for performance
CREATE INDEX idx_customer_loyalty_customer_id ON customer_loyalty(customer_id);
CREATE INDEX idx_customer_loyalty_tier_id ON customer_loyalty(tier_id);
CREATE INDEX idx_bonus_transactions_customer_loyalty_id ON bonus_transactions(customer_loyalty_id);
CREATE INDEX idx_bonus_transactions_order_id ON bonus_transactions(order_id);
CREATE INDEX idx_bonus_transactions_created_at ON bonus_transactions(created_at);
CREATE INDEX idx_bonus_transactions_idempotency_key ON bonus_transactions(idempotency_key);
CREATE INDEX idx_tier_history_customer_loyalty_id ON tier_history(customer_loyalty_id);
CREATE INDEX idx_loyalty_promotions_dates ON loyalty_promotions(start_date, end_date) WHERE active = true;
CREATE INDEX idx_loyalty_config_restaurant_id ON loyalty_config(restaurant_id);

-- Insert default tiers
INSERT INTO customer_tiers (name, level, min_total_spend, min_order_count, bonus_multiplier, benefits_description, color, icon) VALUES
('New', 1, 0, 0, 1.0, 'Welcome bonus on first order', '#94A3B8', 'star'),
('Regular', 2, 1000, 5, 1.2, '20% bonus multiplier, priority support', '#3B82F6', 'award'),
('Gold', 3, 5000, 20, 1.5, '50% bonus multiplier, free delivery', '#F59E0B', 'crown'),
('VIP', 4, 15000, 50, 2.0, '2x bonus multiplier, exclusive offers, personal manager', '#8B5CF6', 'gem');

-- Insert default global loyalty configuration
INSERT INTO loyalty_config (restaurant_id, bonus_rate_type, bonus_rate_value, max_bonus_payment_percentage,
    birthday_bonus_amount, first_order_bonus_amount, reactivation_bonus_amount, reactivation_days_threshold, enabled)
VALUES (NULL, 'PERCENTAGE', 5.0, 50, 500, 300, 200, 30, true);

-- Add trigger to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_loyalty_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER loyalty_config_updated_at BEFORE UPDATE ON loyalty_config
    FOR EACH ROW EXECUTE FUNCTION update_loyalty_updated_at();

CREATE TRIGGER customer_loyalty_updated_at BEFORE UPDATE ON customer_loyalty
    FOR EACH ROW EXECUTE FUNCTION update_loyalty_updated_at();

CREATE TRIGGER loyalty_promotions_updated_at BEFORE UPDATE ON loyalty_promotions
    FOR EACH ROW EXECUTE FUNCTION update_loyalty_updated_at();

CREATE TRIGGER customer_tiers_updated_at BEFORE UPDATE ON customer_tiers
    FOR EACH ROW EXECUTE FUNCTION update_loyalty_updated_at();
