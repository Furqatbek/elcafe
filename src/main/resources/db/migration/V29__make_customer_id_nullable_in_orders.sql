-- Make customer_id nullable in orders table to support waiter orders without customer
ALTER TABLE orders ALTER COLUMN customer_id DROP NOT NULL;
