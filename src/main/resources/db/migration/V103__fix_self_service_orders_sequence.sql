-- Fix self_service_orders sequence synchronization issue
-- This happens when data is inserted with explicit IDs and the sequence isn't updated

-- Fix self_service_orders sequence
SELECT setval(
    pg_get_serial_sequence('self_service_orders', 'id'),
    COALESCE((SELECT MAX(id) FROM self_service_orders), 0) + 1,
    false
);

-- Fix self_service_sessions sequence
SELECT setval(
    pg_get_serial_sequence('self_service_sessions', 'id'),
    COALESCE((SELECT MAX(id) FROM self_service_sessions), 0) + 1,
    false
);

-- Fix self_service_cart_items sequence
SELECT setval(
    pg_get_serial_sequence('self_service_cart_items', 'id'),
    COALESCE((SELECT MAX(id) FROM self_service_cart_items), 0) + 1,
    false
);

-- Fix qr_codes sequence
SELECT setval(
    pg_get_serial_sequence('qr_codes', 'id'),
    COALESCE((SELECT MAX(id) FROM qr_codes), 0) + 1,
    false
);

-- Fix self_service_settings sequence
SELECT setval(
    pg_get_serial_sequence('self_service_settings', 'id'),
    COALESCE((SELECT MAX(id) FROM self_service_settings), 0) + 1,
    false
);
