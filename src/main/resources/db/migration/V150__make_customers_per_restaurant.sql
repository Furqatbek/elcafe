-- V150: Make customers tenant-scoped (per-restaurant identity).
--
-- Until now a customer was a GLOBAL identity: one row, shared across every restaurant it ever
-- ordered from. That leaked PII across tenants (admin customer lists, search-by-phone) and made the
-- §3.4 Hibernate @Filter backstop unusable for customers, because a customer row has no single
-- restaurant to scope to. This migration gives every customer a restaurant_id and fragments any
-- customer that spans multiple restaurants into one row per restaurant, so customers can be
-- @Filter-scoped like waiters (§3.6) and matched per-restaurant at login.
--
-- FRAGMENTATION MODEL (decided with product):
--   * A customer's PRIMARY restaurant is the one it has placed the most orders at (ties -> lowest
--     restaurant id). The original row is KEPT for the primary restaurant and retains ALL
--     "global per-customer" assets that have no restaurant_id of their own and therefore cannot be
--     split: loyalty balance, wallet top-ups, saved addresses, coupons, push/telegram/instagram
--     subscriptions, consumer sessions, promotion usage, milestone redemptions. Nothing is
--     duplicated, so no balance or point total is ever inflated.
--   * For every OTHER restaurant the customer has ordered at, a NEW "shadow" customer row is created
--     (same PII, fresh qr_code) and only that restaurant's restaurant-scoped rows are repointed to
--     it. The shadow starts with no loyalty/wallet/etc. — i.e. a fresh per-restaurant relationship,
--     which is exactly the per-restaurant semantics this change establishes.
--
-- The 8 restaurant-scoped tables below (verified to carry their own restaurant_id) are repointed
-- per-row by restaurant. The 11 global customer tables (no restaurant_id) are intentionally left on
-- the original/primary row.
--
-- UNIQUENESS: email was globally UNIQUE and phone was effectively unique (app-level findByPhone).
-- Both become per-restaurant composite uniques so the same person can exist under two restaurants.
-- The migration runs in one transaction (Postgres transactional DDL): if pre-existing data violates
-- a composite unique (e.g. two distinct rows share a phone within one restaurant) the whole
-- migration rolls back, surfacing the bad data rather than allowing ambiguous per-restaurant login.

-- 1) Tenant column (nullable for now; populated below, then made NOT NULL).
ALTER TABLE customers ADD COLUMN IF NOT EXISTS restaurant_id BIGINT;

-- 2) Drop the global email uniqueness BEFORE inserting shadow rows that reuse an email under a
--    different restaurant. (V1 declared `email ... UNIQUE`, which Postgres named customers_email_key.)
ALTER TABLE customers DROP CONSTRAINT IF EXISTS customers_email_key;

-- 3) Rank each customer's restaurants by order volume. rn = 1 is the primary; rn > 1 are the
--    restaurants that need a shadow row. Orders with a null customer/restaurant are ignored.
CREATE TEMP TABLE _cust_rest ON COMMIT DROP AS
SELECT customer_id,
       restaurant_id,
       ROW_NUMBER() OVER (PARTITION BY customer_id ORDER BY cnt DESC, restaurant_id) AS rn
FROM (
    SELECT customer_id, restaurant_id, COUNT(*) AS cnt
    FROM orders
    WHERE customer_id IS NOT NULL AND restaurant_id IS NOT NULL
    GROUP BY customer_id, restaurant_id
) per_restaurant;

-- 4) Assign each existing customer to its primary restaurant.
UPDATE customers c
SET restaurant_id = r.restaurant_id
FROM _cust_rest r
WHERE r.customer_id = c.id AND r.rn = 1 AND c.restaurant_id IS NULL;

-- 5) Allocate a stable new id for every (customer, secondary restaurant) pair up front, so the
--    repoint UPDATEs below are a simple, order-independent join on the mapping.
CREATE TEMP TABLE _cust_frag ON COMMIT DROP AS
SELECT customer_id AS original_customer_id,
       restaurant_id,
       nextval(pg_get_serial_sequence('customers', 'id')) AS new_customer_id
FROM _cust_rest
WHERE rn > 1;

