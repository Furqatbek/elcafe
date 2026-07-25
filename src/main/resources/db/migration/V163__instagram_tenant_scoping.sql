-- V163: Make the Instagram integration tenant-scoped (per-restaurant channel).
--
-- V105 created the Instagram tables as a GLOBAL, single-account integration: one bot config row for
-- the whole platform (kept single by application code that deactivates every other row), and a
-- subscriber table keyed by a globally-UNIQUE igsid. That model is wrong for a multi-tenant product:
--   * instagram_bot_config had no owner, so every tenant-scoped ADMIN/OWNER/MANAGER could read and
--     overwrite the shared app secret and access token.
--   * instagram_subscribers had no owner, so subscriber listing, phone search, per-id block/DM and
--     broadcast all crossed tenant boundaries.
--   * The webhook resolved its config with findByIsActiveTrue(), so a second restaurant activating
--     its config silently took the channel away from the first.
-- Product decision: Instagram (like Telegram and SMS) is a PER-TENANT channel — each restaurant
-- connects its own Instagram business account. This migration gives the three tables a restaurant_id
-- so they can be scoped by the §3.4 Hibernate restaurantFilter like customers (V150) and the owner
-- bot (V73).
--
-- BACKFILL MODEL: the Instagram webhook path has never been reachable in any deployment — the route
-- was never added to SecurityConfig's permitAll list, so Meta's hub challenge and every event POST
-- were rejected 401 before reaching the controller. instagram_subscribers is therefore expected to
-- be empty, and instagram_bot_config to hold at most the row(s) an operator typed into the settings
-- page. The backfill below is written to be correct anyway:
--   1. Subscribers that were linked to a customer inherit that customer's restaurant (V150 gave
--      every customer a restaurant_id, so this is authoritative).
--   2. Anything still unassigned goes to the oldest restaurant — the only defensible guess for a
--      channel that was, by construction, single-account. An operator can reassign from the UI.
-- If rows exist and there is no restaurant at all to assign them to, the SET NOT NULL below fails
-- and the whole migration rolls back (Postgres transactional DDL), surfacing the bad data rather
-- than silently dropping a tenant's channel data — the same stance V150 took.

-- ---------------------------------------------------------------------------
-- 1) Tenant columns (nullable for now; populated below, then made NOT NULL)
-- ---------------------------------------------------------------------------
ALTER TABLE instagram_bot_config            ADD COLUMN IF NOT EXISTS restaurant_id BIGINT;
ALTER TABLE instagram_subscribers           ADD COLUMN IF NOT EXISTS restaurant_id BIGINT;
ALTER TABLE instagram_subscriber_addresses  ADD COLUMN IF NOT EXISTS restaurant_id BIGINT;

-- ---------------------------------------------------------------------------
-- 2) Backfill
-- ---------------------------------------------------------------------------

-- 2a) Subscribers linked to a customer inherit that customer's tenant.
UPDATE instagram_subscribers s
SET restaurant_id = c.restaurant_id
FROM customers c
WHERE c.id = s.customer_id
  AND s.restaurant_id IS NULL
  AND c.restaurant_id IS NOT NULL;

-- 2b) Everything still unassigned goes to the oldest restaurant (see BACKFILL MODEL above).
--     Guarded by a subquery that yields NULL when no restaurant exists, leaving rows unassigned so
--     the NOT NULL below fails loudly instead of inventing a tenant.
UPDATE instagram_bot_config
SET restaurant_id = (SELECT id FROM restaurants ORDER BY id LIMIT 1)
WHERE restaurant_id IS NULL;

UPDATE instagram_subscribers
SET restaurant_id = (SELECT id FROM restaurants ORDER BY id LIMIT 1)
WHERE restaurant_id IS NULL;

-- 2c) Addresses always follow their parent subscriber — never guessed independently.
UPDATE instagram_subscriber_addresses a
SET restaurant_id = s.restaurant_id
FROM instagram_subscribers s
WHERE s.id = a.subscriber_id
  AND a.restaurant_id IS NULL;

-- ---------------------------------------------------------------------------
-- 3) Enforce the tenant column
-- ---------------------------------------------------------------------------
ALTER TABLE instagram_bot_config           ALTER COLUMN restaurant_id SET NOT NULL;
ALTER TABLE instagram_subscribers          ALTER COLUMN restaurant_id SET NOT NULL;
ALTER TABLE instagram_subscriber_addresses ALTER COLUMN restaurant_id SET NOT NULL;

ALTER TABLE instagram_bot_config
    ADD CONSTRAINT fk_ig_config_restaurant
    FOREIGN KEY (restaurant_id) REFERENCES restaurants(id) ON DELETE CASCADE;

ALTER TABLE instagram_subscribers
    ADD CONSTRAINT fk_ig_subscriber_restaurant
    FOREIGN KEY (restaurant_id) REFERENCES restaurants(id) ON DELETE CASCADE;

ALTER TABLE instagram_subscriber_addresses
    ADD CONSTRAINT fk_ig_sub_addr_restaurant
    FOREIGN KEY (restaurant_id) REFERENCES restaurants(id) ON DELETE CASCADE;

-- ---------------------------------------------------------------------------
-- 4) Uniqueness becomes per-tenant
-- ---------------------------------------------------------------------------

-- An IGSID is scoped to a Meta app, and each restaurant now connects its own app/account, so the
-- same person messaging two restaurants is two independent subscribers. V105 declared
-- `igsid VARCHAR(50) NOT NULL UNIQUE`, which Postgres named instagram_subscribers_igsid_key.
ALTER TABLE instagram_subscribers DROP CONSTRAINT IF EXISTS instagram_subscribers_igsid_key;
ALTER TABLE instagram_subscribers
    ADD CONSTRAINT uq_ig_subscriber_restaurant_igsid UNIQUE (restaurant_id, igsid);

-- "One active config" was only ever a comment in V105 plus an unsynchronised read-then-write in the
-- service, so two concurrent activations could produce two active rows and make every
-- findByIsActiveTrue() throw IncorrectResultSizeDataAccessException. Now it is a real per-tenant
-- constraint: at most one active Instagram config per restaurant.
CREATE UNIQUE INDEX IF NOT EXISTS uq_ig_config_active_per_restaurant
    ON instagram_bot_config(restaurant_id)
    WHERE is_active;

-- The webhook resolves its tenant from the Meta payload's entry.id (the IG business account id), so
-- that lookup must be unique platform-wide and indexed — two restaurants cannot claim one account.
CREATE UNIQUE INDEX IF NOT EXISTS uq_ig_config_account
    ON instagram_bot_config(instagram_account_id)
    WHERE instagram_account_id IS NOT NULL;

-- ---------------------------------------------------------------------------
-- 5) Indexes
-- ---------------------------------------------------------------------------

-- Redundant since V105: igsid already had a UNIQUE constraint (hence its own index), so this was
-- pure write amplification. The new composite unique above covers per-tenant igsid lookups.
DROP INDEX IF EXISTS idx_ig_subscriber_igsid;

-- Unindexed FK: forced a sequential scan of instagram_subscribers on every customer delete.
CREATE INDEX IF NOT EXISTS idx_ig_subscriber_customer ON instagram_subscribers(customer_id);

-- Broadcast/listing path: always tenant-scoped, then filtered on is_active/is_blocked.
CREATE INDEX IF NOT EXISTS idx_ig_subscriber_restaurant_active
    ON instagram_subscribers(restaurant_id, is_active, is_blocked);

CREATE INDEX IF NOT EXISTS idx_ig_sub_addr_restaurant
    ON instagram_subscriber_addresses(restaurant_id);
