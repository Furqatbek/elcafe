-- Create printer_settings table for configurable printer settings
CREATE TABLE printer_settings (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    printer_type VARCHAR(20) NOT NULL CHECK (printer_type IN ('KITCHEN', 'CUSTOMER', 'LABEL', 'REPORT')),
    printer_name VARCHAR(200) NOT NULL,
    ip_address VARCHAR(100),
    port INTEGER,
    connection_type VARCHAR(20),
    paper_width INTEGER NOT NULL DEFAULT 80,
    font_size INTEGER NOT NULL DEFAULT 12,
    auto_print BOOLEAN NOT NULL DEFAULT false,
    enabled BOOLEAN NOT NULL DEFAULT true,
    notes VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes
CREATE INDEX idx_printer_restaurant ON printer_settings(restaurant_id);
CREATE INDEX idx_printer_type ON printer_settings(printer_type);

-- Insert default printer settings for existing restaurants
INSERT INTO printer_settings (restaurant_id, printer_type, printer_name, connection_type, paper_width, auto_print, enabled)
SELECT
    id,
    'KITCHEN',
    'Kitchen Printer',
    'USB',
    80,
    false,
    false
FROM restaurants;

-- Add comment
COMMENT ON TABLE printer_settings IS 'Thermal printer configuration settings for restaurants';