-- 6) Materialise the shadow customer rows (copy PII; fresh unique qr_code; scoped to the secondary
--    restaurant). Global assets are NOT copied — they stay on the original/primary row.
INSERT INTO customers (
    id, restaurant_id, qr_code, first_name, last_name, email, phone,
    default_address, city, state, zip_code, notes, tags, active, birth_date, language,
    registration_source, is_tax_exempt, tax_exemption_type_id, tax_exemption_number,
    tax_exemption_expires_at, created_at, updated_at
)
SELECT f.new_customer_id,
       f.restaurant_id,
       'CST-' || upper(substr(md5(random()::text || f.new_customer_id::text), 1, 12)),
       c.first_name, c.last_name, c.email, c.phone,
       c.default_address, c.city, c.state, c.zip_code, c.notes, c.tags, c.active, c.birth_date,
       c.language, c.registration_source, c.is_tax_exempt, c.tax_exemption_type_id,
       c.tax_exemption_number, c.tax_exemption_expires_at, c.created_at, CURRENT_TIMESTAMP
FROM _cust_frag f
JOIN customers c ON c.id = f.original_customer_id;

-- 7) Repoint the restaurant-scoped rows of each secondary restaurant to its shadow customer.
--    Keyed on the row's own restaurant_id, so multi-customer links (referrals) resolve to the right
--    shadow on BOTH sides regardless of update order.
UPDATE orders t SET customer_id = f.new_customer_id
    FROM _cust_frag f WHERE t.customer_id = f.original_customer_id AND t.restaurant_id = f.restaurant_id;

UPDATE gift_cards t SET purchased_by_customer_id = f.new_customer_id
    FROM _cust_frag f WHERE t.purchased_by_customer_id = f.original_customer_id AND t.restaurant_id = f.restaurant_id;

UPDATE referral_codes t SET customer_id = f.new_customer_id
    FROM _cust_frag f WHERE t.customer_id = f.original_customer_id AND t.restaurant_id = f.restaurant_id;

UPDATE referrals t SET referrer_id = f.new_customer_id
    FROM _cust_frag f WHERE t.referrer_id = f.original_customer_id AND t.restaurant_id = f.restaurant_id;

UPDATE referrals t SET referee_id = f.new_customer_id
    FROM _cust_frag f WHERE t.referee_id = f.original_customer_id AND t.restaurant_id = f.restaurant_id;

UPDATE reservations t SET customer_id = f.new_customer_id
    FROM _cust_frag f WHERE t.customer_id = f.original_customer_id AND t.restaurant_id = f.restaurant_id;

UPDATE reviews t SET customer_id = f.new_customer_id
    FROM _cust_frag f WHERE t.customer_id = f.original_customer_id AND t.restaurant_id = f.restaurant_id;

UPDATE self_service_sessions t SET customer_id = f.new_customer_id
    FROM _cust_frag f WHERE t.customer_id = f.original_customer_id AND t.restaurant_id = f.restaurant_id;

UPDATE tax_exemption_logs t SET customer_id = f.new_customer_id
    FROM _cust_frag f WHERE t.customer_id = f.original_customer_id AND t.restaurant_id = f.restaurant_id;

-- 8) Customers with no qualifying orders (no activity to derive a tenant from) fall back to the
--    oldest restaurant — correct for the common single-restaurant install; an admin can reassign in
--    a multi-restaurant one. Nothing scoped references them yet, so this is safe.
UPDATE customers
SET restaurant_id = (SELECT id FROM restaurants ORDER BY id LIMIT 1)
WHERE restaurant_id IS NULL;

-- 9) Enforce the tenant column + per-restaurant identity.
ALTER TABLE customers ALTER COLUMN restaurant_id SET NOT NULL;
ALTER TABLE customers
    ADD CONSTRAINT fk_customers_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurants(id);
CREATE INDEX IF NOT EXISTS idx_customers_restaurant ON customers(restaurant_id);

-- 10) Per-restaurant uniqueness. NULL email is allowed many times (unique ignores nulls), matching
--     the old global behaviour for customers without an email. qr_code stays GLOBALLY unique
--     (customers_qr_code_unique from an earlier migration) — it is a random, cross-restaurant handle.
ALTER TABLE customers ADD CONSTRAINT uq_customers_restaurant_email UNIQUE (restaurant_id, email);
ALTER TABLE customers ADD CONSTRAINT uq_customers_restaurant_phone UNIQUE (restaurant_id, phone);
