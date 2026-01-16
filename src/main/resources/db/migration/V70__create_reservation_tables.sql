-- Online Reservation System Tables
-- V70: Create reservation tables with deposit support

-- Reservation Settings per restaurant
CREATE TABLE IF NOT EXISTS reservation_settings (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    enabled BOOLEAN NOT NULL DEFAULT true,
    advance_days INT NOT NULL DEFAULT 30,
    min_advance_hours INT NOT NULL DEFAULT 2,
    slot_duration_minutes INT NOT NULL DEFAULT 60,
    min_party_size INT NOT NULL DEFAULT 1,
    max_party_size INT NOT NULL DEFAULT 20,
    deposit_required BOOLEAN NOT NULL DEFAULT false,
    deposit_amount DECIMAL(10, 2) DEFAULT 0.00,
    deposit_percent DECIMAL(5, 2) DEFAULT NULL,
    cancellation_hours INT NOT NULL DEFAULT 24,
    auto_confirm BOOLEAN NOT NULL DEFAULT false,
    send_reminders BOOLEAN NOT NULL DEFAULT true,
    reminder_hours_before INT NOT NULL DEFAULT 24,
    max_reservations_per_slot INT DEFAULT NULL,
    notes_for_customers TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(restaurant_id)
);

-- Time slots configuration (for custom availability)
CREATE TABLE IF NOT EXISTS reservation_time_slots (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    day_of_week INT NOT NULL CHECK (day_of_week BETWEEN 0 AND 6),
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    max_reservations INT DEFAULT NULL,
    enabled BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Blocked dates (holidays, special closures)
CREATE TABLE IF NOT EXISTS reservation_blocked_dates (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    blocked_date DATE NOT NULL,
    reason VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Main reservations table
CREATE TABLE IF NOT EXISTS reservations (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    customer_id BIGINT REFERENCES customers(id) ON DELETE SET NULL,
    table_id BIGINT REFERENCES restaurant_tables(id) ON DELETE SET NULL,

    -- Customer details (in case no customer account)
    customer_name VARCHAR(100) NOT NULL,
    customer_phone VARCHAR(20) NOT NULL,
    customer_email VARCHAR(100),

    -- Reservation details
    reservation_date DATE NOT NULL,
    reservation_time TIME NOT NULL,
    party_size INT NOT NULL,
    duration_minutes INT NOT NULL DEFAULT 60,

    -- Status tracking
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',

    -- Special requests
    special_requests TEXT,
    occasion VARCHAR(50),

    -- Confirmation
    confirmation_code VARCHAR(20) NOT NULL UNIQUE,
    confirmed_at TIMESTAMP,
    confirmed_by BIGINT REFERENCES users(id),

    -- Check-in/completion
    checked_in_at TIMESTAMP,
    completed_at TIMESTAMP,

    -- Cancellation
    cancelled_at TIMESTAMP,
    cancellation_reason TEXT,
    no_show BOOLEAN DEFAULT false,

    -- Deposit info
    deposit_required BOOLEAN NOT NULL DEFAULT false,
    deposit_amount DECIMAL(10, 2) DEFAULT 0.00,
    deposit_paid BOOLEAN NOT NULL DEFAULT false,

    -- Source tracking
    source VARCHAR(30) DEFAULT 'WEBSITE',

    -- Reminders
    reminder_sent BOOLEAN NOT NULL DEFAULT false,
    reminder_sent_at TIMESTAMP,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Reservation deposits/payments
CREATE TABLE IF NOT EXISTS reservation_deposits (
    id BIGSERIAL PRIMARY KEY,
    reservation_id BIGINT NOT NULL REFERENCES reservations(id) ON DELETE CASCADE,
    amount DECIMAL(10, 2) NOT NULL,
    payment_method VARCHAR(30),
    payment_reference VARCHAR(100),
    payment_id BIGINT REFERENCES payments(id),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    paid_at TIMESTAMP,
    refunded BOOLEAN NOT NULL DEFAULT false,
    refund_amount DECIMAL(10, 2) DEFAULT 0.00,
    refunded_at TIMESTAMP,
    refund_reason TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Reservation history/audit log
CREATE TABLE IF NOT EXISTS reservation_history (
    id BIGSERIAL PRIMARY KEY,
    reservation_id BIGINT NOT NULL REFERENCES reservations(id) ON DELETE CASCADE,
    action VARCHAR(50) NOT NULL,
    old_status VARCHAR(30),
    new_status VARCHAR(30),
    changed_by BIGINT REFERENCES users(id),
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_reservations_restaurant_date ON reservations(restaurant_id, reservation_date);
CREATE INDEX IF NOT EXISTS idx_reservations_customer ON reservations(customer_id);
CREATE INDEX IF NOT EXISTS idx_reservations_status ON reservations(status);
CREATE INDEX IF NOT EXISTS idx_reservations_confirmation_code ON reservations(confirmation_code);
CREATE INDEX IF NOT EXISTS idx_reservations_date_time ON reservations(reservation_date, reservation_time);
CREATE INDEX IF NOT EXISTS idx_reservation_time_slots_restaurant ON reservation_time_slots(restaurant_id, day_of_week);
CREATE INDEX IF NOT EXISTS idx_reservation_blocked_dates_restaurant ON reservation_blocked_dates(restaurant_id, blocked_date);
CREATE INDEX IF NOT EXISTS idx_reservation_deposits_reservation ON reservation_deposits(reservation_id);
CREATE INDEX IF NOT EXISTS idx_reservation_history_reservation ON reservation_history(reservation_id);
