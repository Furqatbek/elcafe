-- V194: whether a partner's paymentMode means anything yet.
--
-- ZBR send paymentMode on every order and told us plainly that today it is a constant: every order
-- says PREPAID because their apps do not set it yet. PREPAID is not decoration here -- it creates the
-- payment settled, and the kitchen ticket prints as paid. A counter hand gives a bag to a courier who
-- owes nothing, and the venue finds out at close of books.
--
-- We answered that they should simply not switch a venue on for order push until it is real. That was
-- a promise someone has to remember, and our own demo seeder had already forgotten it. So it is a
-- switch instead: false until a partner tells us the field is meaningful, and while it is false the
-- grant endpoint refuses can_push_orders and says why. Menu reads and status reports are unaffected.
--
-- False for every existing partner, including any already pushing orders: nothing in flight is
-- revoked, but re-granting requires the question to be answered.

ALTER TABLE partners
    ADD COLUMN payment_mode_confirmed BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN partners.payment_mode_confirmed IS
    'Whether this partner''s paymentMode field reflects how the customer actually paid, rather than a '
    'constant they have not implemented yet (V194). While false, this partner cannot be granted order '
    'push at any venue. Set only when the partner has told us the field is real.';
