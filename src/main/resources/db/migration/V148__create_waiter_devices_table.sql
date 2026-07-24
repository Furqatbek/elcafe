-- Registered Expo push tokens for waiter devices (P1-2 native push).
-- One row per token; token is unique so re-registering is an upsert.
CREATE TABLE waiter_devices (
    id BIGSERIAL PRIMARY KEY,
    waiter_id BIGINT NOT NULL,
    token VARCHAR(255) NOT NULL UNIQUE,
    platform VARCHAR(20) NOT NULL,
    device_id VARCHAR(255),
    device_name VARCHAR(255),
    app_version VARCHAR(50),
    registered_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE UNIQUE INDEX idx_waiter_device_token ON waiter_devices(token);
CREATE INDEX idx_waiter_device_waiter ON waiter_devices(waiter_id);
