-- V187: delivery-aggregator (partner) integration — identity, per-venue grants, and order mapping.
--
-- An aggregator is not a tenant and not a user. It spans restaurants (one aggregator lists many of our
-- venues) while every existing actor in this schema belongs to exactly one restaurant, so `partners`
-- deliberately carries no restaurant_id and is NOT covered by the §3.4 restaurantFilter. Authorization
-- is the join table instead: a partner may touch a venue only if a `partner_restaurants` row grants it.
-- That inverts the usual direction here, and it is the point — revoking one venue must not disturb the
-- other venues the same partner serves.

CREATE TABLE partners (
    id                BIGSERIAL    PRIMARY KEY,
    name              VARCHAR(200) NOT NULL,
    -- Stable, human-readable handle. It namespaces the partner's idempotency keys
    -- ("partner:{slug}:{their order id}"), and `idempotency_key` is globally unique, so two partners
    -- reusing the same order-numbering scheme would otherwise collide and silently replay each
    -- other's orders. Immutable once issued for exactly that reason.
    slug              VARCHAR(100) NOT NULL,
    -- SHA-256 hex of the issued key, never the key itself. SHA-256 rather than bcrypt on purpose: an
    -- API key is 256 bits of our own randomness, not a human-chosen password, so it has nothing to
    -- brute-force and the work factor would only buy latency on every single request. The unique index
    -- also makes authentication one indexed lookup instead of a scan-and-compare over every partner.
    api_key_hash      VARCHAR(64)  NOT NULL,
    -- First few characters of the raw key, kept so an operator can tell two keys apart in the admin UI
    -- and name the one to rotate. Not secret and not sufficient to authenticate.
    api_key_prefix    VARCHAR(16),
    contact_email     VARCHAR(255),
    active            BOOLEAN      NOT NULL DEFAULT true,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ,
    CONSTRAINT uq_partners_slug UNIQUE (slug),
    CONSTRAINT uq_partners_api_key_hash UNIQUE (api_key_hash)
);

COMMENT ON TABLE partners IS
    'Delivery aggregators and other external systems that pull our menu and push orders in (V187).';
COMMENT ON COLUMN partners.api_key_hash IS
    'SHA-256 hex of the issued API key. The raw key is shown once at creation and never stored.';

-- Which venues a partner may act on, and what it may do there. Two booleans rather than a scope string:
-- there are exactly two capabilities, and a boolean is checkable in SQL and impossible to typo.
-- Menu-read is granted by default, order-push is not — onboarding a partner starts read-only, and
-- letting them write into a venue's kitchen is a second, deliberate decision.
CREATE TABLE partner_restaurants (
    id                BIGSERIAL    PRIMARY KEY,
    partner_id        BIGINT       NOT NULL REFERENCES partners(id) ON DELETE CASCADE,
    restaurant_id     BIGINT       NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    can_read_menu     BOOLEAN      NOT NULL DEFAULT true,
    can_push_orders   BOOLEAN      NOT NULL DEFAULT false,
    active            BOOLEAN      NOT NULL DEFAULT true,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ,
    CONSTRAINT uq_partner_restaurant UNIQUE (partner_id, restaurant_id)
);

-- The authentication hot path: "may partner P act on restaurant R?" on every partner request.
CREATE INDEX idx_partner_restaurants_partner ON partner_restaurants(partner_id, restaurant_id);
-- The admin read: "who has access to this venue?"
CREATE INDEX idx_partner_restaurants_restaurant ON partner_restaurants(restaurant_id);

COMMENT ON TABLE partner_restaurants IS
    'Per-venue grant: a partner may only read the menu of, or push orders to, restaurants listed here.';

-- Maps the partner's own order identifier onto ours, in both directions.
--
-- This is what makes a retried push safe and a support question answerable. The generic
-- `idempotency_keys` table already collapses duplicate requests, but it expires after 24h and stores
-- an opaque cached response; this row is permanent and is the actual correlation record — "their order
-- 88213 is our ORD-000417" — which is the first thing anyone asks when a delivery goes wrong.
CREATE TABLE partner_orders (
    id                BIGSERIAL    PRIMARY KEY,
    -- RESTRICT, not CASCADE: deleting a partner must not silently erase the provenance of orders a
    -- venue has already cooked and been paid for. Partners are deactivated, not deleted.
    partner_id        BIGINT       NOT NULL REFERENCES partners(id) ON DELETE RESTRICT,
    restaurant_id     BIGINT       NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    external_order_id VARCHAR(190) NOT NULL,
    order_id          BIGINT       NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- The durable dedupe guarantee. Even after the idempotency key expires, a partner replaying an old
    -- order id cannot create a second kitchen ticket for it.
    CONSTRAINT uq_partner_order_external UNIQUE (partner_id, external_order_id)
);

-- Reverse lookup for the admin UI and support: given one of our orders, which partner sent it.
CREATE INDEX idx_partner_orders_order ON partner_orders(order_id);
-- Tenant-leading, for listing a venue's aggregator orders.
CREATE INDEX idx_partner_orders_restaurant ON partner_orders(restaurant_id, created_at);

COMMENT ON TABLE partner_orders IS
    'Correlation between a partner''s own order id and ours (V187). Permanent; outlives idempotency keys.';

-- Order.orderSource is @Enumerated(EnumType.STRING) on a VARCHAR column, so the new enum constant plus
-- this CHECK update is all the persistence needs — mirrors V180 (INSTAGRAM_BOT) and V108 (SELF_SERVICE)
-- exactly; there is no native Postgres enum type to alter.
ALTER TABLE orders DROP CONSTRAINT IF EXISTS chk_order_source;

ALTER TABLE orders ADD CONSTRAINT chk_order_source CHECK (
    order_source IS NULL OR
    order_source IN ('TELEGRAM_BOT', 'WEBSITE', 'ADMIN_PANEL', 'MOBILE_APP', 'PHONE_CALL', 'WALK_IN', 'WAITER', 'SELF_SERVICE', 'INSTAGRAM_BOT', 'AGGREGATOR', 'OTHER')
);

COMMENT ON COLUMN orders.order_source IS 'Channel through which order was placed: TELEGRAM_BOT, WEBSITE, ADMIN_PANEL, MOBILE_APP, PHONE_CALL, WALK_IN, WAITER, SELF_SERVICE, INSTAGRAM_BOT, AGGREGATOR, OTHER';
