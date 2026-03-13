-- Add weight-based and portion-based selling support to order_items
-- weight_amount: actual weight ordered for sell-by-weight products (e.g. 0.75 for 750g)
-- weight_unit:   unit copied from product at order time (KG, G, LB, OZ)
-- portion_multiplier: price multiplier for portion-based products (0.5=half, 1.0=full, 2.0=double)

ALTER TABLE order_items
    ADD COLUMN IF NOT EXISTS weight_amount     NUMERIC(10, 4),
    ADD COLUMN IF NOT EXISTS weight_unit       VARCHAR(10),
    ADD COLUMN IF NOT EXISTS portion_multiplier NUMERIC(10, 4) NOT NULL DEFAULT 1.0;
