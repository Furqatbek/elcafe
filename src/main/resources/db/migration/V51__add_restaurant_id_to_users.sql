-- Add restaurant_id column to users table
ALTER TABLE users ADD COLUMN restaurant_id BIGINT;

-- Add foreign key constraint (optional, depends on your needs)
ALTER TABLE users ADD CONSTRAINT fk_users_restaurant
    FOREIGN KEY (restaurant_id) REFERENCES restaurants(id) ON DELETE SET NULL;

-- Create index for better query performance
CREATE INDEX idx_users_restaurant_id ON users(restaurant_id);

-- Set default restaurant for existing users (set to restaurant ID 1 if it exists)
-- This ensures existing operator/admin users can access the financial reports
UPDATE users SET restaurant_id = 1 WHERE restaurant_id IS NULL AND role IN ('ADMIN', 'OPERATOR', 'MANAGER');
