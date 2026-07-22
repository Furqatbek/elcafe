-- Kitchen Stations Table for routing orders to different kitchen printers
-- Example: Fryer station, Grill station, Cold station, etc.

CREATE TABLE kitchen_stations (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    printer_id BIGINT REFERENCES printer_settings(id) ON DELETE SET NULL,
    color VARCHAR(20) DEFAULT '#3B82F6',
    sort_order INTEGER DEFAULT 0,
    active BOOLEAN DEFAULT true NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Add kitchen_station_id to categories table
ALTER TABLE categories ADD COLUMN kitchen_station_id BIGINT REFERENCES kitchen_stations(id) ON DELETE SET NULL;

-- Create indexes for efficient queries
CREATE INDEX idx_kitchen_stations_restaurant ON kitchen_stations(restaurant_id);
CREATE INDEX idx_kitchen_stations_active ON kitchen_stations(restaurant_id, active);
CREATE INDEX idx_categories_kitchen_station ON categories(kitchen_station_id);

COMMENT ON TABLE kitchen_stations IS 'Kitchen stations for routing orders to different printers (e.g., Fryer, Grill, Cold)';
COMMENT ON COLUMN kitchen_stations.printer_id IS 'The printer assigned to this station for ticket printing';
COMMENT ON COLUMN kitchen_stations.color IS 'Color code for UI display';
COMMENT ON COLUMN categories.kitchen_station_id IS 'The kitchen station where items from this category should be prepared';
