-- Loyalty Milestones (configurable stamp-card style rewards)
-- e.g., "Visit 10 times, get 1 free item"
CREATE TABLE loyalty_milestones (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT REFERENCES restaurants(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    required_visits INTEGER NOT NULL,
    reward_type VARCHAR(30) NOT NULL,
    reward_value DECIMAL(10, 2),
    reward_product_id BIGINT REFERENCES products(id) ON DELETE SET NULL,
    is_repeating BOOLEAN NOT NULL DEFAULT true,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT check_milestone_reward_type CHECK (reward_type IN ('FREE_ITEM', 'DISCOUNT_PERCENTAGE', 'DISCOUNT_FIXED', 'BONUS_POINTS')),
    CONSTRAINT check_required_visits CHECK (required_visits > 0)
);

-- Milestone Redemptions (tracks each customer's progress and redemptions)
CREATE TABLE milestone_redemptions (
    id BIGSERIAL PRIMARY KEY,
    milestone_id BIGINT NOT NULL REFERENCES loyalty_milestones(id) ON DELETE CASCADE,
    customer_id BIGINT NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    current_visits INTEGER NOT NULL DEFAULT 0,
    total_completions INTEGER NOT NULL DEFAULT 0,
    reward_pending BOOLEAN NOT NULL DEFAULT false,
    last_visit_order_id BIGINT REFERENCES orders(id) ON DELETE SET NULL,
    last_completion_at TIMESTAMP,
    last_redemption_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_milestone_customer UNIQUE (milestone_id, customer_id),
    CONSTRAINT check_current_visits CHECK (current_visits >= 0)
);

-- Create indexes for performance
CREATE INDEX idx_loyalty_milestones_restaurant_id ON loyalty_milestones(restaurant_id) WHERE active = true;
CREATE INDEX idx_milestone_redemptions_milestone_id ON milestone_redemptions(milestone_id);
CREATE INDEX idx_milestone_redemptions_customer_id ON milestone_redemptions(customer_id);
CREATE INDEX idx_milestone_redemptions_reward_pending ON milestone_redemptions(reward_pending) WHERE reward_pending = true;

-- Add triggers for updated_at
CREATE TRIGGER loyalty_milestones_updated_at BEFORE UPDATE ON loyalty_milestones
    FOR EACH ROW EXECUTE FUNCTION update_loyalty_updated_at();

CREATE TRIGGER milestone_redemptions_updated_at BEFORE UPDATE ON milestone_redemptions
    FOR EACH ROW EXECUTE FUNCTION update_loyalty_updated_at();
