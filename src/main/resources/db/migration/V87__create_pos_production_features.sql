-- =====================================================
-- POS Production Features Migration
-- Adds: Offline Mode, Cash Drawer, Shift Management,
--       Gift Cards, Barcode, Scale, Customer Display,
--       Tax Exemption
-- =====================================================

-- =====================================================
-- 1. OFFLINE MODE TABLES
-- =====================================================

-- Table to store orders created offline that need to be synced
CREATE TABLE offline_orders (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT NOT NULL REFERENCES restaurants(id),
    device_id VARCHAR(100) NOT NULL,
    client_order_id VARCHAR(100) NOT NULL, -- Client-generated UUID for deduplication
    order_data JSONB NOT NULL, -- Full order payload as JSON
    sync_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    sync_attempts INTEGER DEFAULT 0,
    last_sync_attempt TIMESTAMP WITH TIME ZONE,
    sync_error TEXT,
    synced_order_id BIGINT REFERENCES orders(id), -- Link to created order after sync
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    synced_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_offline_order_client_id UNIQUE (restaurant_id, device_id, client_order_id)
);

CREATE INDEX idx_offline_orders_sync_status ON offline_orders(sync_status);
CREATE INDEX idx_offline_orders_restaurant_device ON offline_orders(restaurant_id, device_id);
CREATE INDEX idx_offline_orders_created ON offline_orders(created_at);

-- Device registration for offline mode
CREATE TABLE pos_devices (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT NOT NULL REFERENCES restaurants(id),
    device_id VARCHAR(100) NOT NULL,
    device_name VARCHAR(200),
    device_type VARCHAR(50), -- TABLET, TERMINAL, MOBILE
    last_sync TIMESTAMP WITH TIME ZONE,
    last_heartbeat TIMESTAMP WITH TIME ZONE,
    offline_enabled BOOLEAN DEFAULT TRUE,
    is_active BOOLEAN DEFAULT TRUE,
    registered_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_pos_device UNIQUE (restaurant_id, device_id)
);

CREATE INDEX idx_pos_devices_restaurant ON pos_devices(restaurant_id);

-- =====================================================
-- 2. CASH DRAWER TABLES
-- =====================================================

