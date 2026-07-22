-- Create order_financial_events table for event sourcing of order financial data
-- Tracks all financial state changes to allow reconstruction of order state at any point in time
-- Critical for POS systems for auditing, dispute resolution, and compliance

CREATE TABLE order_financial_events (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL,
    order_number VARCHAR(50) NOT NULL,
    sequence_number INTEGER NOT NULL,
    event_type VARCHAR(50) NOT NULL,

    -- Financial snapshot at this point
    subtotal DECIMAL(10, 2),
    tax DECIMAL(10, 2),
    delivery_fee DECIMAL(10, 2),
    service_fee DECIMAL(10, 2),
    service_fee_percent DECIMAL(5, 2),
    entry_fee DECIMAL(10, 2),
    discount DECIMAL(10, 2),
    discount_type VARCHAR(50),
    total DECIMAL(10, 2),
    tip_amount DECIMAL(10, 2),
    grand_total DECIMAL(10, 2),
    total_paid DECIMAL(10, 2),
    refunded_amount DECIMAL(10, 2),

    -- Event-specific data
    payment_id BIGINT,
    payment_method VARCHAR(50),
    payment_amount DECIMAL(10, 2),
    item_id BIGINT,
    item_name VARCHAR(255),
    item_quantity INTEGER,
    item_price DECIMAL(10, 2),
    promotion_id BIGINT,
    coupon_code VARCHAR(50),

    -- Metadata
    performed_by VARCHAR(100),
    reason VARCHAR(500),
    notes TEXT,
    terminal_id VARCHAR(50),
    ip_address VARCHAR(50),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for performance
CREATE INDEX idx_financial_event_order ON order_financial_events(order_id);
CREATE INDEX idx_financial_event_type ON order_financial_events(event_type);
CREATE INDEX idx_financial_event_created ON order_financial_events(created_at);
CREATE INDEX idx_financial_event_sequence ON order_financial_events(order_id, sequence_number);
