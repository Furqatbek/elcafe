-- V193: the ticket a venue is owed for.
--
-- A delivery partner's customer cancels after the kitchen has started. The partner refunds them and
-- marks their own order cancelled; we answer 422 CANCELLATION_WINDOW_CLOSED, because the ingredients
-- are used and a cook's time is spent. Both systems are then right and they disagree: their order is
-- cancelled, ours is still PREPARING, and no courier is coming for food that is still being made.
--
-- Until now that 422 left nothing behind. The kitchen finished a meal nobody would collect, the venue
-- absorbed it, and by close of books nobody could say which ticket it had been. These three columns
-- are the record: when the partner cancelled behind our refusal, how far the food had got, and the
-- reason in their words.
--
-- Written even though the request fails -- the refusal rolls its transaction back, so the record is
-- committed separately, on purpose.
--
-- Deliberately not a settlement. Who bears the cost is a commercial question neither side has answered
-- yet, and a decision taken later can only be applied to these tickets if they were written down at
-- the time. That is the whole reason this exists now rather than after the answer.

ALTER TABLE orders
    ADD COLUMN partner_cancel_refused_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN partner_cancel_refused_stage VARCHAR(30),
    ADD COLUMN partner_cancel_refused_reason VARCHAR(500);

COMMENT ON COLUMN orders.partner_cancel_refused_at IS
    'When a delivery partner reported a cancellation we refused as past the kitchen cutoff (V193). '
    'Their customer is refunded and no courier is coming; the food was made here. NULL for the vast '
    'majority of orders. First refusal wins -- a redelivered webhook cannot count the ticket twice.';

COMMENT ON COLUMN orders.partner_cancel_refused_stage IS
    'The order status at the moment of that refusal (V193) -- how far the food had got, which the '
    'order status itself will no longer show once it moves on to READY and COMPLETED.';

COMMENT ON COLUMN orders.partner_cancel_refused_reason IS
    'The partner''s own words for the cancellation (V193), kept verbatim for the venue and for whatever '
    'settlement is eventually agreed.';

-- The settlement query: every ticket at one venue in a period. Partial, because these are rare and a
-- full index on a column that is null for almost every order would be mostly empty pages.
CREATE INDEX idx_orders_partner_cancel_refused
    ON orders (restaurant_id, partner_cancel_refused_at)
    WHERE partner_cancel_refused_at IS NOT NULL;
