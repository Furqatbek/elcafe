-- V166: Instagram marketing campaigns (async, per-recipient, resumable).
--
-- Until now the only way to message all of a restaurant's Instagram subscribers was
-- InstagramBotService.broadcast(): a synchronous loop on the Tomcat request thread making one blocking
-- Graph call per subscriber. Past the nginx proxy_read_timeout (90s) the operator got a 504 while the
-- loop kept running invisibly, and — because nothing recorded who had been messaged — a re-run
-- double-sent to everyone. This gives Instagram the same per-recipient, async, resumable campaign model
-- the Telegram (telegram_campaigns) and SMS (sms_campaigns) channels already have.
--
-- Both tables are per-tenant from birth (no V163-style retrofit needed): restaurant_id is NOT NULL with
-- an FK to restaurants ON DELETE CASCADE, and the entities carry the §3.4 restaurantFilter.

CREATE TABLE instagram_campaign (
    id              BIGSERIAL   PRIMARY KEY,
    restaurant_id   BIGINT      NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    name            VARCHAR(200) NOT NULL,
    message_text    TEXT        NOT NULL,
    target_audience VARCHAR(20) NOT NULL,
    recipient_count INTEGER     NOT NULL DEFAULT 0,
    sent_count      INTEGER     NOT NULL DEFAULT 0,
    failed_count    INTEGER     NOT NULL DEFAULT 0,
    status          VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    started_at      TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ
);

CREATE INDEX idx_ig_campaign_restaurant ON instagram_campaign(restaurant_id);

CREATE TABLE instagram_campaign_recipient (
    id              BIGSERIAL   PRIMARY KEY,
    restaurant_id   BIGINT      NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    campaign_id     BIGINT      NOT NULL REFERENCES instagram_campaign(id) ON DELETE CASCADE,
    subscriber_id   BIGINT      NOT NULL REFERENCES instagram_subscribers(id) ON DELETE CASCADE,
    igsid           VARCHAR(50) NOT NULL,
    message_content TEXT,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    sent_at         TIMESTAMPTZ,
    error_message   TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- One row per subscriber per campaign. The send loop processes only PENDING rows, so this is what
    -- makes a re-send idempotent: an already-messaged subscriber cannot be duplicated into the run.
    CONSTRAINT uq_ig_campaign_recipient UNIQUE (campaign_id, subscriber_id)
);

-- The executor's hot path: the PENDING recipients of one campaign.
CREATE INDEX idx_ig_campaign_recipient_campaign_status
    ON instagram_campaign_recipient(campaign_id, status);
-- Tenant-leading, for the restaurantFilter and any per-restaurant reporting.
CREATE INDEX idx_ig_campaign_recipient_restaurant
    ON instagram_campaign_recipient(restaurant_id);
