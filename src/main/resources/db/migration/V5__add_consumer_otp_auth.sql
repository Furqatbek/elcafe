-- Migration V5: Add Consumer OTP Authentication System
-- Creates table for storing OTP codes for phone-based authentication

-- OTP Codes Table
CREATE TABLE otp_codes (
    id BIGSERIAL PRIMARY KEY,
    phone_number VARCHAR(20) NOT NULL,
    otp_code VARCHAR(6) NOT NULL,
    is_verified BOOLEAN DEFAULT FALSE,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    verified_at TIMESTAMP,
    ip_address VARCHAR(45),
    user_agent VARCHAR(255),
    attempts INTEGER DEFAULT 0,
    CONSTRAINT otp_codes_phone_number_check CHECK (phone_number ~ '^[0-9+]+$')
);

-- Indexes for performance
CREATE INDEX idx_otp_codes_phone_number ON otp_codes(phone_number);
CREATE INDEX idx_otp_codes_expires_at ON otp_codes(expires_at);
CREATE INDEX idx_otp_codes_is_verified ON otp_codes(is_verified);

-- Consumer Sessions Table (for tracking logged-in consumers)
CREATE TABLE consumer_sessions (
    id BIGSERIAL PRIMARY KEY,
    phone_number VARCHAR(20) NOT NULL,
    customer_id BIGINT,
    session_token VARCHAR(512) NOT NULL UNIQUE,
    refresh_token VARCHAR(512) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    refresh_expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_accessed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    ip_address VARCHAR(45),
    user_agent VARCHAR(255),
    is_active BOOLEAN DEFAULT TRUE,
    FOREIGN KEY (customer_id) REFERENCES customers(id) ON DELETE CASCADE
);

-- Indexes for session management
CREATE INDEX idx_consumer_sessions_phone_number ON consumer_sessions(phone_number);
CREATE INDEX idx_consumer_sessions_session_token ON consumer_sessions(session_token);
CREATE INDEX idx_consumer_sessions_refresh_token ON consumer_sessions(refresh_token);
CREATE INDEX idx_consumer_sessions_expires_at ON consumer_sessions(expires_at);
CREATE INDEX idx_consumer_sessions_is_active ON consumer_sessions(is_active);

-- Comments
COMMENT ON TABLE otp_codes IS 'Stores OTP codes for phone-based authentication';
COMMENT ON COLUMN otp_codes.phone_number IS 'Phone number in international format';
COMMENT ON COLUMN otp_codes.otp_code IS '6-digit OTP code';
COMMENT ON COLUMN otp_codes.is_verified IS 'Whether OTP has been verified';
COMMENT ON COLUMN otp_codes.expires_at IS 'OTP expiration timestamp (typically 5 minutes)';
COMMENT ON COLUMN otp_codes.attempts IS 'Number of verification attempts';

COMMENT ON TABLE consumer_sessions IS 'Stores active consumer sessions for mobile app/website users';
COMMENT ON COLUMN consumer_sessions.session_token IS 'JWT access token for API requests';
COMMENT ON COLUMN consumer_sessions.refresh_token IS 'Refresh token for obtaining new access tokens';
COMMENT ON COLUMN consumer_sessions.customer_id IS 'Reference to customer record if exists';
