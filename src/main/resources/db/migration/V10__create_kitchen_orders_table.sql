-- Migration: Create kitchen_orders table
-- Version: V10
-- Description: Creates kitchen_orders table for managing order preparation in the kitchen

-- Create kitchen_orders table
CREATE TABLE kitchen_orders (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL UNIQUE,
    status VARCHAR(50) NOT NULL,
    priority VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
    assigned_chef VARCHAR(200),
    preparation_started_at TIMESTAMP,
    preparation_completed_at TIMESTAMP,
    estimated_preparation_time_minutes INTEGER,
    actual_preparation_time_minutes INTEGER,
    notes VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_kitchen_order_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE
);

-- Create indexes for kitchen_orders
CREATE INDEX idx_kitchen_orders_order_id ON kitchen_orders(order_id);
CREATE INDEX idx_kitchen_orders_status ON kitchen_orders(status);
CREATE INDEX idx_kitchen_orders_priority ON kitchen_orders(priority);
CREATE INDEX idx_kitchen_orders_assigned_chef ON kitchen_orders(assigned_chef);
CREATE INDEX idx_kitchen_orders_created_at ON kitchen_orders(created_at);

-- Add comments for documentation
COMMENT ON TABLE kitchen_orders IS 'Manages food preparation workflow in the kitchen';

COMMENT ON COLUMN kitchen_orders.order_id IS 'Reference to the main order (one-to-one relationship)';
COMMENT ON COLUMN kitchen_orders.status IS 'Kitchen order status: PENDING, PREPARING, READY, PICKED_UP, CANCELLED';
COMMENT ON COLUMN kitchen_orders.priority IS 'Order priority level: LOW, NORMAL, HIGH, URGENT';
COMMENT ON COLUMN kitchen_orders.assigned_chef IS 'Name of the chef assigned to prepare this order';
COMMENT ON COLUMN kitchen_orders.preparation_started_at IS 'Timestamp when chef started preparing the order';
COMMENT ON COLUMN kitchen_orders.preparation_completed_at IS 'Timestamp when preparation was completed';
COMMENT ON COLUMN kitchen_orders.estimated_preparation_time_minutes IS 'Estimated time for preparation in minutes';
COMMENT ON COLUMN kitchen_orders.actual_preparation_time_minutes IS 'Actual time taken for preparation in minutes';
COMMENT ON COLUMN kitchen_orders.notes IS 'Special instructions or notes for the kitchen staff';
COMMENT ON COLUMN kitchen_orders.created_at IS 'Timestamp when the kitchen order was created';
COMMENT ON COLUMN kitchen_orders.updated_at IS 'Timestamp when the kitchen order was last updated';
