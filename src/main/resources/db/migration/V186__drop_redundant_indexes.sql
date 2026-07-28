-- V186: Drop redundant single-column indexes that exactly duplicate a UNIQUE index.
--
-- Each index dropped below is a plain btree over a SINGLE column for which a UNIQUE index
-- already exists on that identical column. A UNIQUE index is a fully usable btree: Postgres
-- serves equality lookups, joins, and ORDER BY from it just as well as from the plain twin.
-- The second index is therefore dead weight — it adds write amplification (every INSERT /
-- UPDATE has to maintain it) and disk bloat for zero read benefit.
--
-- Scope was deliberately narrowed so this cleanup is provably safe, never a judgement call:
--   * Only single-column, plain (non-partial, non-expression) btree indexes qualify. An index
--     is dropped only when the covering UNIQUE index is likewise single-column and plain, over
--     the exact same column.
--   * customer_addresses' plain index is KEPT: its would-be cover is a PARTIAL unique index
--     (WHERE is_default), so it does not serve the general (non-default) lookup.
--   * waiter_performance's plain index is KEPT: it is (waiter_id, performance_date DESC) while
--     the unique index is ASC. Postgres can scan an ASC index backwards, but sort-direction
--     divergence is out of scope for a "provably redundant" drop, so it stays.
--
-- DROP INDEX IF EXISTS is idempotent: applying this against a database where an operator has
-- already removed one of these by hand is a no-op, not an error. Dropping the plain index never
-- touches the UNIQUE constraint it duplicates, so uniqueness enforcement is fully preserved.

DROP INDEX IF EXISTS idx_bonus_transactions_idempotency_key;  -- bonus_transactions(idempotency_key), covered by UNIQUE bonus_transactions_idempotency_key_key
DROP INDEX IF EXISTS idx_consumer_sessions_refresh_token;  -- consumer_sessions(refresh_token), covered by UNIQUE consumer_sessions_refresh_token_key
DROP INDEX IF EXISTS idx_consumer_sessions_session_token;  -- consumer_sessions(session_token), covered by UNIQUE consumer_sessions_session_token_key
DROP INDEX IF EXISTS idx_coupon_codes_code;  -- coupon_codes(code), covered by UNIQUE coupon_codes_code_key
DROP INDEX IF EXISTS idx_courier_profiles_user_id;  -- courier_profiles(user_id), covered by UNIQUE courier_profiles_user_id_key
DROP INDEX IF EXISTS idx_courier_wallets_courier_id;  -- courier_wallets(courier_profile_id), covered by UNIQUE courier_wallets_courier_profile_id_key
DROP INDEX IF EXISTS idx_customer_loyalty_customer_id;  -- customer_loyalty(customer_id), covered by UNIQUE customer_loyalty_customer_id_key
DROP INDEX IF EXISTS idx_customers_qr_code;  -- customers(qr_code), covered by UNIQUE customers_qr_code_unique
DROP INDEX IF EXISTS idx_daily_order_sequences_date;  -- daily_order_sequences(date), covered by UNIQUE daily_order_sequences_date_key
DROP INDEX IF EXISTS idx_purchase_order_number;  -- financial_purchase_orders(po_number), covered by UNIQUE financial_purchase_orders_po_number_key
DROP INDEX IF EXISTS idx_idempotency_key;  -- idempotency_keys(idempotency_key), covered by UNIQUE idempotency_keys_idempotency_key_key
DROP INDEX IF EXISTS idx_ingredients_name;  -- ingredients(name), covered by UNIQUE ingredients_name_unique
DROP INDEX IF EXISTS idx_kitchen_orders_order_id;  -- kitchen_orders(order_id), covered by UNIQUE kitchen_orders_order_id_key
DROP INDEX IF EXISTS idx_orders_order_number;  -- orders(order_number), covered by UNIQUE orders_order_number_key
DROP INDEX IF EXISTS idx_owner_telegram_subscribers_telegram_user_id;  -- owner_telegram_subscribers(telegram_user_id), covered by UNIQUE owner_telegram_subscribers_telegram_user_id_key
DROP INDEX IF EXISTS idx_push_subscriptions_endpoint;  -- push_subscriptions(endpoint), covered by UNIQUE uq_push_subscription_endpoint
DROP INDEX IF EXISTS idx_qr_codes_code;  -- qr_codes(code), covered by UNIQUE qr_codes_code_key
DROP INDEX IF EXISTS idx_receipt_template_restaurant;  -- receipt_templates(restaurant_id), covered by UNIQUE receipt_templates_restaurant_id_key
DROP INDEX IF EXISTS idx_referral_codes_code;  -- referral_codes(code), covered by UNIQUE referral_codes_code_key
DROP INDEX IF EXISTS idx_referral_settings_restaurant;  -- referral_settings(restaurant_id), covered by UNIQUE referral_settings_restaurant_id_key
DROP INDEX IF EXISTS idx_reservations_confirmation_code;  -- reservations(confirmation_code), covered by UNIQUE reservations_confirmation_code_key
DROP INDEX IF EXISTS idx_self_service_orders_order;  -- self_service_orders(order_id), covered by UNIQUE self_service_orders_order_id_key
DROP INDEX IF EXISTS idx_self_service_sessions_token;  -- self_service_sessions(session_token), covered by UNIQUE self_service_sessions_session_token_key
DROP INDEX IF EXISTS idx_users_email;  -- users(email), covered by UNIQUE users_email_key
