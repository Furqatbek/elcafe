-- Customer reviews and feedback
CREATE TABLE reviews (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT REFERENCES orders(id),
    customer_id BIGINT REFERENCES customers(id),
    restaurant_id BIGINT NOT NULL REFERENCES restaurants(id),
    order_number VARCHAR(50),
    customer_name VARCHAR(200),
    rating INTEGER NOT NULL CHECK (rating >= 1 AND rating <= 5),
    comment TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED',
    reply TEXT,
    replied_at TIMESTAMP,
    replied_by VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_reviews_order ON reviews(order_id) WHERE order_id IS NOT NULL;
CREATE INDEX idx_reviews_restaurant ON reviews(restaurant_id);
CREATE INDEX idx_reviews_rating ON reviews(restaurant_id, rating);
CREATE INDEX idx_reviews_created ON reviews(restaurant_id, created_at DESC);
CREATE INDEX idx_reviews_status ON reviews(restaurant_id, status);

COMMENT ON TABLE reviews IS 'Customer reviews and feedback linked to orders';
