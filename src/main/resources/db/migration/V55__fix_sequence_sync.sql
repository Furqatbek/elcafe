-- Fix sequence synchronization issues
-- This happens when data is inserted with explicit IDs and the sequence isn't updated

-- Fix customers sequence
SELECT setval(
    pg_get_serial_sequence('customers', 'id'),
    COALESCE((SELECT MAX(id) FROM customers), 0) + 1,
    false
);

-- Fix orders sequence
SELECT setval(
    pg_get_serial_sequence('orders', 'id'),
    COALESCE((SELECT MAX(id) FROM orders), 0) + 1,
    false
);

-- Fix order_items sequence
SELECT setval(
    pg_get_serial_sequence('order_items', 'id'),
    COALESCE((SELECT MAX(id) FROM order_items), 0) + 1,
    false
);

-- Fix products sequence
SELECT setval(
    pg_get_serial_sequence('products', 'id'),
    COALESCE((SELECT MAX(id) FROM products), 0) + 1,
    false
);

-- Fix restaurants sequence
SELECT setval(
    pg_get_serial_sequence('restaurants', 'id'),
    COALESCE((SELECT MAX(id) FROM restaurants), 0) + 1,
    false
);

-- Fix restaurant_tables sequence
SELECT setval(
    pg_get_serial_sequence('restaurant_tables', 'id'),
    COALESCE((SELECT MAX(id) FROM restaurant_tables), 0) + 1,
    false
);

-- Fix users sequence
SELECT setval(
    pg_get_serial_sequence('users', 'id'),
    COALESCE((SELECT MAX(id) FROM users), 0) + 1,
    false
);

-- Fix waiters sequence
SELECT setval(
    pg_get_serial_sequence('waiters', 'id'),
    COALESCE((SELECT MAX(id) FROM waiters), 0) + 1,
    false
);

-- Fix payments sequence
SELECT setval(
    pg_get_serial_sequence('payments', 'id'),
    COALESCE((SELECT MAX(id) FROM payments), 0) + 1,
    false
);
