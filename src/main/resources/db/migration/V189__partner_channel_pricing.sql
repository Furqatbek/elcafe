-- V189: sell the same dish at a different price on a partner's channel.
--
-- A restaurant listing on an aggregator pays that aggregator a commission, so the shelf price there is
-- normally higher than at the counter. Until now one `products.price` served every channel, which left
-- an owner two bad options: raise the price for walk-in customers too, or absorb the commission.
--
-- Modelled as a RULE, not a copied price list. A second table of literal prices would drift the moment
-- someone edits the base price — you would raise a dish by 2000 and silently keep selling it on the
-- aggregator at yesterday's number. A rule ("+15%") re-derives from the live base price every time, so
-- the two can never fall out of step.
--
-- The common case — one markup for a whole venue — is a column on the grant row that already exists,
-- so it costs no extra table and no extra query. Overrides are opt-in.

ALTER TABLE partner_restaurants
    -- NONE keeps the base price, which is what every existing grant gets on upgrade: this migration
    -- must not silently reprice a live integration.
    ADD COLUMN price_adjustment_type  VARCHAR(10)   NOT NULL DEFAULT 'NONE',
    -- PERCENT: 15 means +15%. AMOUNT: 500 means +500 to the price. Four decimals so a fractional
    -- percent is expressible; negatives are allowed (a partner may be given a discount) but the
    -- resolved price is floored at zero.
    ADD COLUMN price_adjustment_value NUMERIC(10,4) NOT NULL DEFAULT 0,
    -- Round the marked-up price to a multiple of this. 0 = no rounding. 500 turns 34 500 into 34 500
    -- and 34 600 into 34 500 — menus read badly when a markup produces 34 567, and the partner
    -- displays our number verbatim, so the rounding has to happen here rather than on their side.
    ADD COLUMN price_rounding         NUMERIC(10,2) NOT NULL DEFAULT 0;

ALTER TABLE partner_restaurants ADD CONSTRAINT chk_partner_price_adjustment_type
    CHECK (price_adjustment_type IN ('NONE', 'PERCENT', 'AMOUNT'));

-- A percentage cannot take more than the whole price away.
ALTER TABLE partner_restaurants ADD CONSTRAINT chk_partner_price_percent_floor
    CHECK (price_adjustment_type <> 'PERCENT' OR price_adjustment_value >= -100);

ALTER TABLE partner_restaurants ADD CONSTRAINT chk_partner_price_rounding
    CHECK (price_rounding >= 0);

COMMENT ON COLUMN partner_restaurants.price_adjustment_type IS
    'Default channel markup for this partner at this venue: NONE, PERCENT or AMOUNT (V189).';

-- Exceptions to the venue default. Deliberately a separate table: most venues will have none, and the
-- resolution path should not pay for a join that usually returns nothing.
CREATE TABLE partner_price_rules (
    id                BIGSERIAL     PRIMARY KEY,
    partner_id        BIGINT        NOT NULL REFERENCES partners(id) ON DELETE CASCADE,
    restaurant_id     BIGINT        NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    -- CATEGORY | PRODUCT | VARIANT. Most specific wins, so a product rule beats its category's.
    scope             VARCHAR(20)   NOT NULL,
    -- The id of the category, product or variant this rule applies to.
    --
    -- Deliberately NOT a foreign key: it points at a different table depending on `scope`, and there
    -- is no way to express that as one constraint. The cost is orphan rows when a product is deleted;
    -- the cost is acceptable because resolution only ever looks up rules for items it has already
    -- loaded, so an orphan rule is inert rather than wrong.
    target_id         BIGINT        NOT NULL,
    -- PERCENT and AMOUNT adjust the base price. FIXED replaces it outright and skips rounding, because
    -- someone who typed an exact price meant that exact price.
    adjustment_type   VARCHAR(10)   NOT NULL,
    adjustment_value  NUMERIC(10,4) NOT NULL,
    active            BOOLEAN       NOT NULL DEFAULT true,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ,
    CONSTRAINT uq_partner_price_rule UNIQUE (partner_id, restaurant_id, scope, target_id),
    CONSTRAINT chk_partner_price_rule_scope CHECK (scope IN ('CATEGORY', 'PRODUCT', 'VARIANT')),
    CONSTRAINT chk_partner_price_rule_type CHECK (adjustment_type IN ('PERCENT', 'AMOUNT', 'FIXED')),
    CONSTRAINT chk_partner_price_rule_percent CHECK (adjustment_type <> 'PERCENT' OR adjustment_value >= -100),
    CONSTRAINT chk_partner_price_rule_fixed CHECK (adjustment_type <> 'FIXED' OR adjustment_value >= 0)
);

-- The resolver's own read: every rule for one partner at one venue, fetched once per menu build or
-- order rather than once per item.
CREATE INDEX idx_partner_price_rules_lookup
    ON partner_price_rules(partner_id, restaurant_id) WHERE active;

COMMENT ON TABLE partner_price_rules IS
    'Per-category/product/variant exceptions to a partner''s default channel markup (V189).';
