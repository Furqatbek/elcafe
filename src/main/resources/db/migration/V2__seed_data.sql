-- Insert default admin user (password: Admin123!)
INSERT INTO users (email, password, first_name, last_name, role, active, email_verified)
VALUES ('admin@elcafe.com', '$2a$10$9K7JZjFf1F8xYxB5yKxVXOqKzH6j7Ej7Pq9Wx7Zj0Zj7Pq9Wx7Zj0', 'Admin', 'User', 'ADMIN', TRUE, TRUE);

-- Insert default operator user (password: Operator123!)
INSERT INTO users (email, password, first_name, last_name, role, active, email_verified)
VALUES ('operator@elcafe.com', '$2a$10$9K7JZjFf1F8xYxB5yKxVXOqKzH6j7Ej7Pq9Wx7Zj0Zj7Pq9Wx7Zj0', 'Operator', 'User', 'OPERATOR', TRUE, TRUE);

-- Insert sample restaurant
INSERT INTO restaurants (name, description, address, city, state, zip_code, country, phone, email, active, accepting_orders, delivery_fee, estimated_delivery_time_minutes)
VALUES ('El Cafe', 'Best coffee and food in town', '123 Main Street', 'New York', 'NY', '10001', 'USA', '+1234567890', 'info@elcafe.com', TRUE, TRUE, 5.00, 30);

-- Insert business hours for the sample restaurant
INSERT INTO business_hours (restaurant_id, day_of_week, open_time, close_time, closed)
VALUES
    (1, 'MONDAY', '09:00:00', '22:00:00', FALSE),
    (1, 'TUESDAY', '09:00:00', '22:00:00', FALSE),
    (1, 'WEDNESDAY', '09:00:00', '22:00:00', FALSE),
    (1, 'THURSDAY', '09:00:00', '22:00:00', FALSE),
    (1, 'FRIDAY', '09:00:00', '23:00:00', FALSE),
    (1, 'SATURDAY', '10:00:00', '23:00:00', FALSE),
    (1, 'SUNDAY', '10:00:00', '21:00:00', FALSE);
