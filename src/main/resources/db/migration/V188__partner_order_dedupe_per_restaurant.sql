-- V188: scope partner order deduplication by venue as well as by partner.
--
-- V187 made (partner_id, external_order_id) unique, which quietly assumed every aggregator numbers its
-- orders globally. Many number them per store. Under the V187 key, partner P pushing its order "1001"
-- to venue 11 and then "1001" to venue 12 matched the first row, was answered "duplicate — already
-- handled", and venue 12 never received the order. The partner's retry logic, correctly, does not
-- retry a duplicate. Every collision was a silently lost order and a customer waiting for food nobody
-- was cooking.
--
-- The same key also let a partner read back an order from a venue whose grant had since been revoked:
-- the lookup never compared the stored restaurant, so an old external id returned that order's number,
-- line items and prices through a still-valid grant on a different venue.
--
-- Separate migration rather than an edit to V187 because V187 has already been applied elsewhere, and
-- rewriting an applied migration breaks Flyway's checksum on every database that ran it.

ALTER TABLE partner_orders DROP CONSTRAINT IF EXISTS uq_partner_order_external;

ALTER TABLE partner_orders ADD CONSTRAINT uq_partner_order_external
    UNIQUE (partner_id, restaurant_id, external_order_id);

COMMENT ON CONSTRAINT uq_partner_order_external ON partner_orders IS
    'One order per (partner, venue, their order id). Venue-scoped because aggregators number orders per store.';
