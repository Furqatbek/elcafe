-- V165: Make SMS marketing tenant-scoped (per-restaurant campaigns, templates, rules and logs).
--
-- Last of the three marketing channels to be fragmented (Instagram V163, Telegram V164). V63 built
-- SMS as a global platform feature: campaigns, templates, automation rules and logs with no owner,
-- which was only survivable because every SMS controller was locked to SUPER_ADMIN.
--
-- HOW SMS DIFFERS FROM THE OTHER TWO CHANNELS. Instagram and Telegram each carry a per-tenant
-- IDENTITY — a restaurant connects its own Instagram business account, or runs its own bot with its
-- own token — so the receiving account/bot identifies the tenant. SMS has no inbound channel and no
-- per-restaurant identity: every message leaves through ONE platform Eskiz account, configured with
-- the ESKIZ_SMS_* environment variables rather than a database row. There is deliberately no
-- sms_bot_config table here and none is added.
--
-- So "per-tenant SMS" means the DATA is per-restaurant — each restaurant writes its own campaigns
-- and templates and may only ever target its own customers — while the SENDING ACCOUNT stays shared
-- platform infrastructure. Two consequences worth stating plainly:
--   * Recipients are drawn from `customers`, which V150 already made per-restaurant, so a campaign
--     physically cannot reach another restaurant's customers once its targeting is tenant-scoped.
--   * The Eskiz sender ID / alphanumeric header remains platform-wide, so recipients still see the
--     platform's sender, not the restaurant's. Giving each restaurant its own sender would require a
--     per-restaurant Eskiz contract and is out of scope here.
--
-- BACKFILL MODEL, strongest evidence first (same stance as V150/V163/V164):
--   1. Rows that reference a customer inherit that customer's restaurant (authoritative since V150).
--   2. Child rows inherit from their parent — recipients from their campaign, logs from their
--      campaign or rule — never guessed independently.
--   3. Anything still unassigned goes to the oldest restaurant: these were global rows created when
--      SMS was a single platform feature, so they belong to whoever was operating it. Reassignable
--      from the admin UI.
-- If rows exist with no restaurant to hold them, SET NOT NULL fails and the migration rolls back.

-- ---------------------------------------------------------------------------
-- 1) Tenant columns (nullable for now; populated below, then made NOT NULL)
-- ---------------------------------------------------------------------------
ALTER TABLE sms_templates            ADD COLUMN IF NOT EXISTS restaurant_id BIGINT;
ALTER TABLE sms_campaigns            ADD COLUMN IF NOT EXISTS restaurant_id BIGINT;
ALTER TABLE sms_campaign_recipients  ADD COLUMN IF NOT EXISTS restaurant_id BIGINT;
ALTER TABLE sms_automation_rules     ADD COLUMN IF NOT EXISTS restaurant_id BIGINT;
ALTER TABLE sms_logs                 ADD COLUMN IF NOT EXISTS restaurant_id BIGINT;

-- ---------------------------------------------------------------------------
-- 2) Backfill
-- ---------------------------------------------------------------------------

-- 2a) Rows that name a customer inherit that customer's restaurant.
UPDATE sms_campaign_recipients r
SET restaurant_id = c.restaurant_id
FROM customers c
WHERE c.id = r.customer_id AND r.restaurant_id IS NULL AND c.restaurant_id IS NOT NULL;

UPDATE sms_logs g
SET restaurant_id = c.restaurant_id
FROM customers c
WHERE c.id = g.customer_id AND g.restaurant_id IS NULL AND c.restaurant_id IS NOT NULL;

-- 2b) Campaigns inherit from the recipients they produced (a campaign only ever targeted one
--     restaurant's customers in practice; pick the lowest restaurant seen for determinism).
UPDATE sms_campaigns c
SET restaurant_id = sub.restaurant_id
FROM (
    SELECT campaign_id, MIN(restaurant_id) AS restaurant_id
    FROM sms_campaign_recipients
    WHERE restaurant_id IS NOT NULL
    GROUP BY campaign_id
) sub
WHERE sub.campaign_id = c.id AND c.restaurant_id IS NULL;

-- 2c) Remaining globals go to the oldest restaurant. NULL when none exists, so the NOT NULL below
--     fails loudly rather than inventing a tenant.
UPDATE sms_templates        SET restaurant_id = (SELECT id FROM restaurants ORDER BY id LIMIT 1) WHERE restaurant_id IS NULL;
UPDATE sms_campaigns        SET restaurant_id = (SELECT id FROM restaurants ORDER BY id LIMIT 1) WHERE restaurant_id IS NULL;
UPDATE sms_automation_rules SET restaurant_id = (SELECT id FROM restaurants ORDER BY id LIMIT 1) WHERE restaurant_id IS NULL;

