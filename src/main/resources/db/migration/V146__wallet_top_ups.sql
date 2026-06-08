-- Customer-initiated wallet top-ups. Each row represents an intent to
-- credit a customer's loyalty wallet via an external payment provider
-- (Click, Payme) or a manual at-counter confirmation.
--
-- Lifecycle: PENDING -> COMPLETED on successful payment (credits via
-- BonusTransaction), or -> FAILED / CANCELLED / EXPIRED. State is
-- terminal once it leaves PENDING.
CREATE TABLE wallet_top_ups (
    id BIGSERIAL PRIMARY KEY,
    customer_id BIGINT NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    amount NUMERIC(12, 2) NOT NULL CHECK (amount > 0),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    provider VARCHAR(20) NOT NULL,
    payment_url TEXT,
    external_transaction_id VARCHAR(120),
    idempotency_key VARCHAR(120) NOT NULL UNIQUE,
    bonus_transaction_id BIGINT REFERENCES bonus_transactions(id),
    failure_reason TEXT,
    metadata JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_wallet_top_ups_customer_id ON wallet_top_ups (customer_id);
CREATE INDEX idx_wallet_top_ups_status ON wallet_top_ups (status);
CREATE UNIQUE INDEX idx_wallet_top_ups_external_tx
    ON wallet_top_ups (provider, external_transaction_id)
    WHERE external_transaction_id IS NOT NULL;
