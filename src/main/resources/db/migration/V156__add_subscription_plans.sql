-- V156: Subscription plans foundation (Phase 1 / mini-phase A1 — schema only).
--
-- Introduces paid tiers (Start / Advance / Pro) so restaurants can be gated by plan later. This
-- migration is DATA SHAPE ONLY: it creates the subscription_plan catalogue, attaches plan columns to
-- restaurants, and hard-cuts every existing restaurant to the free Start plan. No service gates, no
-- API, no UI yet — those land in later A-phases. The module-to-tier mapping is still open, so the
-- seeded feature_codes are empty '[]' (populated once the mapping is finalised, mini-phase A4).
--
-- Runs on Postgres. The H2 test suite has flyway.enabled=false, so this is not exercised by tests;
-- the equivalent schema is generated from the JPA entities (ddl-auto: create-drop) there.
--
-- Note on NOT NULL: restaurants.plan_id is made NOT NULL at the DB level after backfill (every
-- restaurant must have a plan). The JPA mapping intentionally leaves Restaurant.plan nullable so
-- code/tests that build a Restaurant before plan assignment are unaffected — Hibernate `validate`
-- does not check column nullability, so prod still validates clean.

-- 1) Plan catalogue. feature_codes is a JSONB array of feature-code strings (e.g.
--    ["kitchen.dashboard"]); empty for now. monthly_price is UZS so'm (no fractional unit), 0 until
--    pricing is finalised.
CREATE TABLE subscription_plan (
    id            BIGSERIAL    PRIMARY KEY,
    code          VARCHAR(32)  NOT NULL UNIQUE,            -- start, advance, pro
    name          VARCHAR(64)  NOT NULL,
    monthly_price BIGINT       NOT NULL DEFAULT 0,         -- UZS, no fractional unit
    feature_codes JSONB        NOT NULL DEFAULT '[]'::jsonb,
    sort_order    INTEGER      NOT NULL DEFAULT 0,
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 2) Seed the three tiers. Prices and feature_codes are filled in later (pricing decision + the
--    module-to-tier mapping respectively); seeding them now gives A2/A3 a stable catalogue to read.
INSERT INTO subscription_plan (code, name, monthly_price, feature_codes, sort_order) VALUES
    ('start',   'Start',   0, '[]'::jsonb, 1),
    ('advance', 'Advance', 0, '[]'::jsonb, 2),
    ('pro',     'Pro',     0, '[]'::jsonb, 3);

-- 3) Attach plan columns to restaurants. Nullable for now; plan_id is backfilled then made NOT NULL.
--    plan_expires_at NULL = no expiry (Start is free and never expires).
ALTER TABLE restaurants
    ADD COLUMN plan_id         BIGINT,
    ADD COLUMN plan_started_at TIMESTAMP,
    ADD COLUMN plan_expires_at TIMESTAMP,
    ADD COLUMN is_trial        BOOLEAN NOT NULL DEFAULT FALSE;

-- 4) Hard cut: every existing restaurant goes to Start, no expiry, not a trial. (Accepted product
--    decision — no legacy/transitional tier.)
UPDATE restaurants
SET plan_id         = (SELECT id FROM subscription_plan WHERE code = 'start'),
    plan_started_at = CURRENT_TIMESTAMP,
    plan_expires_at = NULL,
    is_trial        = FALSE
WHERE plan_id IS NULL;

-- 5) Enforce the FK + NOT NULL now that every row is backfilled, and index the FK for plan lookups.
ALTER TABLE restaurants
    ADD CONSTRAINT fk_restaurants_plan FOREIGN KEY (plan_id) REFERENCES subscription_plan(id);
ALTER TABLE restaurants ALTER COLUMN plan_id SET NOT NULL;
CREATE INDEX idx_restaurants_plan ON restaurants(plan_id);
