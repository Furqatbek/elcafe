-- Insert default admin user (password: $lL%UJxdnR$9G^$E)
INSERT INTO users (email, password, first_name, last_name, role, active, email_verified)
VALUES ('admin@lacasa.com', '$2b$10$tk6LZVsLMogk1MKi0zAkFeYto7dM0SBmljIkA.Lc6BO8wJGLzefqG', 'Admin', 'User', 'ADMIN', TRUE, TRUE);

-- Insert default operator user (password: F8fLj8bi1yGBWqpB)
INSERT INTO users (email, password, first_name, last_name, role, active, email_verified)
VALUES ('operator@lacasa.com', '$2b$10$ROuAr99Uk52NYdO5J/JyCe0C/8axTGDCvROewQgFICjVG3s8tDpLq', 'Operator', 'User', 'OPERATOR', TRUE, TRUE);

-- Insert sample restaurant
INSERT INTO restaurants (name, description, address, city, state, zip_code, country, phone, email, active, accepting_orders, delivery_fee, estimated_delivery_time_minutes)
VALUES ('LaCasa', 'Best coffee and food in town', 'Hazorasp, Jalilbek toyhona', 'Xorazm', 'UZB', '10001', 'UZB', '+998 97 421 89 89', 'info@lacasa.uz', TRUE, TRUE, 5.00, 30);

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
