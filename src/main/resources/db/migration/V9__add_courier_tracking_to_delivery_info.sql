-- Migration: Add courier tracking columns to delivery_info table
-- Version: V9
-- Description: Adds courier information and delivery timing columns to support order tracking

-- Add courier tracking columns to delivery_info table
ALTER TABLE delivery_info ADD COLUMN IF NOT EXISTS courier_id BIGINT;
ALTER TABLE delivery_info ADD COLUMN IF NOT EXISTS courier_name VARCHAR(200);
ALTER TABLE delivery_info ADD COLUMN IF NOT EXISTS courier_phone VARCHAR(20);
ALTER TABLE delivery_info ADD COLUMN IF NOT EXISTS courier_provider_id VARCHAR(255);
ALTER TABLE delivery_info ADD COLUMN IF NOT EXISTS courier_tracking_id VARCHAR(255);
ALTER TABLE delivery_info ADD COLUMN IF NOT EXISTS pickup_time TIMESTAMP;
ALTER TABLE delivery_info ADD COLUMN IF NOT EXISTS estimated_delivery_time TIMESTAMP;
ALTER TABLE delivery_info ADD COLUMN IF NOT EXISTS delivery_time TIMESTAMP;
ALTER TABLE delivery_info ADD COLUMN IF NOT EXISTS actual_delivery_time TIMESTAMP;

-- Add index on courier_id for faster lookups
CREATE INDEX IF NOT EXISTS idx_delivery_info_courier_id ON delivery_info(courier_id);

-- Add comments for documentation
COMMENT ON COLUMN delivery_info.courier_id IS 'ID of the assigned courier';
COMMENT ON COLUMN delivery_info.courier_name IS 'Name of the assigned courier';
COMMENT ON COLUMN delivery_info.courier_phone IS 'Phone number of the assigned courier';
COMMENT ON COLUMN delivery_info.courier_provider_id IS 'External courier provider ID (if using third-party service)';
COMMENT ON COLUMN delivery_info.courier_tracking_id IS 'External tracking ID (if using third-party service)';
COMMENT ON COLUMN delivery_info.pickup_time IS 'Time when courier picked up the order';
COMMENT ON COLUMN delivery_info.estimated_delivery_time IS 'Estimated time of delivery';
COMMENT ON COLUMN delivery_info.delivery_time IS 'Actual time when order was delivered';
COMMENT ON COLUMN delivery_info.actual_delivery_time IS 'Actual delivery time (alternative field for compatibility)';
