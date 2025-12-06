-- Add customer profile fields for enhanced consumer experience
-- Supports birthDate, language preference, and registration source tracking

-- Add birthDate for customer demographics and marketing
ALTER TABLE customers ADD COLUMN IF NOT EXISTS birth_date DATE;

-- Add language preference for localized customer experience
ALTER TABLE customers ADD COLUMN IF NOT EXISTS language VARCHAR(10);

-- Add registration source to track how customers found the platform
ALTER TABLE customers ADD COLUMN IF NOT EXISTS registration_source VARCHAR(50);

-- Add check constraint for language codes
ALTER TABLE customers DROP CONSTRAINT IF EXISTS chk_customer_language;
ALTER TABLE customers ADD CONSTRAINT chk_customer_language CHECK (
    language IS NULL OR
    language IN ('uz', 'ru', 'en', 'tr', 'ar')
);

-- Add check constraint for registration source
ALTER TABLE customers DROP CONSTRAINT IF EXISTS chk_registration_source;
ALTER TABLE customers ADD CONSTRAINT chk_registration_source CHECK (
    registration_source IS NULL OR
    registration_source IN ('WEB', 'MOBILE', 'TELEGRAM', 'FACEBOOK', 'INSTAGRAM', 'PHONE', 'REFERRAL', 'OTHER')
);

-- Create indexes for filtering and analytics
CREATE INDEX IF NOT EXISTS idx_customers_language ON customers(language);
CREATE INDEX IF NOT EXISTS idx_customers_registration_source ON customers(registration_source);
CREATE INDEX IF NOT EXISTS idx_customers_birth_date ON customers(birth_date);

-- Add comments
COMMENT ON COLUMN customers.birth_date IS 'Customer date of birth for demographics and birthday promotions';
COMMENT ON COLUMN customers.language IS 'Preferred language for customer communication: uz, ru, en, tr, ar';
COMMENT ON COLUMN customers.registration_source IS 'Channel through which customer registered: WEB, MOBILE, TELEGRAM, etc.';
