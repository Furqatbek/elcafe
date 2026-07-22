-- Bundle Deals / Combos tables
-- Allows creating meal deals with combined pricing

-- Main bundles table
CREATE TABLE bundles (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    image_url VARCHAR(500),
    bundle_price DECIMAL(10, 2) NOT NULL,
    original_price DECIMAL(10, 2),
    savings_amount DECIMAL(10, 2),
    savings_percent DECIMAL(5, 2),
    active BOOLEAN DEFAULT true,
    available_from TIME,
    available_until TIME,
    available_days VARCHAR(50),
    max_per_order INTEGER,
    display_order INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Bundle items - products included in the bundle
CREATE TABLE bundle_items (
    id BIGSERIAL PRIMARY KEY,
    bundle_id BIGINT NOT NULL REFERENCES bundles(id) ON DELETE CASCADE,
    product_id BIGINT REFERENCES products(id) ON DELETE SET NULL,
    quantity INTEGER NOT NULL DEFAULT 1,
    is_required BOOLEAN DEFAULT true,
    is_default BOOLEAN DEFAULT true,
    display_order INTEGER DEFAULT 0,
    UNIQUE(bundle_id, product_id)
);

-- Bundle option groups - for "choose one" scenarios
CREATE TABLE bundle_option_groups (
    id BIGSERIAL PRIMARY KEY,
    bundle_id BIGINT NOT NULL REFERENCES bundles(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    min_selections INTEGER DEFAULT 1,
    max_selections INTEGER DEFAULT 1,
    is_required BOOLEAN DEFAULT true,
    display_order INTEGER DEFAULT 0
);

-- Bundle options - products available in each option group
CREATE TABLE bundle_options (
    id BIGSERIAL PRIMARY KEY,
    option_group_id BIGINT NOT NULL REFERENCES bundle_option_groups(id) ON DELETE CASCADE,
    product_id BIGINT REFERENCES products(id) ON DELETE SET NULL,
    price_adjustment DECIMAL(10, 2) DEFAULT 0,
    is_default BOOLEAN DEFAULT false,
    display_order INTEGER DEFAULT 0
);

-- Indexes for better query performance
CREATE INDEX idx_bundles_restaurant_id ON bundles(restaurant_id);
CREATE INDEX idx_bundles_active ON bundles(active);
CREATE INDEX idx_bundle_items_bundle_id ON bundle_items(bundle_id);
CREATE INDEX idx_bundle_option_groups_bundle_id ON bundle_option_groups(bundle_id);
CREATE INDEX idx_bundle_options_group_id ON bundle_options(option_group_id);
