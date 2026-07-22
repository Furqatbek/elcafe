-- Add registration data fields to otp_codes table
ALTER TABLE otp_codes ADD COLUMN first_name VARCHAR(100);
ALTER TABLE otp_codes ADD COLUMN last_name VARCHAR(100);
ALTER TABLE otp_codes ADD COLUMN birth_date DATE;
ALTER TABLE otp_codes ADD COLUMN registration_source VARCHAR(50);
ALTER TABLE otp_codes ADD COLUMN language VARCHAR(10);
