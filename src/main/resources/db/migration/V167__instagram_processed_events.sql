-- V167: Instagram webhook event idempotency (dedup of Meta's at-least-once re-deliveries).
--
-- Meta delivers webhooks at-least-once and re-sends on any non-2xx response or timeout. The receiver
-- returns 200 immediately and processes on a background thread, so a re-delivery is not driven by our
-- own processing outcome -- but Meta still re-sends the same event, and without a record of what was
-- handled a re-delivered messaging event re-advances the registration wizard (or re-runs the customer
-- link) and a re-delivered comment event re-fires the public auto-reply.
--
-- InstagramWebhookDedupService records the event's Meta id (message/postback mid, or comment id) here
-- before dispatch and skips anything already present. The UNIQUE(restaurant_id, event_id) constraint is
-- the real arbiter: two concurrent re-deliveries both pass the pre-check, but only one INSERT wins and
-- the other is caught as a duplicate.
--
-- Per-tenant from birth: restaurant_id is NOT NULL with an FK to restaurants ON DELETE CASCADE, and the
-- entity carries the section 3.4 restaurantFilter. Uniqueness is per-tenant, so the same Meta id
-- delivered to two restaurants' accounts is two independent rows.

CREATE TABLE instagram_processed_events (
    id            BIGSERIAL    PRIMARY KEY,
    restaurant_id BIGINT       NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    event_id      VARCHAR(255) NOT NULL,
    processed_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- One row per (tenant, Meta event id). This is what makes a re-delivery a no-op, and it arbitrates
    -- the race between two simultaneous re-deliveries of the same event. Its index (restaurant_id-first)
    -- also serves the existsBy pre-check on the hot path.
    CONSTRAINT uq_ig_processed_event UNIQUE (restaurant_id, event_id)
);

-- The retention sweep deletes by age across all tenants (see InstagramWebhookDedupService).
CREATE INDEX idx_ig_processed_event_processed_at ON instagram_processed_events(processed_at);