-- Cash drawer configuration per terminal
CREATE TABLE cash_drawers (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT NOT NULL REFERENCES restaurants(id),
    device_id VARCHAR(100),
    drawer_name VARCHAR(100) NOT NULL,
    printer_name VARCHAR(200), -- Linked printer for drawer kick
    kick_command VARCHAR(50) DEFAULT 'ESC_P', -- ESC/POS command type
    expected_float DECIMAL(10,2) DEFAULT 0.00, -- Expected starting cash
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_cash_drawers_restaurant ON cash_drawers(restaurant_id);

-- Cash drawer operations log
CREATE TABLE cash_drawer_operations (
    id BIGSERIAL PRIMARY KEY,
    cash_drawer_id BIGINT NOT NULL REFERENCES cash_drawers(id),
    shift_id BIGINT, -- Reference to employee_shifts
    operation_type VARCHAR(50) NOT NULL, -- OPEN, CASH_IN, CASH_OUT, PAID_IN, PAID_OUT, DROP, PICKUP, CLOSE
    amount DECIMAL(10,2) NOT NULL,
    reason VARCHAR(500),
    operator_id BIGINT NOT NULL REFERENCES users(id),
    order_id BIGINT REFERENCES orders(id),
    payment_id BIGINT REFERENCES payments(id),
    notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_cash_drawer_ops_drawer ON cash_drawer_operations(cash_drawer_id);
CREATE INDEX idx_cash_drawer_ops_shift ON cash_drawer_operations(shift_id);
CREATE INDEX idx_cash_drawer_ops_created ON cash_drawer_operations(created_at);

-- =====================================================
-- 3. SHIFT MANAGEMENT TABLES
-- =====================================================

-- Employee shifts for time tracking and reconciliation
CREATE TABLE employee_shifts (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT NOT NULL REFERENCES restaurants(id),
    employee_id BIGINT NOT NULL REFERENCES users(id),
    waiter_id BIGINT REFERENCES waiters(id), -- Optional waiter link
    cash_drawer_id BIGINT REFERENCES cash_drawers(id),
    shift_date DATE NOT NULL,
    clock_in TIMESTAMP WITH TIME ZONE NOT NULL,
    clock_out TIMESTAMP WITH TIME ZONE,
    scheduled_start TIME,
    scheduled_end TIME,
    break_minutes INTEGER DEFAULT 0,
    overtime_minutes INTEGER DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, COMPLETED, APPROVED
    -- Opening counts
    opening_cash DECIMAL(10,2) DEFAULT 0.00,
    -- Closing counts
    closing_cash DECIMAL(10,2),
    expected_cash DECIMAL(10,2),
    cash_variance DECIMAL(10,2),
    -- Shift totals
    total_sales DECIMAL(12,2) DEFAULT 0.00,
    total_cash_sales DECIMAL(12,2) DEFAULT 0.00,
    total_card_sales DECIMAL(12,2) DEFAULT 0.00,
    total_tips DECIMAL(10,2) DEFAULT 0.00,
    total_orders INTEGER DEFAULT 0,
    total_refunds DECIMAL(10,2) DEFAULT 0.00,
    total_voids DECIMAL(10,2) DEFAULT 0.00,
    -- Approvals
    approved_by BIGINT REFERENCES users(id),
    approved_at TIMESTAMP WITH TIME ZONE,
    manager_notes TEXT,
    employee_notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_employee_shifts_restaurant ON employee_shifts(restaurant_id);
CREATE INDEX idx_employee_shifts_employee ON employee_shifts(employee_id);
CREATE INDEX idx_employee_shifts_date ON employee_shifts(shift_date);
CREATE INDEX idx_employee_shifts_status ON employee_shifts(status);

-- Break tracking within shifts
CREATE TABLE shift_breaks (
    id BIGSERIAL PRIMARY KEY,
    shift_id BIGINT NOT NULL REFERENCES employee_shifts(id) ON DELETE CASCADE,
    break_start TIMESTAMP WITH TIME ZONE NOT NULL,
    break_end TIMESTAMP WITH TIME ZONE,
    break_type VARCHAR(50) DEFAULT 'BREAK', -- BREAK, MEAL, OTHER
    is_paid BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_shift_breaks_shift ON shift_breaks(shift_id);

-- =====================================================
-- 4. GIFT CARDS TABLES
-- =====================================================

-- Gift card types/products
CREATE TABLE gift_card_types (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT NOT NULL REFERENCES restaurants(id),
    name VARCHAR(200) NOT NULL,
    description TEXT,
    fixed_amounts JSONB, -- Array of fixed amounts: [10, 25, 50, 100]
    min_amount DECIMAL(10,2),
    max_amount DECIMAL(10,2),
    is_custom_amount_allowed BOOLEAN DEFAULT TRUE,
    validity_days INTEGER DEFAULT 365,
    is_rechargeable BOOLEAN DEFAULT TRUE,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_gift_card_types_restaurant ON gift_card_types(restaurant_id);

-- Individual gift cards
CREATE TABLE gift_cards (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT NOT NULL REFERENCES restaurants(id),
    gift_card_type_id BIGINT REFERENCES gift_card_types(id),
    card_number VARCHAR(50) NOT NULL,
    pin VARCHAR(20), -- Optional PIN for extra security
    barcode VARCHAR(100),
    initial_balance DECIMAL(10,2) NOT NULL,
    current_balance DECIMAL(10,2) NOT NULL,
    currency VARCHAR(3) DEFAULT 'USD',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, REDEEMED, EXPIRED, CANCELLED
    -- Purchase info
    purchased_at TIMESTAMP WITH TIME ZONE,
    purchased_by_customer_id BIGINT REFERENCES customers(id),
    purchase_order_id BIGINT REFERENCES orders(id),
    purchase_amount DECIMAL(10,2),
    -- Recipient info
    recipient_name VARCHAR(200),
    recipient_email VARCHAR(200),
    recipient_phone VARCHAR(50),
    personal_message TEXT,
    -- Validity
    issued_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMP WITH TIME ZONE,
    last_used_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_gift_card_number UNIQUE (restaurant_id, card_number)
);

CREATE INDEX idx_gift_cards_restaurant ON gift_cards(restaurant_id);
CREATE INDEX idx_gift_cards_card_number ON gift_cards(card_number);
CREATE INDEX idx_gift_cards_barcode ON gift_cards(barcode);
CREATE INDEX idx_gift_cards_status ON gift_cards(status);

-- Gift card transactions
CREATE TABLE gift_card_transactions (
    id BIGSERIAL PRIMARY KEY,
    gift_card_id BIGINT NOT NULL REFERENCES gift_cards(id),
    order_id BIGINT REFERENCES orders(id),
    payment_id BIGINT REFERENCES payments(id),
    transaction_type VARCHAR(30) NOT NULL, -- PURCHASE, RELOAD, REDEMPTION, REFUND, ADJUSTMENT, EXPIRATION
    amount DECIMAL(10,2) NOT NULL,
    balance_before DECIMAL(10,2) NOT NULL,
    balance_after DECIMAL(10,2) NOT NULL,
    performed_by BIGINT REFERENCES users(id),
    notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_gift_card_txn_card ON gift_card_transactions(gift_card_id);
CREATE INDEX idx_gift_card_txn_order ON gift_card_transactions(order_id);
CREATE INDEX idx_gift_card_txn_created ON gift_card_transactions(created_at);

-- =====================================================
-- 5. BARCODE/SKU SUPPORT
-- =====================================================

-- Add barcode and SKU fields to products
ALTER TABLE products ADD COLUMN IF NOT EXISTS sku VARCHAR(100);
ALTER TABLE products ADD COLUMN IF NOT EXISTS barcode VARCHAR(100);
ALTER TABLE products ADD COLUMN IF NOT EXISTS barcode_type VARCHAR(20); -- UPC, EAN13, EAN8, CODE128, CODE39

CREATE INDEX IF NOT EXISTS idx_products_sku ON products(sku);
CREATE INDEX IF NOT EXISTS idx_products_barcode ON products(barcode);

-- Add barcode to product variants
ALTER TABLE product_variants ADD COLUMN IF NOT EXISTS sku VARCHAR(100);
ALTER TABLE product_variants ADD COLUMN IF NOT EXISTS barcode VARCHAR(100);

CREATE INDEX IF NOT EXISTS idx_product_variants_sku ON product_variants(sku);
CREATE INDEX IF NOT EXISTS idx_product_variants_barcode ON product_variants(barcode);

-- =====================================================
-- 6. SCALE INTEGRATION (SELL BY WEIGHT)
-- =====================================================

-- Add weight-based pricing fields to products
ALTER TABLE products ADD COLUMN IF NOT EXISTS is_sold_by_weight BOOLEAN DEFAULT FALSE;
ALTER TABLE products ADD COLUMN IF NOT EXISTS weight_unit VARCHAR(10); -- KG, LB, OZ, G
ALTER TABLE products ADD COLUMN IF NOT EXISTS tare_weight DECIMAL(10,4) DEFAULT 0; -- Container weight
ALTER TABLE products ADD COLUMN IF NOT EXISTS min_weight DECIMAL(10,4);
ALTER TABLE products ADD COLUMN IF NOT EXISTS max_weight DECIMAL(10,4);

-- Scale configuration
CREATE TABLE scales (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT NOT NULL REFERENCES restaurants(id),
    scale_name VARCHAR(100) NOT NULL,
    device_id VARCHAR(100),
    connection_type VARCHAR(30) NOT NULL, -- SERIAL, USB, NETWORK, BLUETOOTH
    connection_string VARCHAR(500), -- COM port, IP address, etc.
    protocol VARCHAR(50), -- TOLEDO, METTLER, AVERY, GENERIC
    weight_unit VARCHAR(10) DEFAULT 'KG',
    decimal_places INTEGER DEFAULT 3,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_scales_restaurant ON scales(restaurant_id);

-- =====================================================
-- 7. CUSTOMER DISPLAY SUPPORT
-- =====================================================

-- Customer display configuration
CREATE TABLE customer_displays (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT NOT NULL REFERENCES restaurants(id),
    device_id VARCHAR(100),
    display_name VARCHAR(100) NOT NULL,
    display_type VARCHAR(30) NOT NULL, -- POLE, SCREEN, TABLET
    connection_type VARCHAR(30), -- SERIAL, USB, NETWORK, WEBSOCKET
    connection_string VARCHAR(500),
    show_item_details BOOLEAN DEFAULT TRUE,
    show_running_total BOOLEAN DEFAULT TRUE,
    show_promotions BOOLEAN DEFAULT TRUE,
    idle_message VARCHAR(500),
    thank_you_message VARCHAR(500),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_customer_displays_restaurant ON customer_displays(restaurant_id);

-- Customer display messages queue
CREATE TABLE customer_display_messages (
    id BIGSERIAL PRIMARY KEY,
    customer_display_id BIGINT NOT NULL REFERENCES customer_displays(id),
    order_id BIGINT REFERENCES orders(id),
    message_type VARCHAR(30) NOT NULL, -- ITEM_ADDED, TOTAL_UPDATE, PAYMENT, THANK_YOU, PROMO
    message_data JSONB NOT NULL,
    displayed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_cust_display_msgs_display ON customer_display_messages(customer_display_id);
CREATE INDEX idx_cust_display_msgs_order ON customer_display_messages(order_id);

-- =====================================================
-- 8. TAX EXEMPTION SUPPORT
-- =====================================================

-- Tax exemption types
CREATE TABLE tax_exemption_types (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT NOT NULL REFERENCES restaurants(id),
    name VARCHAR(100) NOT NULL,
    description TEXT,
    exemption_code VARCHAR(50),
    requires_documentation BOOLEAN DEFAULT TRUE,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_tax_exemption_types_restaurant ON tax_exemption_types(restaurant_id);

-- Add tax exemption fields to customers
ALTER TABLE customers ADD COLUMN IF NOT EXISTS is_tax_exempt BOOLEAN DEFAULT FALSE;
ALTER TABLE customers ADD COLUMN IF NOT EXISTS tax_exemption_type_id BIGINT REFERENCES tax_exemption_types(id);
ALTER TABLE customers ADD COLUMN IF NOT EXISTS tax_exemption_number VARCHAR(100);
ALTER TABLE customers ADD COLUMN IF NOT EXISTS tax_exemption_expires_at DATE;

CREATE INDEX IF NOT EXISTS idx_customers_tax_exempt ON customers(is_tax_exempt) WHERE is_tax_exempt = TRUE;

-- Add tax exemption fields to orders
ALTER TABLE orders ADD COLUMN IF NOT EXISTS is_tax_exempt BOOLEAN DEFAULT FALSE;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS tax_exemption_type_id BIGINT REFERENCES tax_exemption_types(id);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS tax_exemption_number VARCHAR(100);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS tax_exemption_reason VARCHAR(500);

-- Tax exemption audit log
CREATE TABLE tax_exemption_logs (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT NOT NULL REFERENCES restaurants(id),
    order_id BIGINT REFERENCES orders(id),
    customer_id BIGINT REFERENCES customers(id),
    exemption_type_id BIGINT REFERENCES tax_exemption_types(id),
    exemption_number VARCHAR(100),
    tax_amount_exempted DECIMAL(10,2) NOT NULL,
    applied_by BIGINT NOT NULL REFERENCES users(id),
    reason VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_tax_exemption_logs_restaurant ON tax_exemption_logs(restaurant_id);
CREATE INDEX idx_tax_exemption_logs_order ON tax_exemption_logs(order_id);
CREATE INDEX idx_tax_exemption_logs_customer ON tax_exemption_logs(customer_id);

-- =====================================================
-- 9. UPDATE EXISTING TABLES
-- =====================================================

-- Add device tracking to orders for offline support
ALTER TABLE orders ADD COLUMN IF NOT EXISTS device_id VARCHAR(100);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS client_order_id VARCHAR(100);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS is_offline_order BOOLEAN DEFAULT FALSE;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS synced_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX IF NOT EXISTS idx_orders_client_order_id ON orders(client_order_id);
CREATE INDEX IF NOT EXISTS idx_orders_device_id ON orders(device_id);

-- Add shift reference to payments
ALTER TABLE payments ADD COLUMN IF NOT EXISTS shift_id BIGINT REFERENCES employee_shifts(id);

CREATE INDEX IF NOT EXISTS idx_payments_shift ON payments(shift_id);

-- =====================================================
-- 10. FUNCTIONS & TRIGGERS
-- =====================================================

-- Function to update gift card balance
CREATE OR REPLACE FUNCTION update_gift_card_balance()
RETURNS TRIGGER AS $$
BEGIN
    UPDATE gift_cards
    SET current_balance = NEW.balance_after,
        last_used_at = CASE WHEN NEW.transaction_type IN ('REDEMPTION', 'REFUND') THEN NOW() ELSE last_used_at END,
        updated_at = NOW()
    WHERE id = NEW.gift_card_id;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_update_gift_card_balance
AFTER INSERT ON gift_card_transactions
FOR EACH ROW
EXECUTE FUNCTION update_gift_card_balance();

-- Function to check gift card expiration
CREATE OR REPLACE FUNCTION check_gift_card_expiration()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.expires_at IS NOT NULL AND NEW.expires_at < NOW() AND NEW.status = 'ACTIVE' THEN
        NEW.status := 'EXPIRED';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_check_gift_card_expiration
BEFORE UPDATE ON gift_cards
FOR EACH ROW
EXECUTE FUNCTION check_gift_card_expiration();

-- =====================================================
-- COMMENTS
-- =====================================================

COMMENT ON TABLE offline_orders IS 'Orders created while POS terminal was offline, pending sync';
COMMENT ON TABLE pos_devices IS 'Registered POS devices with offline capabilities';
COMMENT ON TABLE cash_drawers IS 'Physical cash drawer configuration per terminal';
COMMENT ON TABLE cash_drawer_operations IS 'Log of all cash drawer open/close and cash movements';
COMMENT ON TABLE employee_shifts IS 'Employee time tracking and shift reconciliation';
COMMENT ON TABLE shift_breaks IS 'Break periods within employee shifts';
COMMENT ON TABLE gift_card_types IS 'Gift card product configurations';
COMMENT ON TABLE gift_cards IS 'Individual gift cards with balance tracking';
COMMENT ON TABLE gift_card_transactions IS 'All gift card value changes';
COMMENT ON TABLE scales IS 'Weighing scale device configuration';
COMMENT ON TABLE customer_displays IS 'Customer-facing display configuration';
COMMENT ON TABLE tax_exemption_types IS 'Types of tax exemptions (non-profit, resale, etc.)';
COMMENT ON TABLE tax_exemption_logs IS 'Audit trail for tax exemption applications';
