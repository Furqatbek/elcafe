-- V67: Fix foreign key references in QR ordering tables
-- The V66 migration incorrectly referenced 'tables' instead of 'restaurant_tables'

-- Fix qr_codes table foreign key
ALTER TABLE qr_codes DROP CONSTRAINT IF EXISTS qr_codes_table_id_fkey;
ALTER TABLE qr_codes
    ADD CONSTRAINT qr_codes_table_id_fkey
    FOREIGN KEY (table_id) REFERENCES restaurant_tables(id) ON DELETE SET NULL;

-- Fix self_service_sessions table foreign key
ALTER TABLE self_service_sessions DROP CONSTRAINT IF EXISTS self_service_sessions_table_id_fkey;
ALTER TABLE self_service_sessions
    ADD CONSTRAINT self_service_sessions_table_id_fkey
    FOREIGN KEY (table_id) REFERENCES restaurant_tables(id) ON DELETE SET NULL;
