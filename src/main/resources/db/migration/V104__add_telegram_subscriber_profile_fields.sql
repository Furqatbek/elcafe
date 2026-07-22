-- Add seller-bot profile fields to telegram_subscribers
ALTER TABLE telegram_subscribers
    ADD COLUMN IF NOT EXISTS display_name    VARCHAR(200),
    ADD COLUMN IF NOT EXISTS phone           VARCHAR(30),
    ADD COLUMN IF NOT EXISTS birth_date      DATE,
    ADD COLUMN IF NOT EXISTS conversation_state VARCHAR(30);

-- Delivery / pick-up locations collected during registration
CREATE TABLE IF NOT EXISTS telegram_subscriber_locations (
    id            BIGSERIAL PRIMARY KEY,
    subscriber_id BIGINT        NOT NULL REFERENCES telegram_subscribers(id) ON DELETE CASCADE,
    latitude      DOUBLE PRECISION NOT NULL,
    longitude     DOUBLE PRECISION NOT NULL,
    label         VARCHAR(200),
    is_default    BOOLEAN DEFAULT false,
    created_at    TIMESTAMP DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_tsl_subscriber ON telegram_subscriber_locations(subscriber_id);
