-- Insert default admin user (password: $lL%UJxdnR$9G^$E)
INSERT INTO users (email, password, first_name, last_name, role, active, email_verified)
VALUES ('admin@elcafe.com', '$2b$10$tk6LZVsLMogk1MKi0zAkFeYto7dM0SBmljIkA.Lc6BO8wJGLzefqG', 'Admin', 'User', 'ADMIN', TRUE, TRUE);

-- Insert default operator user (password: F8fLj8bi1yGBWqpB)
INSERT INTO users (email, password, first_name, last_name, role, active, email_verified)
VALUES ('operator@elcafe.com', '$2b$10$ROuAr99Uk52NYdO5J/JyCe0C/8axTGDCvROewQgFICjVG3s8tDpLq', 'Operator', 'User', 'OPERATOR', TRUE, TRUE);

-- Insert sample restaurant
INSERT INTO restaurants (name, description, address, city, state, zip_code, country, phone, email, active, accepting_orders, delivery_fee, estimated_delivery_time_minutes)
VALUES ('El Cafe', 'Best coffee and food in town', '123 Main Street', 'New York', 'NY', '10001', 'USA', '+1234567890', 'info@elcafe.com', TRUE, TRUE, 5.00, 30);

-- Business hours can now be configured through the Working Hours interface
