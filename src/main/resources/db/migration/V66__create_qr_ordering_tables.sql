-- V66: Create QR code ordering tables for self-service
-- Phase 3.1: QR Code Menu & Ordering

-- QR codes for tables
CREATE TABLE qr_codes (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    table_id BIGINT REFERENCES tables(id) ON DELETE SET NULL,
    code VARCHAR(50) NOT NULL UNIQUE,
    short_url VARCHAR(255),
    name VARCHAR(100),
    description VARCHAR(500),
    qr_type VARCHAR(50) NOT NULL DEFAULT 'TABLE',  -- TABLE, TAKEAWAY, DELIVERY
    is_active BOOLEAN DEFAULT true,
    scan_count INTEGER DEFAULT 0,
    last_scanned_at TIMESTAMP WITH TIME ZONE,
    expires_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Self-service settings per restaurant
CREATE TABLE self_service_settings (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT NOT NULL UNIQUE REFERENCES restaurants(id) ON DELETE CASCADE,
    enabled BOOLEAN DEFAULT false,
    require_payment BOOLEAN DEFAULT false,
    allow_takeaway BOOLEAN DEFAULT true,
    allow_dine_in BOOLEAN DEFAULT true,
    minimum_order_amount DECIMAL(10,2) DEFAULT 0,
    service_charge_percent DECIMAL(5,2) DEFAULT 0,
    auto_accept_orders BOOLEAN DEFAULT false,
    estimated_prep_time_minutes INTEGER DEFAULT 15,
    show_wait_time BOOLEAN DEFAULT true,
    allow_special_instructions BOOLEAN DEFAULT true,
    max_items_per_order INTEGER DEFAULT 50,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Self-service sessions (anonymous customer sessions)
CREATE TABLE self_service_sessions (
    id BIGSERIAL PRIMARY KEY,
    session_token VARCHAR(100) NOT NULL UNIQUE,
    restaurant_id BIGINT NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    table_id BIGINT REFERENCES tables(id) ON DELETE SET NULL,
    qr_code_id BIGINT REFERENCES qr_codes(id) ON DELETE SET NULL,
    customer_id BIGINT REFERENCES customers(id) ON DELETE SET NULL,
    customer_name VARCHAR(100),
    customer_phone VARCHAR(20),
    device_info VARCHAR(500),
    ip_address VARCHAR(50),
    started_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    last_activity_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE,
    is_active BOOLEAN DEFAULT true
);

-- Self-service orders (extends regular orders with self-service metadata)
CREATE TABLE self_service_orders (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL UNIQUE REFERENCES orders(id) ON DELETE CASCADE,
    session_id BIGINT REFERENCES self_service_sessions(id) ON DELETE SET NULL,
    qr_code_id BIGINT REFERENCES qr_codes(id) ON DELETE SET NULL,
    order_type VARCHAR(50) NOT NULL DEFAULT 'DINE_IN',  -- DINE_IN, TAKEAWAY
    customer_name VARCHAR(100),
    customer_phone VARCHAR(20),
    special_instructions TEXT,
    estimated_ready_time TIMESTAMP WITH TIME ZONE,
    actual_ready_time TIMESTAMP WITH TIME ZONE,
    notified_at TIMESTAMP WITH TIME ZONE,
    picked_up_at TIMESTAMP WITH TIME ZONE,
    feedback_rating INTEGER CHECK (feedback_rating >= 1 AND feedback_rating <= 5),
    feedback_comment TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Self-service cart items (temporary storage before order creation)
CREATE TABLE self_service_cart_items (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL REFERENCES self_service_sessions(id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    variant_id BIGINT REFERENCES product_variants(id) ON DELETE SET NULL,
    quantity INTEGER NOT NULL DEFAULT 1,
    unit_price DECIMAL(10,2) NOT NULL,
    special_instructions VARCHAR(500),
    added_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Cart item modifiers (linked items like toppings, sides)
CREATE TABLE self_service_cart_modifiers (
    id BIGSERIAL PRIMARY KEY,
    cart_item_id BIGINT NOT NULL REFERENCES self_service_cart_items(id) ON DELETE CASCADE,
    linked_item_id BIGINT NOT NULL REFERENCES linked_items(id) ON DELETE CASCADE,
    quantity INTEGER NOT NULL DEFAULT 1,
    price DECIMAL(10,2) NOT NULL
);

-- Indexes for performance
CREATE INDEX idx_qr_codes_restaurant ON qr_codes(restaurant_id);
CREATE INDEX idx_qr_codes_table ON qr_codes(table_id);
CREATE INDEX idx_qr_codes_code ON qr_codes(code);
CREATE INDEX idx_qr_codes_active ON qr_codes(is_active) WHERE is_active = true;

CREATE INDEX idx_self_service_sessions_token ON self_service_sessions(session_token);
CREATE INDEX idx_self_service_sessions_restaurant ON self_service_sessions(restaurant_id);
CREATE INDEX idx_self_service_sessions_active ON self_service_sessions(is_active) WHERE is_active = true;

CREATE INDEX idx_self_service_orders_order ON self_service_orders(order_id);
CREATE INDEX idx_self_service_orders_session ON self_service_orders(session_id);

CREATE INDEX idx_cart_items_session ON self_service_cart_items(session_id);
CREATE INDEX idx_cart_modifiers_item ON self_service_cart_modifiers(cart_item_id);

-- Comments
COMMENT ON TABLE qr_codes IS 'QR codes for self-service ordering at tables or for takeaway';
COMMENT ON TABLE self_service_settings IS 'Self-service configuration per restaurant';
COMMENT ON TABLE self_service_sessions IS 'Customer sessions for self-service ordering';
COMMENT ON TABLE self_service_orders IS 'Self-service order metadata linked to main orders';
COMMENT ON TABLE self_service_cart_items IS 'Temporary cart storage before order submission';
