-- Add TAKEAWAY QR code for self-service ordering on print receipts
-- This QR code allows customers to scan and order online for takeaway

INSERT INTO qr_codes (
    restaurant_id,
    code,
    short_url,
    name,
    description,
    qr_type,
    is_active,
    scan_count,
    created_at,
    updated_at
) VALUES (
    1,
    'TAKEAWAY',
    'https://jangirovs.uz/order/menu/1/TAKEAWAY',
    'Olib ketish buyurtmasi',
    'QR code for takeaway orders - printed on receipts',
    'TAKEAWAY',
    true,
    0,
    NOW(),
    NOW()
) ON CONFLICT (code) DO NOTHING;
