-- Courier Management System Tables

-- Courier Profiles Table
CREATE TABLE IF NOT EXISTS courier_profiles (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    courier_type VARCHAR(50) NOT NULL,
    vehicle VARCHAR(50) NOT NULL,
    vehicle_plate VARCHAR(50),
    license_number VARCHAR(100),
    available BOOLEAN NOT NULL DEFAULT TRUE,
    verified BOOLEAN NOT NULL DEFAULT FALSE,
    address TEXT,
    city VARCHAR(100),
    emergency_contact VARCHAR(20),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE courier_profiles IS 'Stores courier-specific profile information';
COMMENT ON COLUMN courier_profiles.courier_type IS 'Type of courier: FULL_TIME, PART_TIME, FREELANCE, CONTRACTOR';
COMMENT ON COLUMN courier_profiles.vehicle IS 'Vehicle type: BICYCLE, MOTORCYCLE, SCOOTER, CAR, ON_FOOT';
COMMENT ON COLUMN courier_profiles.available IS 'Whether courier is currently available for deliveries';
COMMENT ON COLUMN courier_profiles.verified IS 'Whether courier has been verified by admin';

-- Courier Wallets Table
CREATE TABLE IF NOT EXISTS courier_wallets (
    id BIGSERIAL PRIMARY KEY,
    courier_profile_id BIGINT NOT NULL UNIQUE REFERENCES courier_profiles(id) ON DELETE CASCADE,
    balance DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    total_earned DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    total_withdrawn DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    total_bonuses DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    total_fines DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE courier_wallets IS 'Stores courier wallet balance and transaction summaries';
COMMENT ON COLUMN courier_wallets.balance IS 'Current available balance';
COMMENT ON COLUMN courier_wallets.total_earned IS 'Total amount earned from deliveries';
COMMENT ON COLUMN courier_wallets.total_bonuses IS 'Total bonuses received';
COMMENT ON COLUMN courier_wallets.total_fines IS 'Total fines deducted';

-- Wallet Transactions Table
CREATE TABLE IF NOT EXISTS wallet_transactions (
    id BIGSERIAL PRIMARY KEY,
    wallet_id BIGINT NOT NULL REFERENCES courier_wallets(id) ON DELETE CASCADE,
    type VARCHAR(50) NOT NULL,
    amount DECIMAL(10, 2) NOT NULL,
    balance_before DECIMAL(10, 2) NOT NULL,
    balance_after DECIMAL(10, 2) NOT NULL,
    description TEXT,
    reference_id VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE wallet_transactions IS 'Tracks all wallet transactions for couriers';
COMMENT ON COLUMN wallet_transactions.type IS 'Transaction type: BONUS, FINE, PAYMENT, WITHDRAWAL, ADJUSTMENT';
COMMENT ON COLUMN wallet_transactions.reference_id IS 'Reference to related entity (order ID, tariff ID, etc.)';

-- Courier Tariffs Table
CREATE TABLE IF NOT EXISTS courier_tariffs (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    type VARCHAR(50) NOT NULL,
    description TEXT,
    fixed_amount DECIMAL(10, 2) DEFAULT 0.00,
    amount_per_order DECIMAL(10, 2) DEFAULT 0.00,
    amount_per_kilometer DECIMAL(10, 2) DEFAULT 0.00,
    min_orders INTEGER,
    max_orders INTEGER,
    min_distance DECIMAL(5, 2),
    max_distance DECIMAL(5, 2),
    min_attendance_days INTEGER,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE courier_tariffs IS 'Defines tariff rules for courier bonuses and fines';
COMMENT ON COLUMN courier_tariffs.type IS 'Tariff type: BONUS or FINE';
COMMENT ON COLUMN courier_tariffs.fixed_amount IS 'Fixed amount to apply';
COMMENT ON COLUMN courier_tariffs.amount_per_order IS 'Amount per order completed';
COMMENT ON COLUMN courier_tariffs.amount_per_kilometer IS 'Amount per kilometer traveled';
COMMENT ON COLUMN courier_tariffs.min_orders IS 'Minimum orders required to apply bonus';
COMMENT ON COLUMN courier_tariffs.min_attendance_days IS 'Minimum attendance days required';

-- Courier Bonus/Fine Records Table
CREATE TABLE IF NOT EXISTS courier_bonus_fines (
    id BIGSERIAL PRIMARY KEY,
    courier_profile_id BIGINT NOT NULL REFERENCES courier_profiles(id) ON DELETE CASCADE,
    tariff_id BIGINT REFERENCES courier_tariffs(id) ON DELETE SET NULL,
    type VARCHAR(50) NOT NULL,
    amount DECIMAL(10, 2) NOT NULL,
    reason TEXT,
    reference_id VARCHAR(100),
    orders_completed INTEGER,
    distance_covered DECIMAL(10, 2),
    attendance_days INTEGER,
    applied BOOLEAN NOT NULL DEFAULT FALSE,
    applied_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE courier_bonus_fines IS 'Records of bonuses and fines applied to couriers';
COMMENT ON COLUMN courier_bonus_fines.type IS 'BONUS or FINE';
COMMENT ON COLUMN courier_bonus_fines.applied IS 'Whether the bonus/fine has been applied to wallet';
COMMENT ON COLUMN courier_bonus_fines.orders_completed IS 'Number of orders in the period';
COMMENT ON COLUMN courier_bonus_fines.distance_covered IS 'Total distance covered in km';

-- Courier Attendance Table
CREATE TABLE IF NOT EXISTS courier_attendance (
    id BIGSERIAL PRIMARY KEY,
    courier_profile_id BIGINT NOT NULL REFERENCES courier_profiles(id) ON DELETE CASCADE,
    date DATE NOT NULL,
    check_in_time TIME,
    check_out_time TIME,
    present BOOLEAN NOT NULL DEFAULT FALSE,
    notes TEXT,
    created_at TIME NOT NULL DEFAULT CURRENT_TIME,
    UNIQUE(courier_profile_id, date)
);

COMMENT ON TABLE courier_attendance IS 'Tracks daily attendance of couriers';
COMMENT ON COLUMN courier_attendance.present IS 'Whether courier was present on this date';
COMMENT ON COLUMN courier_attendance.check_in_time IS 'Time courier checked in';
COMMENT ON COLUMN courier_attendance.check_out_time IS 'Time courier checked out';

-- Create Indexes
CREATE INDEX idx_courier_profiles_user_id ON courier_profiles(user_id);
CREATE INDEX idx_courier_profiles_available ON courier_profiles(available);
CREATE INDEX idx_courier_wallets_courier_id ON courier_wallets(courier_profile_id);
CREATE INDEX idx_wallet_transactions_wallet_id ON wallet_transactions(wallet_id);
CREATE INDEX idx_wallet_transactions_type ON wallet_transactions(type);
CREATE INDEX idx_courier_tariffs_type ON courier_tariffs(type);
CREATE INDEX idx_courier_tariffs_active ON courier_tariffs(active);
CREATE INDEX idx_courier_bonus_fines_courier_id ON courier_bonus_fines(courier_profile_id);
CREATE INDEX idx_courier_bonus_fines_applied ON courier_bonus_fines(applied);
CREATE INDEX idx_courier_attendance_courier_id ON courier_attendance(courier_profile_id);
CREATE INDEX idx_courier_attendance_date ON courier_attendance(date);
