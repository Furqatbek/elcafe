-- Create order_tables join table for proper many-to-many relationship between orders and restaurant_tables
-- This replaces the anti-pattern of storing comma-separated table IDs in orders.table_ids

CREATE TABLE order_tables (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL,
    table_id BIGINT NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_order_tables_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
    CONSTRAINT fk_order_tables_table FOREIGN KEY (table_id) REFERENCES restaurant_tables(id) ON DELETE CASCADE,
    CONSTRAINT uk_order_tables_order_table UNIQUE (order_id, table_id)
);

-- Create indexes for efficient lookups
CREATE INDEX idx_order_tables_order_id ON order_tables(order_id);
CREATE INDEX idx_order_tables_table_id ON order_tables(table_id);

-- Migrate existing data from orders.table_ids (comma-separated) to the new join table
-- This handles both single table (dining_table_id) and multi-table (table_ids) scenarios

-- First, migrate data from table_ids column (comma-separated format)
-- Using a DO block with PL/pgSQL for procedural migration
DO $$
DECLARE
    order_rec RECORD;
    table_id_str TEXT;
    table_id_val BIGINT;
    is_first BOOLEAN;
BEGIN
    -- Process orders that have table_ids set
    FOR order_rec IN
        SELECT id, table_ids, dining_table_id
        FROM orders
        WHERE table_ids IS NOT NULL AND table_ids <> ''
    LOOP
        is_first := TRUE;
        -- Split and process each table ID
        FOR table_id_str IN SELECT unnest(string_to_array(order_rec.table_ids, ','))
        LOOP
            -- Trim whitespace and convert to BIGINT
            table_id_val := TRIM(table_id_str)::BIGINT;

            -- Check if the table exists before inserting
            IF EXISTS (SELECT 1 FROM restaurant_tables WHERE id = table_id_val) THEN
                -- Insert into join table, first one or matching dining_table_id is primary
                INSERT INTO order_tables (order_id, table_id, is_primary)
                VALUES (
                    order_rec.id,
                    table_id_val,
                    is_first OR (order_rec.dining_table_id IS NOT NULL AND table_id_val = order_rec.dining_table_id)
                )
                ON CONFLICT (order_id, table_id) DO NOTHING;

                is_first := FALSE;
            END IF;
        END LOOP;
    END LOOP;

    -- Process orders that only have dining_table_id set (no table_ids)
    FOR order_rec IN
        SELECT id, dining_table_id
        FROM orders
        WHERE dining_table_id IS NOT NULL
        AND (table_ids IS NULL OR table_ids = '')
        AND NOT EXISTS (SELECT 1 FROM order_tables WHERE order_id = orders.id)
    LOOP
        -- Check if the table exists before inserting
        IF EXISTS (SELECT 1 FROM restaurant_tables WHERE id = order_rec.dining_table_id) THEN
            INSERT INTO order_tables (order_id, table_id, is_primary)
            VALUES (order_rec.id, order_rec.dining_table_id, TRUE)
            ON CONFLICT (order_id, table_id) DO NOTHING;
        END IF;
    END LOOP;
END $$;

-- Add comment to document the deprecation of table_ids column
COMMENT ON COLUMN orders.table_ids IS 'DEPRECATED: Use order_tables join table instead. This column will be removed in a future migration.';
