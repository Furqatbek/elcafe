-- V164: Make the Telegram integration tenant-scoped (per-restaurant channel).
--
-- V64 built Telegram as a GLOBAL, single-bot integration: one bot_token for the whole platform, a
-- subscriber table keyed by a globally-UNIQUE telegram_user_id, and campaigns/templates/automation
-- rules with no owner. That was survivable only because every controller was locked to SUPER_ADMIN.
-- Product decision: Telegram (like Instagram in V163, and SMS next) is a PER-TENANT channel — each
-- restaurant runs its own bot, with its own token, subscribers, campaigns and templates.
--
-- This migration gives all nine Telegram tables a restaurant_id so they can be scoped by the §3.4
-- Hibernate restaurantFilter, exactly as V73 already does for the owner bot and V163 for Instagram.
--
-- BACKFILL MODEL — unlike Instagram (whose webhook was never reachable, so its tables were empty),
-- telegram_subscribers can hold REAL production rows. Assignment is therefore evidence-based and
-- ordered from strongest evidence to weakest:
--   1. A subscriber linked to a customer inherits that customer's restaurant. V150 gave every
--      customer a restaurant_id, so this is authoritative.
--   2. Anything still unassigned goes to the oldest restaurant. For a channel that was, by
--      construction, a single global bot, that is the only defensible reading: those rows belong to
--      whoever was running that one bot. Operators can reassign from the admin UI.
--   3. Child rows NEVER guess — they inherit from their parent (recipients from their campaign,
--      locations from their subscriber, logs from their subscriber then campaign).
-- If rows exist and there is no restaurant to assign them to, SET NOT NULL fails and the whole
-- migration rolls back (Postgres transactional DDL), surfacing the bad data rather than silently
-- reassigning a live subscriber base. Same stance as V150 and V163.

-- ---------------------------------------------------------------------------
-- 1) Tenant columns (nullable for now; populated below, then made NOT NULL)
-- ---------------------------------------------------------------------------
ALTER TABLE telegram_bot_config             ADD COLUMN IF NOT EXISTS restaurant_id BIGINT;
ALTER TABLE telegram_subscribers            ADD COLUMN IF NOT EXISTS restaurant_id BIGINT;
ALTER TABLE telegram_subscriber_locations   ADD COLUMN IF NOT EXISTS restaurant_id BIGINT;
ALTER TABLE telegram_templates              ADD COLUMN IF NOT EXISTS restaurant_id BIGINT;
ALTER TABLE telegram_campaigns              ADD COLUMN IF NOT EXISTS restaurant_id BIGINT;
ALTER TABLE telegram_campaign_recipients    ADD COLUMN IF NOT EXISTS restaurant_id BIGINT;
ALTER TABLE telegram_automation_rules       ADD COLUMN IF NOT EXISTS restaurant_id BIGINT;
ALTER TABLE telegram_logs                   ADD COLUMN IF NOT EXISTS restaurant_id BIGINT;
ALTER TABLE telegram_bot_commands           ADD COLUMN IF NOT EXISTS restaurant_id BIGINT;

-- ---------------------------------------------------------------------------
-- 2) Backfill — strongest evidence first
-- ---------------------------------------------------------------------------

-- 2a) Subscribers linked to a customer inherit that customer's tenant (authoritative).
UPDATE telegram_subscribers s
SET restaurant_id = c.restaurant_id
FROM customers c
WHERE c.id = s.customer_id
  AND s.restaurant_id IS NULL
  AND c.restaurant_id IS NOT NULL;

