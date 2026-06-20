-- V153: per-restaurant loyalty (Phase 0 §3.7).
--
-- Loyalty is already per-restaurant for NEW activity: every mutation keys off
-- customer_loyalty.customer_id, which V150 made a per-restaurant id. This migration makes that
-- scoping EXPLICIT and enforceable — it adds restaurant_id (NOT NULL, FK, @Filter-backed) to the
-- loyalty tables so the §3.4 Hibernate tenant backstop covers them and by-restaurant queries work.
--
-- Product decision (zero-out, start fresh): pre-V150 historical balances were stranded on each
-- multi-restaurant customer's PRIMARY row (V150 deliberately did not split them). Per an explicit
-- product decision, ALL loyalty balances are reset to 0 here so every (customer, restaurant) starts
-- fresh. The spendable balance commingles promo points and customer-funded top-ups; note that
-- top-ups are by design "non-refundable promo credit, not a stored-value liability" (see
-- WalletTopUp), so this reset is a promo-credit reset, not a refund event. Transaction and top-up
-- HISTORY rows are retained (with restaurant_id backfilled) for audit; the live balance is the
-- cached current_balance column, which is what we zero. Order-derived stats (total_spent,
-- order_count) and the timestamps/claim flags are left intact — they re-derive from the preserved
-- order history.
--
-- Runs on Postgres (UPDATE ... FROM is Postgres syntax); the H2 test suite has flyway.enabled=false,
-- so this is NOT exercised by tests — validate on a Postgres copy (and back up) before deploy.

-- 1) Add the tenant column (nullable first, for backfill).
ALTER TABLE customer_loyalty   ADD COLUMN IF NOT EXISTS restaurant_id BIGINT;
ALTER TABLE bonus_transactions ADD COLUMN IF NOT EXISTS restaurant_id BIGINT;
ALTER TABLE wallet_top_ups     ADD COLUMN IF NOT EXISTS restaurant_id BIGINT;
ALTER TABLE tier_history       ADD COLUMN IF NOT EXISTS restaurant_id BIGINT;

-- 2) Backfill from the owning customer / parent loyalty row.
UPDATE customer_loyalty cl   SET restaurant_id = c.restaurant_id
    FROM customers c          WHERE cl.customer_id = c.id;
UPDATE wallet_top_ups w      SET restaurant_id = c.restaurant_id
    FROM customers c          WHERE w.customer_id = c.id;
UPDATE bonus_transactions bt SET restaurant_id = cl.restaurant_id
    FROM customer_loyalty cl  WHERE bt.customer_loyalty_id = cl.id;
UPDATE tier_history th       SET restaurant_id = cl.restaurant_id
    FROM customer_loyalty cl  WHERE th.customer_loyalty_id = cl.id;

-- 3) Zero out all balances — every (customer, restaurant) starts fresh (product decision). tier_id
--    is reset so tier standing re-earns per restaurant; order-derived stats are left to re-derive.
UPDATE customer_loyalty SET current_balance = 0, lifetime_earned = 0, lifetime_spent = 0, tier_id = NULL;

-- 4) Enforce NOT NULL (every row backfilled; customers.restaurant_id is NOT NULL since V150).
ALTER TABLE customer_loyalty   ALTER COLUMN restaurant_id SET NOT NULL;
ALTER TABLE bonus_transactions ALTER COLUMN restaurant_id SET NOT NULL;
ALTER TABLE wallet_top_ups     ALTER COLUMN restaurant_id SET NOT NULL;
ALTER TABLE tier_history       ALTER COLUMN restaurant_id SET NOT NULL;

-- 5) FKs + indexes.
ALTER TABLE customer_loyalty   ADD CONSTRAINT fk_customer_loyalty_restaurant   FOREIGN KEY (restaurant_id) REFERENCES restaurants(id);
ALTER TABLE bonus_transactions ADD CONSTRAINT fk_bonus_transactions_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurants(id);
ALTER TABLE wallet_top_ups     ADD CONSTRAINT fk_wallet_top_ups_restaurant     FOREIGN KEY (restaurant_id) REFERENCES restaurants(id);
ALTER TABLE tier_history       ADD CONSTRAINT fk_tier_history_restaurant       FOREIGN KEY (restaurant_id) REFERENCES restaurants(id);

CREATE INDEX IF NOT EXISTS idx_customer_loyalty_restaurant_id   ON customer_loyalty(restaurant_id);
CREATE INDEX IF NOT EXISTS idx_bonus_transactions_restaurant_id ON bonus_transactions(restaurant_id);
CREATE INDEX IF NOT EXISTS idx_wallet_top_ups_restaurant_id     ON wallet_top_ups(restaurant_id);
CREATE INDEX IF NOT EXISTS idx_tier_history_restaurant_id       ON tier_history(restaurant_id);
