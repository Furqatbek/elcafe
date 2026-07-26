-- V179: inbound-message storage + human-handoff inbox for the per-tenant Instagram module.
--
-- Today an inbound DM's text is handed to the registration wizard (InstagramBotService) and then
-- thrown away — nothing durable records what a customer actually said, so there is no way to answer
-- "what did this customer say last Tuesday", and the only admin action toward a subscriber is a blind
-- one-shot DM (InstagramBotService#sendAdminMessage). This migration adds the storage half of an
-- agent-takeover inbox: every inbound TEXT message is now persisted, and a human agent can claim a
-- subscriber's thread away from the wizard for a while.
--
-- ---------------------------------------------------------------------------------------------------
-- Why a NEW table (instagram_inbound_message) rather than a `direction` column on instagram_logs (V171)
-- ---------------------------------------------------------------------------------------------------
-- instagram_logs is the SEND audit trail: InstagramStatisticsService counts its rows by `status` as
-- outbound message volume (sent/delivered/failed/pending — see getStatusCountsByRestaurantId), and
-- InstagramMessageLogger#record is the one chokepoint for every Graph send this module makes. Folding
-- inbound rows into that same table (a `direction` column, or a sentinel messageType) would silently
-- inflate every one of those counts with rows that were never sent anywhere. instagram_logs' own
-- history already treats "no DM recipient" (AUTO_REPLY/PRIVATE_REPLY, which repurpose the igsid column
-- for a comment id) as a documented exception, not a precedent for mixing directions — a genuinely
-- inbound row is a bigger departure than that. A dedicated table keeps instagram_logs' "one row per
-- send attempt" invariant intact and needs no defensive "WHERE direction = 'OUT'" retrofitted onto
-- every statistics/retry/resume query that already reads it.
--
-- ---------------------------------------------------------------------------------------------------
-- Shape
-- ---------------------------------------------------------------------------------------------------
--   * restaurant_id NOT NULL — per-tenant from birth like every Instagram table since V163, scoped by
--     the §3.4 restaurantFilter.
--   * subscriber_id ON DELETE CASCADE, nullable — CASCADE (not instagram_logs' SET NULL) because this
--     row IS the customer's own words, i.e. PII in the fullest sense, and mirrors the subscriber-
--     erasure story (InstagramBotService#eraseSubscriber): once the subscriber is gone this content
--     must not survive it either. Nullable because a stranger's very first inbound message can be a
--     STOP/SUBSCRIBE keyword that InstagramBotService#handleOptOut / #handleOptIn deliberately never
--     creates a subscriber row for (nothing to opt out of yet) — the message is still worth storing,
--     just without a subscriber to cascade from.
--   * igsid NOT NULL — denormalised the same way instagram_logs.igsid is (V171): the row must still
--     identify its sender even when subscriber_id is null.
--   * message_text TEXT, nullable in the column definition (matching instagram_logs' permissive
--     shape), though the application only ever writes inbound TEXT-kind messages here — a quick-reply
--     tap, story engagement or unsupported-attachment placeholder is classified separately by
--     InstagramWebhookService and is not "what the customer said".
--   * received_at TIMESTAMPTZ NOT NULL DEFAULT now() — matches every timestamp on every Instagram
--     table introduced since V163.
--
-- Erasure: explicit deleteBySubscriber / deleteByRestaurantIdAndIgsid calls in
-- InstagramMessageLogger#eraseSubscriberLogs (reached from InstagramBotService#eraseSubscriber and its
-- CustomerDeletedEvent listener) erase these rows immediately and tenant-cleanly, mirroring V171's own
-- dual erasure path exactly. The ON DELETE CASCADE above is only the backstop for a subscriber row
-- removed any other way.

CREATE TABLE instagram_inbound_message (
    id            BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    subscriber_id BIGINT REFERENCES instagram_subscribers(id) ON DELETE CASCADE,
    igsid         VARCHAR(50) NOT NULL,
    message_text  TEXT,
    received_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- restaurant_id: every tenant-scoped inbox read starts here.
CREATE INDEX idx_ig_inbound_msg_restaurant ON instagram_inbound_message(restaurant_id);

-- subscriber_id: "this subscriber's inbound history" (one conversation's transcript) and the
-- PII-erasure delete.
CREATE INDEX idx_ig_inbound_msg_subscriber ON instagram_inbound_message(subscriber_id);

-- (restaurant_id, received_at): the inbox's "recent conversations, newest first" listing — the table's
-- primary read path, mirroring idx_instagram_logs_restaurant_created (V171).
CREATE INDEX idx_ig_inbound_msg_restaurant_received ON instagram_inbound_message(restaurant_id, received_at);

-- ---------------------------------------------------------------------------------------------------
-- human_handoff_until on instagram_subscribers
-- ---------------------------------------------------------------------------------------------------
-- Nullable, no default — the same "absence is meaningful" choice V175 (token_expires_at) and V177
-- (last_webhook_received_at) already made. NULL or a past timestamp means "the wizard answers, as
-- today"; a future timestamp means a human agent has claimed this subscriber's thread (the new inbox's
-- take-over action) and InstagramWebhookService must still store inbound messages but skip dispatching
-- them to the wizard until it lapses or an explicit release clears it. See InstagramWebhookService's
-- isHandedOffNow for the read side and InstagramInboxService#takeover/#release for the write side.
ALTER TABLE instagram_subscribers ADD COLUMN IF NOT EXISTS human_handoff_until TIMESTAMPTZ;
