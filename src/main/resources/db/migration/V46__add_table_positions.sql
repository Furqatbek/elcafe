-- Add floor plan positioning columns to restaurant_tables
ALTER TABLE restaurant_tables
ADD COLUMN position_x INTEGER,
ADD COLUMN position_y INTEGER,
ADD COLUMN table_width INTEGER DEFAULT 100,
ADD COLUMN table_height INTEGER DEFAULT 100;

-- Add index for efficient floor plan queries
CREATE INDEX idx_restaurant_tables_position ON restaurant_tables (restaurant_id, position_x, position_y);
