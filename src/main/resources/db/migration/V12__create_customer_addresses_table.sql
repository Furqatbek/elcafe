-- Create customer_addresses table
CREATE TABLE customer_addresses (
    id BIGSERIAL PRIMARY KEY,
    customer_id BIGINT NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    label VARCHAR(200),
    is_default BOOLEAN NOT NULL DEFAULT FALSE,

    -- OpenStreetMap/Nominatim fields
    place_id BIGINT,
    osm_type VARCHAR(20),
    osm_id BIGINT,
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    address_class VARCHAR(100),
    type VARCHAR(100),
    display_name TEXT,

    -- Detailed address components
    road VARCHAR(200),
    neighbourhood VARCHAR(200),
    county VARCHAR(200),
    city VARCHAR(200),
    state VARCHAR(200),
    postcode VARCHAR(20),
    country VARCHAR(100),
    country_code VARCHAR(10),

    -- Bounding box
    bounding_box_min_lat DOUBLE PRECISION,
    bounding_box_max_lat DOUBLE PRECISION,
    bounding_box_min_lon DOUBLE PRECISION,
    bounding_box_max_lon DOUBLE PRECISION,

    -- Additional info
    delivery_instructions TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for faster queries
CREATE INDEX idx_customer_addresses_customer_id ON customer_addresses(customer_id);
CREATE INDEX idx_customer_addresses_customer_id_active ON customer_addresses(customer_id, active);
CREATE INDEX idx_customer_addresses_customer_id_default ON customer_addresses(customer_id, is_default);
CREATE INDEX idx_customer_addresses_latitude_longitude ON customer_addresses(latitude, longitude);

-- Add constraint to ensure only one default address per customer
CREATE UNIQUE INDEX idx_customer_addresses_unique_default
ON customer_addresses(customer_id)
WHERE is_default = TRUE AND active = TRUE;
