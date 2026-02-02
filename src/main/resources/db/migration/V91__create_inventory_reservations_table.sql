-- Create inventory_reservations table for tracking inventory reservations to prevent overselling
-- When an order is being created, ingredients are reserved before checkout
-- Reservations are either confirmed (converted to deductions) or expired (released)

CREATE TABLE inventory_reservations (
    id BIGSERIAL PRIMARY KEY,
    ingredient_id BIGINT NOT NULL REFERENCES inventory_ingredients(id),
    order_id BIGINT,
    session_id VARCHAR(100),
    product_id BIGINT,
    quantity DECIMAL(10, 3) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    confirmed_at TIMESTAMP,
    released_at TIMESTAMP,
    version BIGINT DEFAULT 0
);

-- Create indexes for performance
CREATE INDEX idx_reservation_ingredient ON inventory_reservations(ingredient_id);
CREATE INDEX idx_reservation_order ON inventory_reservations(order_id);
CREATE INDEX idx_reservation_session ON inventory_reservations(session_id);
CREATE INDEX idx_reservation_status ON inventory_reservations(status);
CREATE INDEX idx_reservation_expires ON inventory_reservations(expires_at);
