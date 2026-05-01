CREATE TABLE packaging_rules (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT NOT NULL REFERENCES restaurants(id),
    product_id BIGINT NOT NULL REFERENCES products(id),
    packaging_product_id BIGINT NOT NULL REFERENCES products(id),
    order_types VARCHAR(50) NOT NULL DEFAULT 'DELIVERY,TAKEAWAY',
    quantity_mode VARCHAR(20) NOT NULL DEFAULT 'PER_ITEM',
    auto_add_quantity INTEGER NOT NULL DEFAULT 1,
    charge_to_customer BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_packaging_rules_product ON packaging_rules(product_id);
CREATE INDEX idx_packaging_rules_restaurant ON packaging_rules(restaurant_id);

COMMENT ON TABLE packaging_rules IS 'Auto-add packaging items (bags, bowls, spoons) for delivery/takeaway orders';
COMMENT ON COLUMN packaging_rules.order_types IS 'Comma-separated order types: DELIVERY,TAKEAWAY';
COMMENT ON COLUMN packaging_rules.quantity_mode IS 'PER_ITEM=multiply by qty, PER_ORDER=add once, FIXED=exact qty';
