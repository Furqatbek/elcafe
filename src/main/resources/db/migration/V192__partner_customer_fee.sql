-- V192: what a partner adds to our price before their customer pays.
--
-- A venue sets a channel markup to recover an aggregator's commission, and sets it against the price
-- they believe the customer will see. That belief can be wrong. ZBR adds 8% at their own checkout on
-- top of the number we publish, so a venue choosing "+15%" is choosing +24.2% as far as the diner is
-- concerned — and the lever they think they are pulling is not the lever they are pulling.
--
-- This column exists so the admin screen can show the whole chain. It is DISPLAY ONLY. Nothing prices
-- an item with it, nothing charges it, and no menu we publish includes it: it is the partner's own
-- charge, levied by them, and we could not collect it if we wanted to. A change here must never reach
-- PartnerPriceResolver, which is asserted by a test.
--
-- Zero by default, which means "none, or we have not been told". A partner who has not told us what
-- they add gets no speculative arithmetic shown against their name.

ALTER TABLE partners
    ADD COLUMN customer_fee_percent NUMERIC(5,2) NOT NULL DEFAULT 0;

COMMENT ON COLUMN partners.customer_fee_percent IS
    'What this partner adds at their own checkout, as a percentage of our published price, as they '
    'have told us (V192). Display only — never priced, never charged, never published. 0 = none or unknown.';

ALTER TABLE partners
    ADD CONSTRAINT chk_partner_customer_fee CHECK (customer_fee_percent >= 0 AND customer_fee_percent <= 100);
