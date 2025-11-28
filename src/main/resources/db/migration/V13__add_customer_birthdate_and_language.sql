-- Add birth_date and language fields to customers table
ALTER TABLE customers ADD COLUMN birth_date DATE;
ALTER TABLE customers ADD COLUMN language VARCHAR(10);

-- Create index for language filtering
CREATE INDEX idx_customers_language ON customers(language);
