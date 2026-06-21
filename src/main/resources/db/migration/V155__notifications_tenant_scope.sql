-- V155: tenant-scope notifications (Phase 0 §3.7 follow-up — staff-side hardening).
--
-- Notification had no restaurant_id and no @Filter, so staff could read/mutate ANY restaurant's
-- notifications by id/userId (cross-tenant), and ADMIN/COURIER "broadcasts" (user_id NULL) fan out
-- across every restaurant. This adds restaurant_id so the §3.4 Hibernate tenant filter scopes
-- notifications like every other business entity (closing the cross-tenant staff IDOR on the enforce
-- flip; a restaurant's staff then see only their restaurant's notifications, SUPER_ADMIN sees all).
--
-- Every notification references an order, so restaurant_id is backfilled from that order, with
-- user_id/customer fallbacks for completeness. Kept NULLABLE on purpose: any stray un-backfillable
-- row stays NULL and is then invisible to tenant-scoped reads (only SUPER_ADMIN's null scope sees it),
-- which is the safe default, and avoids a NOT NULL backfill failure on a high-volume table. New rows
-- are always stamped by NotificationService.
--
-- Postgres-only (UPDATE ... FROM); the H2 test suite has flyway.enabled=false, so not exercised here.

ALTER TABLE notifications ADD COLUMN IF NOT EXISTS restaurant_id BIGINT;

-- Primary backfill: the order every notification references.
UPDATE notifications n SET restaurant_id = o.restaurant_id
    FROM orders o WHERE n.order_id = o.id AND n.restaurant_id IS NULL;

-- RESTAURANT / KITCHEN notifications carry the restaurant id directly in user_id.
UPDATE notifications SET restaurant_id = user_id
    WHERE restaurant_id IS NULL AND user_role IN ('RESTAURANT', 'KITCHEN') AND user_id IS NOT NULL;

-- CUSTOMER notifications → the customer's restaurant.
UPDATE notifications n SET restaurant_id = c.restaurant_id
    FROM customers c WHERE n.restaurant_id IS NULL AND n.user_role = 'CUSTOMER' AND n.user_id = c.id;

CREATE INDEX IF NOT EXISTS idx_notifications_restaurant_id ON notifications(restaurant_id);
ALTER TABLE notifications ADD CONSTRAINT fk_notifications_restaurant
    FOREIGN KEY (restaurant_id) REFERENCES restaurants(id);
