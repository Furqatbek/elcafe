-- V15: Create Waiter Module Tables
-- This migration creates the waiter module tables for table management, waiter assignments, and order events

-- Create waiters table
CREATE TABLE waiters (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    pin_code VARCHAR(10) NOT NULL UNIQUE,
    email VARCHAR(100) UNIQUE,
    phone_number VARCHAR(20),
    role VARCHAR(50) NOT NULL DEFAULT 'WAITER',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    permissions TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_waiters_pin_code ON waiters(pin_code);
CREATE INDEX idx_waiters_email ON waiters(email);
CREATE INDEX idx_waiters_active ON waiters(active);
CREATE INDEX idx_waiters_role ON waiters(role);

-- Create tables table
CREATE TABLE tables (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    number INTEGER NOT NULL,
    capacity INTEGER NOT NULL DEFAULT 4,
    floor VARCHAR(50),
    section VARCHAR(50),
    status VARCHAR(50) NOT NULL DEFAULT 'FREE',
    current_waiter_id BIGINT REFERENCES waiters(id) ON DELETE SET NULL,
    merged_with_id BIGINT REFERENCES tables(id) ON DELETE SET NULL,
    opened_at TIMESTAMP,
    closed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_restaurant_table UNIQUE (restaurant_id, number)
);

CREATE INDEX idx_tables_restaurant_id ON tables(restaurant_id);
CREATE INDEX idx_tables_status ON tables(status);
CREATE INDEX idx_tables_current_waiter_id ON tables(current_waiter_id);
CREATE INDEX idx_tables_merged_with_id ON tables(merged_with_id);
CREATE INDEX idx_tables_restaurant_section ON tables(restaurant_id, section);
CREATE INDEX idx_tables_restaurant_floor ON tables(restaurant_id, floor);

-- Create waiter_tables junction table for tracking assignments
CREATE TABLE waiter_tables (
    id BIGSERIAL PRIMARY KEY,
    waiter_id BIGINT NOT NULL REFERENCES waiters(id) ON DELETE CASCADE,
    table_id BIGINT NOT NULL REFERENCES tables(id) ON DELETE CASCADE,
    assigned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    unassigned_at TIMESTAMP,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX idx_waiter_tables_waiter_id ON waiter_tables(waiter_id);
CREATE INDEX idx_waiter_tables_table_id ON waiter_tables(table_id);
CREATE INDEX idx_waiter_tables_waiter_active ON waiter_tables(waiter_id, active);
CREATE INDEX idx_waiter_tables_table_active ON waiter_tables(table_id, active);

-- Create order_events table for audit trail
CREATE TABLE order_events (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    event_type VARCHAR(100) NOT NULL,
    triggered_by VARCHAR(100),
    metadata TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_order_events_order_id ON order_events(order_id);
CREATE INDEX idx_order_events_event_type ON order_events(event_type);
CREATE INDEX idx_order_events_created_at ON order_events(created_at);

-- Modify orders table to add table_id and waiter_id
ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS table_id BIGINT REFERENCES tables(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS waiter_id BIGINT REFERENCES waiters(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_orders_table_id ON orders(table_id);
CREATE INDEX IF NOT EXISTS idx_orders_waiter_id ON orders(waiter_id);

-- Add order_source column if it doesn't exist (for tracking where the order came from)
ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS order_source VARCHAR(50) DEFAULT 'ADMIN_PANEL';

CREATE INDEX IF NOT EXISTS idx_orders_order_source ON orders(order_source);
