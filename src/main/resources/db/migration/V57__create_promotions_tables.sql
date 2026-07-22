-- V57: Create promotions and coupon system tables
-- Part of Phase 1.1: Discount & Coupon System

-- Main promotions table
CREATE TABLE promotions (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT NOT NULL REFERENCES restaurants(id),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    promotion_type VARCHAR(50) NOT NULL, -- PERCENTAGE, FIXED_AMOUNT, FREE_ITEM, BUY_X_GET_Y
    promotion_scope VARCHAR(50) NOT NULL DEFAULT 'ALL', -- ALL, CATEGORY, PRODUCT, ORDER_TYPE
    discount_value DECIMAL(10, 2) NOT NULL DEFAULT 0, -- percentage or fixed amount
    buy_quantity INT, -- for BUY_X_GET_Y: buy X items
    get_quantity INT, -- for BUY_X_GET_Y: get Y items free/discounted
    free_product_id BIGINT REFERENCES products(id), -- for FREE_ITEM type
    start_date TIMESTAMP NOT NULL,
    end_date TIMESTAMP,
    active BOOLEAN NOT NULL DEFAULT true,
    priority INT NOT NULL DEFAULT 0, -- higher priority applied first
    stackable BOOLEAN NOT NULL DEFAULT false, -- can combine with other promotions
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Promotion rules (conditions for applying)
CREATE TABLE promotion_rules (
    id BIGSERIAL PRIMARY KEY,
    promotion_id BIGINT NOT NULL REFERENCES promotions(id) ON DELETE CASCADE,
    min_order_amount DECIMAL(10, 2), -- minimum order subtotal
    max_discount_amount DECIMAL(10, 2), -- cap on discount amount
    usage_limit INT, -- total uses allowed (null = unlimited)
    per_customer_limit INT, -- uses per customer (null = unlimited)
    min_items INT, -- minimum items in order
    applicable_order_types VARCHAR(255), -- comma-separated: DINE_IN,TAKEAWAY,DELIVERY
    applicable_days VARCHAR(50), -- comma-separated days: MON,TUE,WED,THU,FRI,SAT,SUN
    start_time TIME, -- time window start
    end_time TIME, -- time window end
    first_order_only BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Promotion-product mapping (which products/categories the promotion applies to)
CREATE TABLE promotion_products (
    id BIGSERIAL PRIMARY KEY,
    promotion_id BIGINT NOT NULL REFERENCES promotions(id) ON DELETE CASCADE,
    product_id BIGINT REFERENCES products(id) ON DELETE CASCADE,
    category_id BIGINT REFERENCES categories(id) ON DELETE CASCADE,
    include BOOLEAN NOT NULL DEFAULT true, -- true = include, false = exclude
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_product_or_category CHECK (
        (product_id IS NOT NULL AND category_id IS NULL) OR
        (product_id IS NULL AND category_id IS NOT NULL)
    )
);

-- Coupon codes table
CREATE TABLE coupon_codes (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    promotion_id BIGINT NOT NULL REFERENCES promotions(id) ON DELETE CASCADE,
    single_use BOOLEAN NOT NULL DEFAULT false,
    max_uses INT, -- null = unlimited
    used_count INT NOT NULL DEFAULT 0,
    assigned_customer_id BIGINT REFERENCES customers(id), -- for personalized coupons
    valid_from TIMESTAMP,
    valid_until TIMESTAMP,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Promotion usage tracking
CREATE TABLE promotion_usage (
    id BIGSERIAL PRIMARY KEY,
    promotion_id BIGINT NOT NULL REFERENCES promotions(id) ON DELETE CASCADE,
    coupon_code_id BIGINT REFERENCES coupon_codes(id) ON DELETE SET NULL,
    customer_id BIGINT REFERENCES customers(id) ON DELETE SET NULL,
    order_id BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    discount_amount DECIMAL(10, 2) NOT NULL,
    used_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Add promotion fields to orders table
ALTER TABLE orders ADD COLUMN IF NOT EXISTS promotion_id BIGINT REFERENCES promotions(id);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS coupon_code VARCHAR(50);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS discount_type VARCHAR(50); -- PROMOTION, COUPON, MANUAL
ALTER TABLE orders ADD COLUMN IF NOT EXISTS discount_reason VARCHAR(500);

-- Indexes for performance
CREATE INDEX idx_promotions_restaurant ON promotions(restaurant_id);
CREATE INDEX idx_promotions_active ON promotions(active, start_date, end_date);
CREATE INDEX idx_promotions_type ON promotions(promotion_type);
CREATE INDEX idx_promotion_rules_promotion ON promotion_rules(promotion_id);
CREATE INDEX idx_promotion_products_promotion ON promotion_products(promotion_id);
CREATE INDEX idx_promotion_products_product ON promotion_products(product_id);
CREATE INDEX idx_promotion_products_category ON promotion_products(category_id);
CREATE INDEX idx_coupon_codes_code ON coupon_codes(code);
CREATE INDEX idx_coupon_codes_promotion ON coupon_codes(promotion_id);
CREATE INDEX idx_coupon_codes_customer ON coupon_codes(assigned_customer_id);
CREATE INDEX idx_promotion_usage_promotion ON promotion_usage(promotion_id);
CREATE INDEX idx_promotion_usage_customer ON promotion_usage(customer_id);
CREATE INDEX idx_promotion_usage_order ON promotion_usage(order_id);
CREATE INDEX idx_orders_promotion ON orders(promotion_id);
CREATE INDEX idx_orders_coupon_code ON orders(coupon_code);
