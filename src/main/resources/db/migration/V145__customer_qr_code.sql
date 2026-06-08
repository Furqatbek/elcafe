-- Add a stable per-customer QR identifier so cashiers can attach a customer
-- to an in-flight POS order by scanning their loyalty QR code.
ALTER TABLE customers
    ADD COLUMN qr_code VARCHAR(40);

UPDATE customers
SET qr_code = 'CST-' || upper(substr(md5(random()::text || id::text), 1, 12))
WHERE qr_code IS NULL;

ALTER TABLE customers
    ALTER COLUMN qr_code SET NOT NULL,
    ADD CONSTRAINT customers_qr_code_unique UNIQUE (qr_code);

CREATE INDEX idx_customers_qr_code ON customers (qr_code);
