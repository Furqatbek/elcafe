-- Create idempotency_keys table for preventing duplicate operations (especially payments)
CREATE TABLE idempotency_keys (
    id BIGSERIAL PRIMARY KEY,
    idempotency_key VARCHAR(255) NOT NULL UNIQUE,
    operation_type VARCHAR(50) NOT NULL,
    request_hash VARCHAR(64),
    response_status VARCHAR(20),
    response_body TEXT,
    result_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    locked_until TIMESTAMP
);

-- Create indexes for performance
CREATE INDEX idx_idempotency_key ON idempotency_keys(idempotency_key);
CREATE INDEX idx_idempotency_expires ON idempotency_keys(expires_at);
