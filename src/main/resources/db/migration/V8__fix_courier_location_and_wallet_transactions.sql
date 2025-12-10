-- Migration: Fix courier location and wallet transactions (cleanup and recreate)
-- Version: V8
-- Description: Fixes the V7 migration by dropping partial objects and recreating with correct index names

-- Drop tables if they exist (from failed V7 migration)
DROP TABLE IF EXISTS courier_wallet_transactions CASCADE;
DROP TABLE IF EXISTS courier_locations CASCADE;

-- Drop any indexes that might have been created
DROP INDEX IF EXISTS idx_courier_id;
DROP INDEX IF EXISTS idx_order_id;
DROP INDEX IF EXISTS idx_timestamp;
DROP INDEX IF EXISTS idx_wallet_id;
DROP INDEX IF EXISTS idx_transaction_type;
DROP INDEX IF EXISTS idx_created_at;

-- Now create everything with correct names

-- Create courier_locations table
CREATE TABLE courier_locations (
    id BIGSERIAL PRIMARY KEY,
    courier_id BIGINT NOT NULL,
    order_id BIGINT,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    address VARCHAR(100),
    speed DOUBLE PRECISION,
    accuracy DOUBLE PRECISION,
    altitude DOUBLE PRECISION,
    bearing DOUBLE PRECISION,
    battery_level INTEGER,
    is_active BOOLEAN DEFAULT TRUE,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    notes VARCHAR(500),
    CONSTRAINT fk_courier_location_courier FOREIGN KEY (courier_id) REFERENCES courier_profiles(id) ON DELETE CASCADE
);

-- Create indexes for courier_locations with unique names
CREATE INDEX idx_courier_locations_courier_id ON courier_locations(courier_id);
CREATE INDEX idx_courier_locations_order_id ON courier_locations(order_id);
CREATE INDEX idx_courier_locations_timestamp ON courier_locations(timestamp);

-- Create courier_wallet_transactions table
CREATE TABLE courier_wallet_transactions (
    id BIGSERIAL PRIMARY KEY,
    wallet_id BIGINT NOT NULL,
    courier_id BIGINT NOT NULL,
    order_id BIGINT,
    transaction_type VARCHAR(50) NOT NULL,
    amount DECIMAL(10, 2) NOT NULL,
    balance_before DECIMAL(10, 2) NOT NULL,
    balance_after DECIMAL(10, 2) NOT NULL,
    description VARCHAR(500),
    reference VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    CONSTRAINT fk_wallet_transaction_wallet FOREIGN KEY (wallet_id) REFERENCES courier_wallets(id) ON DELETE CASCADE
);

-- Create indexes for courier_wallet_transactions with unique names
CREATE INDEX idx_courier_wallet_transactions_wallet_id ON courier_wallet_transactions(wallet_id);
CREATE INDEX idx_courier_wallet_transactions_courier_id ON courier_wallet_transactions(courier_id);
CREATE INDEX idx_courier_wallet_transactions_order_id ON courier_wallet_transactions(order_id);
CREATE INDEX idx_courier_wallet_transactions_transaction_type ON courier_wallet_transactions(transaction_type);
CREATE INDEX idx_courier_wallet_transactions_created_at ON courier_wallet_transactions(created_at);

-- Add comments for documentation
COMMENT ON TABLE courier_locations IS 'Stores real-time GPS location data for courier tracking';
COMMENT ON TABLE courier_wallet_transactions IS 'Tracks all financial transactions in courier wallets';

COMMENT ON COLUMN courier_locations.latitude IS 'GPS latitude coordinate';
COMMENT ON COLUMN courier_locations.longitude IS 'GPS longitude coordinate';
COMMENT ON COLUMN courier_locations.speed IS 'Speed in km/h';
COMMENT ON COLUMN courier_locations.accuracy IS 'GPS accuracy in meters';
COMMENT ON COLUMN courier_locations.battery_level IS 'Battery percentage (0-100)';
COMMENT ON COLUMN courier_locations.is_active IS 'Whether courier is currently active/online';

COMMENT ON COLUMN courier_wallet_transactions.transaction_type IS 'Type: DELIVERY_FEE, BONUS, TIP, FINE, WITHDRAWAL, ADJUSTMENT, REFUND, COMPENSATION';
COMMENT ON COLUMN courier_wallet_transactions.amount IS 'Transaction amount (positive for credit, negative for debit)';
COMMENT ON COLUMN courier_wallet_transactions.balance_before IS 'Wallet balance before transaction';
COMMENT ON COLUMN courier_wallet_transactions.balance_after IS 'Wallet balance after transaction';
