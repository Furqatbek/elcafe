-- Durable fire-once marker for the order-completion event chain (audit FUNC-15).
-- OrderCompletionEvents publishes the loyalty/marketing OrderCompletedEvent exactly once per order;
-- the marker is written in the same transaction as the publish, so replays are impossible even when
-- an order's qualification oscillates (tip raises grandTotal after full payment, refunds, admin
-- payment corrections). NULL = the event has not fired for this order.
ALTER TABLE orders ADD COLUMN completion_event_published_at TIMESTAMP;

COMMENT ON COLUMN orders.completion_event_published_at IS
    'When the loyalty/marketing order-completed event was published for this order (fire-once marker); NULL = never';
