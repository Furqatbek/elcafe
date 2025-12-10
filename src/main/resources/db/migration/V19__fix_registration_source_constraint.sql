-- Fix registration_source constraint to match RegistrationSource enum
-- Updates constraint values from WEB, MOBILE, etc. to TELEGRAM_BOT, WEBSITE, etc.

-- First, normalize existing registration_source values to uppercase
UPDATE customers SET registration_source = UPPER(registration_source) WHERE registration_source IS NOT NULL;

-- Map old values to new enum values
UPDATE customers SET registration_source = 'WEBSITE' WHERE registration_source = 'WEB';
UPDATE customers SET registration_source = 'MOBILE_APP' WHERE registration_source = 'MOBILE';
UPDATE customers SET registration_source = 'TELEGRAM_BOT' WHERE registration_source = 'TELEGRAM';
UPDATE customers SET registration_source = 'OTHER' WHERE registration_source = 'FACEBOOK';
UPDATE customers SET registration_source = 'OTHER' WHERE registration_source = 'INSTAGRAM';
UPDATE customers SET registration_source = 'PHONE_CALL' WHERE registration_source = 'PHONE';
UPDATE customers SET registration_source = 'OTHER' WHERE registration_source = 'REFERRAL';

-- Convert any remaining invalid values to 'OTHER'
UPDATE customers SET registration_source = 'OTHER'
WHERE registration_source IS NOT NULL
  AND registration_source NOT IN ('TELEGRAM_BOT', 'WEBSITE', 'ADMIN_PANEL', 'MOBILE_APP', 'PHONE_CALL', 'WALK_IN', 'OTHER');

-- Drop old constraint
ALTER TABLE customers DROP CONSTRAINT IF EXISTS chk_registration_source;

-- Add new constraint with correct enum values
ALTER TABLE customers ADD CONSTRAINT chk_registration_source CHECK (
    registration_source IS NULL OR
    registration_source IN ('TELEGRAM_BOT', 'WEBSITE', 'ADMIN_PANEL', 'MOBILE_APP', 'PHONE_CALL', 'WALK_IN', 'OTHER')
);

-- Update column comment to reflect new values
COMMENT ON COLUMN customers.registration_source IS 'Channel through which customer registered: TELEGRAM_BOT, WEBSITE, ADMIN_PANEL, MOBILE_APP, PHONE_CALL, WALK_IN, OTHER';
