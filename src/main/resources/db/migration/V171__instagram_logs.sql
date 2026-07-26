-- V171: instagram_logs — the per-tenant Instagram message log.
--
-- Every Instagram send this module fires (DM wizard reply, admin DM, campaign broadcast, comment
-- auto-reply) is meant to leave exactly one row here, whatever the outcome — via the new
-- InstagramMessageLogger, called right after InstagramApiClient returns. This table is the load-bearing
-- substrate for Instagram statistics, retry and resume: without a durable per-attempt record, none of
-- those can tell a delivered send from a failed one, or a not-yet-attempted one from either.
--
-- Mirrors telegram_logs (V64) but Instagram-shaped:
--   * igsid (not a numeric telegram_user_id) is the recipient identifier, denormalised the same way
--     instagram_campaign_recipient (V166) denormalises it. A comment auto-reply has no DM recipient, so
--     for that row igsid instead carries the Meta comment id being replied to.
--   * instagram_message_id is Meta's opaque mid STRING (Telegram's message id is numeric).
--   * campaign_id is a plain nullable id, matching the InstagramLog entity's plain Long field (the
--     campaign executor only ever has the id on hand) — no FK, since the entity has no @ManyToOne here.
--   * Timestamps are TIMESTAMPTZ, matching every Instagram table introduced since V163
--     (instagram_subscribers, instagram_bot_config, instagram_campaign) rather than telegram_logs'
--     older TIMESTAMP columns.
--   * Only restaurant_id is NOT NULL. A logging failure must never cost a send (InstagramMessageLogger
--     swallows any save error), but an avoidable NOT NULL violation would silently drop the very row
--     that failed attempt should have left behind — so every other column stays permissive.

CREATE TABLE instagram_logs (
    id                   BIGSERIAL PRIMARY KEY,
    restaurant_id        BIGINT NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    subscriber_id        BIGINT REFERENCES instagram_subscribers(id) ON DELETE SET NULL,
    igsid                VARCHAR(50),
    message_type         VARCHAR(50),               -- AUTOMATION, CAMPAIGN, MANUAL, NOTIFICATION, AUTO_REPLY
    status               VARCHAR(20) DEFAULT 'PENDING', -- PENDING, SENT, DELIVERED, FAILED, ... (sms.MessageStatus)
    instagram_message_id VARCHAR(255),               -- Meta's mid; not yet populated (see entity javadoc)
    message              TEXT,
    error_message        TEXT,
    error_code           INTEGER,
    campaign_id          BIGINT,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    delivered_at         TIMESTAMPTZ,
    metadata             JSONB
);

-- restaurant_id: every tenant-scoped listing/stats query starts here.
CREATE INDEX idx_instagram_logs_restaurant ON instagram_logs(restaurant_id);

-- subscriber_id: "this subscriber's message history" (admin UI) and the PII-erasure delete.
CREATE INDEX idx_instagram_logs_subscriber ON instagram_logs(subscriber_id);

-- campaign_id: "this campaign's send log" — the retry/resume path a campaign re-send needs.
CREATE INDEX idx_instagram_logs_campaign ON instagram_logs(campaign_id);

-- (restaurant_id, created_at): the paged, newest-first admin log view — the table's primary read path.
CREATE INDEX idx_instagram_logs_restaurant_created ON instagram_logs(restaurant_id, created_at);
