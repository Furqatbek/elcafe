-- V190: a durable outbox for everything we send OUT to an integration partner.
--
-- Until now every integration was inbound: a partner called us. Telling a partner something — this
-- item just sold out, this order was declined — means calling them, and a call to someone else's
-- server fails in ways a local write does not. It times out, it 500s, their deploy takes them offline
-- for ten minutes. Doing that inline would tie our transaction to their uptime; doing it fire-and-
-- forget would lose the message with no trace and leave their menu permanently wrong.
--
-- So writes land here first, in the SAME transaction as the change that caused them. If the order
-- commits, the notification exists; if the order rolls back, so does the notification. A worker
-- delivers them afterwards, retries with backoff, and gives up into a dead-letter queue an operator
-- can see. This is the transactional-outbox pattern, and it is the same shape as print_jobs — which
-- has been carrying this exact load for the print agent already.
--
-- Deliberately generic rather than ZBR-shaped: the pitch every aggregator makes is that they
-- integrate many POS systems, which means we will be doing this again. Partner-specific knowledge
-- lives in a dispatcher bean, not in this table.

CREATE TABLE integration_events (
    id                BIGSERIAL     PRIMARY KEY,
    partner_id        BIGINT        NOT NULL REFERENCES partners(id) ON DELETE CASCADE,
    restaurant_id     BIGINT        NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    -- ORDER_STATUS_CHANGED, MENU_ITEM_CHANGED, MENU_ITEM_AVAILABILITY, ...
    event_type        VARCHAR(40)   NOT NULL,

    -- What this event is ABOUT: "order:512", "product:412". Two things depend on it.
    --
    -- Coalescing: an item whose stock flaps around its threshold would otherwise queue a dozen
    -- contradictory availability messages, and the partner would end on whichever happened to be
    -- delivered last. For state-sync events a newer message makes an older pending one worthless, so
    -- enqueueing supersedes it.
    --
    -- Ordering: for events that are NOT coalesced, the ones sharing a subject must arrive in the
    -- order they happened. Telling a partner an order was ACCEPTED after telling them it was READY
    -- would walk their UI backwards.
    subject_key       VARCHAR(120)  NOT NULL,

    payload           TEXT          NOT NULL,

    -- PENDING -> SENT, or -> DEAD_LETTER after max_attempts, or -> SUPERSEDED when a newer event for
    -- the same subject makes this one obsolete. A failed attempt stays PENDING with a later
    -- next_attempt_at, so the worker's claim query is a single predicate.
    status            VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    attempt_count     INT           NOT NULL DEFAULT 0,
    max_attempts      INT           NOT NULL DEFAULT 10,
    next_attempt_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    -- Truncated: a partner's error body can be a whole HTML page, and this column exists to be read
    -- by a human in a list, not to archive their stack trace.
    last_error        VARCHAR(1000),

    dispatched_at     TIMESTAMPTZ,
    dead_lettered_at  TIMESTAMPTZ,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ,

    CONSTRAINT chk_integration_event_status
        CHECK (status IN ('PENDING', 'SENT', 'DEAD_LETTER', 'SUPERSEDED')),
    CONSTRAINT chk_integration_event_attempts CHECK (attempt_count >= 0 AND max_attempts > 0)
);

-- The worker's own query, run every few seconds: what is due now. Partial, because SENT rows are the
-- overwhelming majority within a day and none of them are ever due again.
CREATE INDEX idx_integration_events_due
    ON integration_events(next_attempt_at, id) WHERE status = 'PENDING';

-- The supersede lookup on enqueue, and the per-subject ordering check on dispatch.
CREATE INDEX idx_integration_events_subject
    ON integration_events(partner_id, subject_key) WHERE status = 'PENDING';

-- The operator's view: what is stuck for this partner.
CREATE INDEX idx_integration_events_partner_status
    ON integration_events(partner_id, status, created_at);

COMMENT ON TABLE integration_events IS
    'Outbound messages to integration partners (V190). Written in the same transaction as the change '
    'that caused them; delivered asynchronously with retry and a dead-letter queue.';
COMMENT ON COLUMN integration_events.subject_key IS
    'What the event is about ("order:512"). Drives coalescing of stale state events and per-subject ordering.';