-- 2b) Everything that was global goes to the oldest restaurant (see BACKFILL MODEL).
--     The subquery yields NULL when no restaurant exists, leaving rows unassigned so the NOT NULL
--     below fails loudly instead of inventing a tenant.
UPDATE telegram_bot_config       SET restaurant_id = (SELECT id FROM restaurants ORDER BY id LIMIT 1) WHERE restaurant_id IS NULL;
UPDATE telegram_subscribers      SET restaurant_id = (SELECT id FROM restaurants ORDER BY id LIMIT 1) WHERE restaurant_id IS NULL;
UPDATE telegram_templates        SET restaurant_id = (SELECT id FROM restaurants ORDER BY id LIMIT 1) WHERE restaurant_id IS NULL;
UPDATE telegram_campaigns        SET restaurant_id = (SELECT id FROM restaurants ORDER BY id LIMIT 1) WHERE restaurant_id IS NULL;
UPDATE telegram_automation_rules SET restaurant_id = (SELECT id FROM restaurants ORDER BY id LIMIT 1) WHERE restaurant_id IS NULL;
UPDATE telegram_bot_commands     SET restaurant_id = (SELECT id FROM restaurants ORDER BY id LIMIT 1) WHERE restaurant_id IS NULL;

-- 2c) Child rows inherit from their parent — never guessed independently.
UPDATE telegram_subscriber_locations l
SET restaurant_id = s.restaurant_id
FROM telegram_subscribers s
WHERE s.id = l.subscriber_id AND l.restaurant_id IS NULL;

UPDATE telegram_campaign_recipients r
SET restaurant_id = c.restaurant_id
FROM telegram_campaigns c
WHERE c.id = r.campaign_id AND r.restaurant_id IS NULL;

-- Logs: prefer the subscriber they were sent to, then the campaign that produced them.
UPDATE telegram_logs g
SET restaurant_id = s.restaurant_id
FROM telegram_subscribers s
WHERE s.id = g.subscriber_id AND g.restaurant_id IS NULL;

UPDATE telegram_logs g
SET restaurant_id = c.restaurant_id
FROM telegram_campaigns c
WHERE c.id = g.campaign_id AND g.restaurant_id IS NULL;

UPDATE telegram_logs
SET restaurant_id = (SELECT id FROM restaurants ORDER BY id LIMIT 1)
WHERE restaurant_id IS NULL;

-- ---------------------------------------------------------------------------
-- 3) Enforce the tenant column
-- ---------------------------------------------------------------------------
ALTER TABLE telegram_bot_config           ALTER COLUMN restaurant_id SET NOT NULL;
ALTER TABLE telegram_subscribers          ALTER COLUMN restaurant_id SET NOT NULL;
ALTER TABLE telegram_subscriber_locations ALTER COLUMN restaurant_id SET NOT NULL;
ALTER TABLE telegram_templates            ALTER COLUMN restaurant_id SET NOT NULL;
ALTER TABLE telegram_campaigns            ALTER COLUMN restaurant_id SET NOT NULL;
ALTER TABLE telegram_campaign_recipients  ALTER COLUMN restaurant_id SET NOT NULL;
ALTER TABLE telegram_automation_rules     ALTER COLUMN restaurant_id SET NOT NULL;
ALTER TABLE telegram_logs                 ALTER COLUMN restaurant_id SET NOT NULL;
ALTER TABLE telegram_bot_commands         ALTER COLUMN restaurant_id SET NOT NULL;

ALTER TABLE telegram_bot_config           ADD CONSTRAINT fk_tg_config_restaurant      FOREIGN KEY (restaurant_id) REFERENCES restaurants(id) ON DELETE CASCADE;
ALTER TABLE telegram_subscribers          ADD CONSTRAINT fk_tg_subscriber_restaurant  FOREIGN KEY (restaurant_id) REFERENCES restaurants(id) ON DELETE CASCADE;
ALTER TABLE telegram_subscriber_locations ADD CONSTRAINT fk_tg_location_restaurant    FOREIGN KEY (restaurant_id) REFERENCES restaurants(id) ON DELETE CASCADE;
ALTER TABLE telegram_templates            ADD CONSTRAINT fk_tg_template_restaurant    FOREIGN KEY (restaurant_id) REFERENCES restaurants(id) ON DELETE CASCADE;
ALTER TABLE telegram_campaigns            ADD CONSTRAINT fk_tg_campaign_restaurant    FOREIGN KEY (restaurant_id) REFERENCES restaurants(id) ON DELETE CASCADE;
ALTER TABLE telegram_campaign_recipients  ADD CONSTRAINT fk_tg_recipient_restaurant   FOREIGN KEY (restaurant_id) REFERENCES restaurants(id) ON DELETE CASCADE;
ALTER TABLE telegram_automation_rules     ADD CONSTRAINT fk_tg_rule_restaurant        FOREIGN KEY (restaurant_id) REFERENCES restaurants(id) ON DELETE CASCADE;
ALTER TABLE telegram_logs                 ADD CONSTRAINT fk_tg_log_restaurant         FOREIGN KEY (restaurant_id) REFERENCES restaurants(id) ON DELETE CASCADE;
ALTER TABLE telegram_bot_commands         ADD CONSTRAINT fk_tg_command_restaurant     FOREIGN KEY (restaurant_id) REFERENCES restaurants(id) ON DELETE CASCADE;

