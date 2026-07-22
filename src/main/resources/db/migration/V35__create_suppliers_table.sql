-- Suppliers table for managing ingredient suppliers
CREATE TABLE suppliers (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(20),

    -- Contact Information
    contact_person VARCHAR(100),
    phone VARCHAR(20),
    email VARCHAR(100),
    address TEXT,

    -- Business Terms
    payment_terms VARCHAR(50),
    credit_limit DECIMAL(12,2),
    currency VARCHAR(3) DEFAULT 'UZS',

    -- Performance Metrics
    rating DECIMAL(2,1) DEFAULT 0,
    total_orders INTEGER DEFAULT 0,
    on_time_delivery_rate DECIMAL(5,2) DEFAULT 0,

    -- Status
    active BOOLEAN DEFAULT TRUE,
    notes TEXT,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_supplier_restaurant_code UNIQUE (restaurant_id, code)
);

-- Add supplier_id to inventory_ingredients table
ALTER TABLE inventory_ingredients ADD COLUMN supplier_id BIGINT REFERENCES suppliers(id) ON DELETE SET NULL;

-- Indexes
CREATE INDEX idx_suppliers_restaurant ON suppliers(restaurant_id);
CREATE INDEX idx_suppliers_active ON suppliers(active);
CREATE INDEX idx_suppliers_name ON suppliers(name);
CREATE INDEX idx_ingredients_supplier ON inventory_ingredients(supplier_id);

-- Comments
COMMENT ON TABLE suppliers IS 'Stores supplier information for inventory management';
COMMENT ON COLUMN suppliers.code IS 'Internal supplier code (e.g., SUP-001)';
COMMENT ON COLUMN suppliers.payment_terms IS 'Payment terms: COD, NET15, NET30, NET60, PREPAID';
COMMENT ON COLUMN suppliers.rating IS 'Supplier rating from 0.0 to 5.0';
COMMENT ON COLUMN suppliers.on_time_delivery_rate IS 'Percentage of on-time deliveries';
