-- Add RESERVATION and QR_ORDER to registration_source constraint
-- These values are used when customers are created via reservations or QR code orders

-- Drop the existing constraint
ALTER TABLE customers DROP CONSTRAINT IF EXISTS chk_registration_source;

-- Add the updated constraint with RESERVATION and QR_ORDER
ALTER TABLE customers ADD CONSTRAINT chk_registration_source CHECK (
    registration_source IS NULL OR
    registration_source IN ('TELEGRAM_BOT', 'WEBSITE', 'ADMIN_PANEL', 'MOBILE_APP', 'PHONE_CALL', 'WALK_IN', 'RESERVATION', 'QR_ORDER', 'OTHER')
);

-- Update comment
COMMENT ON COLUMN customers.registration_source IS 'Channel through which customer registered: TELEGRAM_BOT, WEBSITE, ADMIN_PANEL, MOBILE_APP, PHONE_CALL, WALK_IN, RESERVATION, QR_ORDER, OTHER';
