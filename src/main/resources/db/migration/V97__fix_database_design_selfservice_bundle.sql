-- V97: Fix database design issues in SelfService and Bundle modules
-- Addresses missing indexes, precision mismatches, and cascade delete risks

-- ============================================================
-- SELFSERVICE MODULE FIXES
-- ============================================================

-- 1. Add composite index for duplicate detection in cart items
-- This speeds up the common query: find existing item with same product+variant in session
CREATE INDEX IF NOT EXISTS idx_cart_items_session_product_variant
    ON self_service_cart_items(session_id, product_id, variant_id);

-- 2. Add index on session_id for self_service_orders for faster session-based lookups
CREATE INDEX IF NOT EXISTS idx_self_service_orders_session_id
    ON self_service_orders(session_id)
    WHERE session_id IS NOT NULL;

-- ============================================================
-- BUNDLE MODULE FIXES
-- ============================================================

-- 3. Add missing index on bundle_items.product_id for product lookups
CREATE INDEX IF NOT EXISTS idx_bundle_items_product_id
    ON bundle_items(product_id)
    WHERE product_id IS NOT NULL;

-- 4. Add missing index on bundle_options.product_id for product lookups
CREATE INDEX IF NOT EXISTS idx_bundle_options_product_id
    ON bundle_options(product_id)
    WHERE product_id IS NOT NULL;

-- 5. Add combined index on bundles(restaurant_id, active) for filtered queries
CREATE INDEX IF NOT EXISTS idx_bundles_restaurant_active
    ON bundles(restaurant_id, active);

-- 6. Fix savings_percent precision to match bundle_price precision
-- DECIMAL(5,2) can only hold up to 999.99, which should be enough for percentages
-- but for consistency and calculation safety, increase to DECIMAL(10,2)
ALTER TABLE bundles
    ALTER COLUMN savings_percent TYPE DECIMAL(10, 2);

-- 7. Fix cascade delete risk: Change ON DELETE SET NULL to ON DELETE RESTRICT
-- for product references in bundle_items and bundle_options
-- This prevents orphaned bundle items when a product is deleted

-- For bundle_items.product_id
ALTER TABLE bundle_items
    DROP CONSTRAINT IF EXISTS bundle_items_product_id_fkey;

ALTER TABLE bundle_items
    ADD CONSTRAINT bundle_items_product_id_fkey
    FOREIGN KEY (product_id)
    REFERENCES products(id)
    ON DELETE RESTRICT;

-- For bundle_options.product_id
ALTER TABLE bundle_options
    DROP CONSTRAINT IF EXISTS bundle_options_product_id_fkey;

ALTER TABLE bundle_options
    ADD CONSTRAINT bundle_options_product_id_fkey
    FOREIGN KEY (product_id)
    REFERENCES products(id)
    ON DELETE RESTRICT;

-- ============================================================
-- COMMENTS
-- ============================================================

COMMENT ON INDEX idx_cart_items_session_product_variant IS
    'Composite index for fast duplicate detection when adding items to cart';

COMMENT ON INDEX idx_bundle_items_product_id IS
    'Index for product lookups in bundle items - speeds up product deletion checks';

COMMENT ON INDEX idx_bundle_options_product_id IS
    'Index for product lookups in bundle options - speeds up product deletion checks';

COMMENT ON INDEX idx_bundles_restaurant_active IS
    'Combined index for common query pattern: active bundles by restaurant';
