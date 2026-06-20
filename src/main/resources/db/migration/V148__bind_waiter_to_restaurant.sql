-- V148: Bind waiters to a restaurant (Phase 0 §3.6).
--
-- The core Waiter entity had no restaurant_id, so waiters were global: listings leaked them across
-- tenants and the §3.4 Hibernate backstop could not scope them. This adds waiters.restaurant_id and
-- backfills it from each waiter's own activity, so waiter listings and the tenant @Filter can
-- include them.
--
-- The column is left NULLABLE on purpose: in a multi-restaurant install a waiter with no derivable
-- activity may stay unassigned until an admin sets it. A later migration can enforce NOT NULL once
-- every row is confirmed populated. Entities only ever reference a waiter within the same
-- restaurant, so a correctly backfilled value keeps the @Filter backstop safe (no cross-tenant
-- association fetches).

ALTER TABLE waiters ADD COLUMN IF NOT EXISTS restaurant_id BIGINT;

-- 1) Primary signal: the restaurant where the waiter has taken the most orders.
UPDATE waiters w
SET restaurant_id = ranked.restaurant_id
FROM (
    SELECT waiter_id, restaurant_id
    FROM (
        SELECT o.waiter_id,
               o.restaurant_id,
               ROW_NUMBER() OVER (PARTITION BY o.waiter_id
                                  ORDER BY COUNT(*) DESC, o.restaurant_id) AS rn
        FROM orders o
        WHERE o.waiter_id IS NOT NULL AND o.restaurant_id IS NOT NULL
        GROUP BY o.waiter_id, o.restaurant_id
    ) per_restaurant
    WHERE rn = 1
) ranked
WHERE w.id = ranked.waiter_id AND w.restaurant_id IS NULL;

-- 2) Fallback for waiters with performance rows but no orders.
UPDATE waiters w
SET restaurant_id = sub.restaurant_id
FROM (
    SELECT DISTINCT ON (waiter_id) waiter_id, restaurant_id
    FROM waiter_performance
    WHERE restaurant_id IS NOT NULL
    ORDER BY waiter_id, restaurant_id
) sub
WHERE w.id = sub.waiter_id AND w.restaurant_id IS NULL;

-- 3) Best-effort final fallback: activity-less waiters go to the oldest restaurant (correct for the
--    common single-restaurant install; an admin can reassign in a multi-restaurant one). Nothing
--    references these waiters, so this cannot break association fetches under the @Filter backstop.
UPDATE waiters
SET restaurant_id = (SELECT id FROM restaurants ORDER BY id LIMIT 1)
WHERE restaurant_id IS NULL;

-- Foreign key + index.
ALTER TABLE waiters
    ADD CONSTRAINT fk_waiters_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurants(id);
CREATE INDEX IF NOT EXISTS idx_waiters_restaurant ON waiters(restaurant_id);
