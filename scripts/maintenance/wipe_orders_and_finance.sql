-- One-off maintenance script: permanently deletes ALL orders and ALL financial data.
-- Keeps: menu, restaurants, tables, users, waiters, customers, suppliers, ingredients and current stock levels.
-- NOT a Flyway migration. Run manually, only after taking a backup:
--   psql -v ON_ERROR_STOP=1 --single-transaction -f wipe_orders_and_finance.sql
-- Any error rolls back everything; no partial wipe is possible.

\echo '--- Before ---'
SELECT (SELECT count(*) FROM orders)                    AS orders,
       (SELECT count(*) FROM payments)                  AS payments,
       (SELECT count(*) FROM financial_transactions)    AS fin_transactions,
       (SELECT count(*) FROM financial_expenses)        AS expenses,
       (SELECT count(*) FROM financial_purchase_orders) AS purchase_orders,
       (SELECT count(*) FROM bonus_transactions)        AS bonus_tx;

-- Orders and every table that references them. No CASCADE on purpose:
-- if an unlisted table still references these, TRUNCATE fails and nothing is deleted.
TRUNCATE TABLE
    order_items,
    order_status_history,
    payments,
    delivery_info,
    kitchen_orders,
    order_events,
    notifications,
    bonus_transactions,
    orders,
    daily_order_sequences
RESTART IDENTITY;

-- Finance: ledger, expenses, payroll, purchase orders.
TRUNCATE TABLE
    financial_transactions,
    financial_journal_entries,
    financial_expenses,
    financial_payroll_entries,
    financial_purchase_order_items,
    financial_purchase_orders
RESTART IDENTITY;

UPDATE financial_accounts SET balance = 0;

-- Courier money tied to deliveries.
TRUNCATE TABLE courier_wallet_transactions, wallet_transactions, courier_bonus_fines RESTART IDENTITY;
UPDATE courier_wallets
SET balance = 0, total_earned = 0, total_withdrawn = 0, total_bonuses = 0, total_fines = 0;

-- Inventory: drop order-driven movement history, keep current stock quantities and batches.
DELETE FROM batch_consumptions WHERE order_id IS NOT NULL;
DELETE FROM inventory_transactions WHERE reference_type LIKE 'ORDER%';

-- Loyalty: keep customers, reset points and stats to a fresh start on the lowest tier.
TRUNCATE TABLE tier_history RESTART IDENTITY;
UPDATE customer_loyalty
SET current_balance = 0,
    lifetime_earned = 0,
    lifetime_spent = 0,
    total_spent = 0,
    order_count = 0,
    last_order_date = NULL,
    first_order_bonus_claimed = false,
    tier_id = (SELECT id FROM customer_tiers ORDER BY level ASC LIMIT 1);

-- Tables left occupied by the deleted orders.
UPDATE restaurant_tables SET status = 'AVAILABLE' WHERE status = 'OCCUPIED';
UPDATE tables SET status = 'FREE' WHERE status <> 'FREE';

\echo '--- After ---'
SELECT (SELECT count(*) FROM orders)                    AS orders,
       (SELECT count(*) FROM payments)                  AS payments,
       (SELECT count(*) FROM financial_transactions)    AS fin_transactions,
       (SELECT count(*) FROM financial_expenses)        AS expenses,
       (SELECT count(*) FROM financial_purchase_orders) AS purchase_orders,
       (SELECT count(*) FROM bonus_transactions)        AS bonus_tx,
       (SELECT coalesce(sum(balance), 0) FROM financial_accounts)   AS account_balance_sum,
       (SELECT coalesce(sum(current_balance), 0) FROM customer_loyalty) AS loyalty_points_sum;
