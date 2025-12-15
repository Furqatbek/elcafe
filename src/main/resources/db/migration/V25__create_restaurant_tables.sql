-- Create restaurant_tables table for table management
CREATE TABLE restaurant_tables (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    table_number VARCHAR(50) NOT NULL,
    table_name VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    capacity INTEGER NOT NULL,
    section VARCHAR(100),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    notes VARCHAR(500),
    qr_code VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_table_number_per_restaurant UNIQUE (restaurant_id, table_number),
    CONSTRAINT check_valid_table_status CHECK (status IN ('AVAILABLE', 'OCCUPIED', 'RESERVED', 'CLEANING', 'OUT_OF_SERVICE')),
    CONSTRAINT check_valid_capacity CHECK (capacity >= 1)
);

-- Create indexes for common queries
CREATE INDEX idx_restaurant_tables_restaurant ON restaurant_tables(restaurant_id);
CREATE INDEX idx_restaurant_tables_status ON restaurant_tables(status);
CREATE INDEX idx_restaurant_tables_section ON restaurant_tables(section);
CREATE INDEX idx_restaurant_tables_restaurant_status ON restaurant_tables(restaurant_id, status);
CREATE INDEX idx_restaurant_tables_restaurant_section ON restaurant_tables(restaurant_id, section);
CREATE INDEX idx_restaurant_tables_active ON restaurant_tables(active);

-- Add comments
COMMENT ON TABLE restaurant_tables IS 'Restaurant table management for dine-in orders';
COMMENT ON COLUMN restaurant_tables.table_number IS 'Unique table number within the restaurant (e.g., "T1", "A5")';
COMMENT ON COLUMN restaurant_tables.table_name IS 'Optional friendly name for the table';
COMMENT ON COLUMN restaurant_tables.status IS 'Current status: AVAILABLE, OCCUPIED, RESERVED, CLEANING, OUT_OF_SERVICE';
COMMENT ON COLUMN restaurant_tables.capacity IS 'Maximum number of guests the table can accommodate';
COMMENT ON COLUMN restaurant_tables.section IS 'Section or area of the restaurant (e.g., "Main Hall", "Patio", "VIP")';
COMMENT ON COLUMN restaurant_tables.qr_code IS 'QR code for table ordering (optional)';