-- ---------------------------------------------------------------------------
-- 4) Uniqueness becomes per-tenant
-- ---------------------------------------------------------------------------

-- One Telegram account can now subscribe to several restaurants' bots — each is an independent
-- subscriber with its own wizard state, phone and saved locations. V64 declared
-- `telegram_user_id BIGINT NOT NULL UNIQUE` (Postgres: telegram_subscribers_telegram_user_id_key).
ALTER TABLE telegram_subscribers DROP CONSTRAINT IF EXISTS telegram_subscribers_telegram_user_id_key;
ALTER TABLE telegram_subscribers
    ADD CONSTRAINT uq_tg_subscriber_restaurant_user UNIQUE (restaurant_id, telegram_user_id);

-- Commands are per-bot, so "/start" exists once per restaurant, not once per platform.
ALTER TABLE telegram_bot_commands DROP CONSTRAINT IF EXISTS telegram_bot_commands_command_key;
ALTER TABLE telegram_bot_commands
    ADD CONSTRAINT uq_tg_command_restaurant UNIQUE (restaurant_id, command);

-- At most one active bot config per restaurant. Previously "one active config" was only an
-- application-level assumption (findByIsActiveTrue), maintained by an unsynchronised read-then-write.
CREATE UNIQUE INDEX IF NOT EXISTS uq_tg_config_active_per_restaurant
    ON telegram_bot_config(restaurant_id)
    WHERE is_active;

-- A bot token identifies the tenant for every inbound update: the bot instance that receives an
-- update IS the restaurant. Two restaurants sharing a token would make updates unattributable, so
-- the token must be globally unique.
CREATE UNIQUE INDEX IF NOT EXISTS uq_tg_config_bot_token
    ON telegram_bot_config(bot_token);

-- ---------------------------------------------------------------------------
-- 5) Indexes — every hot path is now tenant-scoped first
-- ---------------------------------------------------------------------------

-- Superseded by uq_tg_subscriber_restaurant_user, whose leading column is restaurant_id.
DROP INDEX IF EXISTS idx_telegram_subscribers_user_id;
DROP INDEX IF EXISTS idx_telegram_subscribers_active;

CREATE INDEX IF NOT EXISTS idx_tg_subscribers_restaurant_active
    ON telegram_subscribers(restaurant_id, is_active, is_blocked);
CREATE INDEX IF NOT EXISTS idx_tg_templates_restaurant   ON telegram_templates(restaurant_id, is_active);
CREATE INDEX IF NOT EXISTS idx_tg_campaigns_restaurant   ON telegram_campaigns(restaurant_id, status);
CREATE INDEX IF NOT EXISTS idx_tg_recipients_restaurant  ON telegram_campaign_recipients(restaurant_id);
CREATE INDEX IF NOT EXISTS idx_tg_rules_restaurant       ON telegram_automation_rules(restaurant_id, is_active);
CREATE INDEX IF NOT EXISTS idx_tg_logs_restaurant        ON telegram_logs(restaurant_id, created_at);
CREATE INDEX IF NOT EXISTS idx_tg_commands_restaurant    ON telegram_bot_commands(restaurant_id, is_active);
CREATE INDEX IF NOT EXISTS idx_tg_locations_restaurant   ON telegram_subscriber_locations(restaurant_id);