-- 2d) Child rows still unassigned (no customer link) follow their parent.
UPDATE sms_campaign_recipients r
SET restaurant_id = c.restaurant_id
FROM sms_campaigns c
WHERE c.id = r.campaign_id AND r.restaurant_id IS NULL;

UPDATE sms_logs g
SET restaurant_id = c.restaurant_id
FROM sms_campaigns c
WHERE c.id = g.campaign_id AND g.restaurant_id IS NULL;

UPDATE sms_logs g
SET restaurant_id = a.restaurant_id
FROM sms_automation_rules a
WHERE a.id = g.automation_rule_id AND g.restaurant_id IS NULL;

UPDATE sms_logs
SET restaurant_id = (SELECT id FROM restaurants ORDER BY id LIMIT 1)
WHERE restaurant_id IS NULL;

-- ---------------------------------------------------------------------------
-- 2e) Drop rows that NO restaurant can own.
--
-- After 2a-2d a row is still NULL only when `(SELECT id FROM restaurants ...)` returned NULL — i.e.
-- the deployment has no restaurants at all, the normal state of a fresh database at migrate time.
-- V63 deliberately seeds nothing (its trailing comment records that the demo templates and rules
-- were removed), so today these DELETEs match nothing even on an empty database. They are here
-- because V164 learned this the hard way: V73 seeds platform-wide telegram_templates, which made
-- SET NOT NULL fail on every fresh deploy. This keeps the same failure impossible for SMS if a
-- future migration ever seeds one of these tables.
--
-- Not a data-loss path: with even one restaurant present, 2b/2c assign every row and nothing below
-- matches. Ordered children-first so the foreign keys hold.
-- ---------------------------------------------------------------------------
DELETE FROM sms_logs                WHERE restaurant_id IS NULL;
DELETE FROM sms_campaign_recipients WHERE restaurant_id IS NULL;
DELETE FROM sms_campaigns           WHERE restaurant_id IS NULL;
DELETE FROM sms_automation_rules    WHERE restaurant_id IS NULL;
DELETE FROM sms_templates           WHERE restaurant_id IS NULL;

-- ---------------------------------------------------------------------------
-- 3) Enforce the tenant column
-- ---------------------------------------------------------------------------
ALTER TABLE sms_templates           ALTER COLUMN restaurant_id SET NOT NULL;
ALTER TABLE sms_campaigns           ALTER COLUMN restaurant_id SET NOT NULL;
ALTER TABLE sms_campaign_recipients ALTER COLUMN restaurant_id SET NOT NULL;
ALTER TABLE sms_automation_rules    ALTER COLUMN restaurant_id SET NOT NULL;
ALTER TABLE sms_logs                ALTER COLUMN restaurant_id SET NOT NULL;

ALTER TABLE sms_templates           ADD CONSTRAINT fk_sms_template_restaurant  FOREIGN KEY (restaurant_id) REFERENCES restaurants(id) ON DELETE CASCADE;
ALTER TABLE sms_campaigns           ADD CONSTRAINT fk_sms_campaign_restaurant  FOREIGN KEY (restaurant_id) REFERENCES restaurants(id) ON DELETE CASCADE;
ALTER TABLE sms_campaign_recipients ADD CONSTRAINT fk_sms_recipient_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurants(id) ON DELETE CASCADE;
ALTER TABLE sms_automation_rules    ADD CONSTRAINT fk_sms_rule_restaurant      FOREIGN KEY (restaurant_id) REFERENCES restaurants(id) ON DELETE CASCADE;
ALTER TABLE sms_logs                ADD CONSTRAINT fk_sms_log_restaurant       FOREIGN KEY (restaurant_id) REFERENCES restaurants(id) ON DELETE CASCADE;

-- ---------------------------------------------------------------------------
-- 4) Indexes — every hot path is now tenant-scoped first
-- ---------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_sms_templates_restaurant  ON sms_templates(restaurant_id, is_active);
CREATE INDEX IF NOT EXISTS idx_sms_campaigns_restaurant  ON sms_campaigns(restaurant_id, status);
CREATE INDEX IF NOT EXISTS idx_sms_recipients_restaurant ON sms_campaign_recipients(restaurant_id);
CREATE INDEX IF NOT EXISTS idx_sms_rules_restaurant      ON sms_automation_rules(restaurant_id, trigger_type, is_active);
CREATE INDEX IF NOT EXISTS idx_sms_logs_restaurant       ON sms_logs(restaurant_id, created_at);
