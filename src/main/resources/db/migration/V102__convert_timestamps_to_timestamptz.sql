-- =====================================================
-- V102: Convert remaining timestamp columns to TIMESTAMP WITH TIME ZONE
-- Aligns database columns with entity OffsetDateTime types
-- to prevent Hibernate type conversion errors.
-- =====================================================

-- =====================================================
-- DELIVERY_INFO TABLE
-- =====================================================
ALTER TABLE delivery_info
    ALTER COLUMN pickup_time TYPE TIMESTAMP WITH TIME ZONE,
    ALTER COLUMN estimated_delivery_time TYPE TIMESTAMP WITH TIME ZONE,
    ALTER COLUMN delivery_time TYPE TIMESTAMP WITH TIME ZONE,
    ALTER COLUMN actual_delivery_time TYPE TIMESTAMP WITH TIME ZONE;

-- =====================================================
-- ORDER_STATUS_HISTORY TABLE
-- =====================================================
ALTER TABLE order_status_history
    ALTER COLUMN created_at TYPE TIMESTAMP WITH TIME ZONE;

-- =====================================================
-- ORDER_TABLES TABLE
-- =====================================================
ALTER TABLE order_tables
    ALTER COLUMN created_at TYPE TIMESTAMP WITH TIME ZONE;

-- =====================================================
-- ORDER_FINANCIAL_EVENTS TABLE
-- =====================================================
ALTER TABLE order_financial_events
    ALTER COLUMN created_at TYPE TIMESTAMP WITH TIME ZONE;

-- =====================================================
-- CUSTOMERS TABLE
-- =====================================================
ALTER TABLE customers
    ALTER COLUMN created_at TYPE TIMESTAMP WITH TIME ZONE,
    ALTER COLUMN updated_at TYPE TIMESTAMP WITH TIME ZONE;
