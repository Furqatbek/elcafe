-- Add support for table merging
-- When tables are merged, one table becomes the "main" table and others reference it

-- Add merged_table_id column to track which table this is merged with
ALTER TABLE restaurant_tables ADD COLUMN merged_table_id BIGINT;

-- Add foreign key constraint
ALTER TABLE restaurant_tables
    ADD CONSTRAINT fk_merged_table
    FOREIGN KEY (merged_table_id)
    REFERENCES restaurant_tables(id)
    ON DELETE SET NULL;

-- Add index for better query performance
CREATE INDEX idx_restaurant_tables_merged_table_id ON restaurant_tables(merged_table_id);

-- Add original_capacity column to store the original capacity before merging
ALTER TABLE restaurant_tables ADD COLUMN original_capacity INTEGER;

-- Set original_capacity to current capacity for existing tables
UPDATE restaurant_tables SET original_capacity = capacity WHERE original_capacity IS NULL;
