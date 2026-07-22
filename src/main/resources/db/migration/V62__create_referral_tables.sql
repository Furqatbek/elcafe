-- Referral Program tables
-- Enables customer acquisition through referrals with rewards

-- Referral program settings per restaurant
CREATE TABLE referral_settings (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    program_active BOOLEAN DEFAULT false,
    referrer_reward_type VARCHAR(20) NOT NULL DEFAULT 'BONUS_POINTS',
    referrer_reward_amount DECIMAL(10, 2) NOT NULL DEFAULT 100,
    referee_reward_type VARCHAR(20) NOT NULL DEFAULT 'BONUS_POINTS',
    referee_reward_amount DECIMAL(10, 2) NOT NULL DEFAULT 50,
    min_order_amount DECIMAL(10, 2),
    max_referrals_per_customer INTEGER,
    reward_expires_days INTEGER DEFAULT 30,
    terms_and_conditions TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(restaurant_id),
    CONSTRAINT chk_referrer_reward_type CHECK (referrer_reward_type IN ('BONUS_POINTS', 'DISCOUNT_AMOUNT', 'DISCOUNT_PERCENT', 'FREE_ITEM')),
    CONSTRAINT chk_referee_reward_type CHECK (referee_reward_type IN ('BONUS_POINTS', 'DISCOUNT_AMOUNT', 'DISCOUNT_PERCENT', 'FREE_ITEM'))
);

-- Referral codes for each customer
CREATE TABLE referral_codes (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    customer_id BIGINT NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    code VARCHAR(20) NOT NULL,
    active BOOLEAN DEFAULT true,
    usage_count INTEGER DEFAULT 0,
    max_uses INTEGER,
    expires_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(code),
    UNIQUE(restaurant_id, customer_id)
);

-- Track individual referrals
CREATE TABLE referrals (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    referral_code_id BIGINT NOT NULL REFERENCES referral_codes(id) ON DELETE CASCADE,
    referrer_id BIGINT NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    referee_id BIGINT NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    order_id BIGINT REFERENCES orders(id) ON DELETE SET NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    referrer_reward_given BOOLEAN DEFAULT false,
    referrer_reward_amount DECIMAL(10, 2),
    referrer_reward_type VARCHAR(20),
    referee_reward_given BOOLEAN DEFAULT false,
    referee_reward_amount DECIMAL(10, 2),
    referee_reward_type VARCHAR(20),
    referrer_rewarded_at TIMESTAMP,
    referee_rewarded_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    UNIQUE(referee_id, restaurant_id),
    CONSTRAINT chk_referral_status CHECK (status IN ('PENDING', 'COMPLETED', 'EXPIRED', 'CANCELLED'))
);

-- Indexes for better query performance
CREATE INDEX idx_referral_settings_restaurant ON referral_settings(restaurant_id);
CREATE INDEX idx_referral_codes_restaurant ON referral_codes(restaurant_id);
CREATE INDEX idx_referral_codes_customer ON referral_codes(customer_id);
CREATE INDEX idx_referral_codes_code ON referral_codes(code);
CREATE INDEX idx_referrals_restaurant ON referrals(restaurant_id);
CREATE INDEX idx_referrals_referrer ON referrals(referrer_id);
CREATE INDEX idx_referrals_referee ON referrals(referee_id);
CREATE INDEX idx_referrals_status ON referrals(status);
