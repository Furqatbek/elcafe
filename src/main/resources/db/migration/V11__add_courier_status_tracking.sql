-- Migration: Add courier status tracking fields
-- Version: V11
-- Description: Adds fields to track courier online/offline status and delivery state

-- Add status tracking columns to courier_profiles
ALTER TABLE courier_profiles
ADD COLUMN IF NOT EXISTS is_online BOOLEAN NOT NULL DEFAULT FALSE,
ADD COLUMN IF NOT EXISTS current_status VARCHAR(50) NOT NULL DEFAULT 'OFFLINE',
ADD COLUMN IF NOT EXISTS last_seen_at TIMESTAMP,
ADD COLUMN IF NOT EXISTS last_location_update_at TIMESTAMP;

-- Create index for online courier queries
CREATE INDEX IF NOT EXISTS idx_courier_profiles_is_online ON courier_profiles(is_online);
CREATE INDEX IF NOT EXISTS idx_courier_profiles_status ON courier_profiles(current_status);
CREATE INDEX IF NOT EXISTS idx_courier_profiles_last_seen ON courier_profiles(last_seen_at);

-- Add comments for documentation
COMMENT ON COLUMN courier_profiles.is_online IS 'Whether courier is currently online and available to receive orders';
COMMENT ON COLUMN courier_profiles.current_status IS 'Current courier status: OFFLINE, ONLINE, ON_DELIVERY, BUSY';
COMMENT ON COLUMN courier_profiles.last_seen_at IS 'Last time courier was active (sent location update, accepted order, etc.)';
COMMENT ON COLUMN courier_profiles.last_location_update_at IS 'Last time courier sent GPS location update';
