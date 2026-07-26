-- V178: Instagram automation rules — birthday and win-back messages (per-tenant channel).
--
-- Mirrors telegram_automation_rules (V64/V164) for the Instagram channel, adapted to the per-tenant
-- model instagram_templates (V172) already established: restaurant_id is NOT NULL with an FK to
-- restaurants ON DELETE CASCADE from birth (no V164-style retrofit needed, exactly like
-- instagram_campaign/instagram_templates), and the entity carries the §3.4 restaurantFilter.
--
-- Fulfils the "🎁 you'll get birthday gifts" promise the Instagram registration wizard already makes
-- (birth_date is collected at the subscriber's AWAITING_BIRTHDAY wizard step — see
-- InstagramSubscriber.birthDate, V163) but which the system never kept until now: InstagramScheduler
-- reads the active rule(s) below and actually sends.
--
-- Column choices that deliberately do NOT match telegram_automation_rules' original (2023-era) shape:
--   * is_active / delay_minutes / sent_count are NOT NULL with a DEFAULT, following instagram_templates
--     (V172) / instagram_campaign (V166)'s stricter, newer convention rather than V64's nullable style.
--   * UNIQUE(restaurant_id, name), backed by InstagramAutomationService rejecting a duplicate name per
--     tenant — telegram_automation_rules has no such constraint (its one application-level check,
--     TelegramAutomationRuleRepository.existsByName, is unscoped/global and unused by any controller
--     today). instagram_templates (V172) already established the per-tenant-unique-name pattern this
--     migration follows instead.
--   * template_id is ON DELETE RESTRICT, made explicit (telegram_automation_rules' bare REFERENCES
--     defaults to the equivalent NO ACTION): an operator must not be able to delete a template a live
--     automation rule still points at and leave the rule silently dangling — deactivate or repoint the
--     rule first.
--   * delay_minutes exists purely for schema parity with telegram_automation_rules (whose scheduler
--     never actually reads it either — it is written and displayed, never applied). Rather than ship
--     another silently-dead field, InstagramAutomationService rejects a non-zero value at create/update
--     time (mirroring SmsAutomationService.requireImmediateDelivery) so the column can only ever hold 0.
--
-- IMPORTANT — Instagram's 24-hour messaging window (full detail: InstagramScheduler's class javadoc).
-- Unlike Telegram (long-polling/webhook, no send-window restriction), Meta allows an Instagram DM ONLY
-- within 24 hours of the recipient's last INBOUND message to the business, and marketing/automated
-- content is not eligible for any of Meta's window-extending message tags. A birthday or win-back send
-- to a subscriber who has not messaged the business in the last 24h is REJECTED by Meta
-- (InstagramSendResult.Failure.RECIPIENT_UNAVAILABLE — error code 10, or subcode 2534014 "outside the
-- allowed window"). InstagramScheduler still ATTEMPTS and LOGS every eligible subscriber every day — an
-- in-window subscriber genuinely IS reached, and every attempt is auditable via InstagramLog — but
-- scheduled outbound to a subscriber who has gone quiet is largely UNDELIVERABLE by design. This is an
-- inherent Meta platform constraint, not a bug in this feature (most acute for WIN_BACK, whose entire
-- audience is, by definition, people who have gone quiet).

CREATE TABLE instagram_automation (
    id                BIGSERIAL    PRIMARY KEY,
    restaurant_id     BIGINT       NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    name              VARCHAR(100) NOT NULL,
    description       TEXT,
    trigger_type      VARCHAR(50)  NOT NULL,        -- BIRTHDAY, WIN_BACK
    template_id       BIGINT       NOT NULL REFERENCES instagram_templates(id) ON DELETE RESTRICT,
    delay_minutes     INTEGER      NOT NULL DEFAULT 0,  -- reserved for parity; not honoured — see above
    is_active         BOOLEAN      NOT NULL DEFAULT true,
    conditions        JSONB,                         -- e.g. {"days_inactive": 14} for WIN_BACK
    sent_count        INTEGER      NOT NULL DEFAULT 0,
    last_triggered_at TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ,
    CONSTRAINT uq_instagram_automation_restaurant_name UNIQUE (restaurant_id, name)
);

-- Tenant-leading, for the restaurantFilter and the paged "list this restaurant's rules" query.
CREATE INDEX idx_instagram_automation_restaurant ON instagram_automation(restaurant_id);

-- The scheduler's own entry point: every active rule of one trigger type, across all tenants — see
-- InstagramAutomationRuleRepository.findActiveRulesWithTemplate's javadoc for why this is deliberately
-- NOT restaurant-scoped (the scheduler resolves each rule's own tenant explicitly afterward).
CREATE INDEX idx_instagram_automation_trigger_active ON instagram_automation(trigger_type, is_active);

-- The template a rule renders; also backs the ON DELETE RESTRICT check.
CREATE INDEX idx_instagram_automation_template ON instagram_automation(template_id);
