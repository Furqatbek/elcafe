-- Create notifications table for tracking order state notifications
CREATE TABLE notifications (
    id BIGSERIAL PRIMARY KEY,
    user_role VARCHAR(20) NOT NULL,
    user_id BIGINT,
    type VARCHAR(50) NOT NULL,
    title VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    order_id BIGINT,
    order_number VARCHAR(50),
    status VARCHAR(20) NOT NULL DEFAULT 'UNREAD',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at TIMESTAMP,
    metadata TEXT,
    priority INTEGER DEFAULT 3,

    CONSTRAINT chk_user_role CHECK (user_role IN ('ADMIN', 'RESTAURANT', 'CUSTOMER', 'COURIER', 'KITCHEN', 'WAITER')),
    CONSTRAINT chk_notification_status CHECK (status IN ('UNREAD', 'READ', 'ARCHIVED')),
    CONSTRAINT chk_priority CHECK (priority >= 1 AND priority <= 5)
);

-- Create indexes for efficient querying
CREATE INDEX idx_user_role ON notifications(user_role);
CREATE INDEX idx_user_id_role ON notifications(user_id, user_role);
CREATE INDEX idx_status ON notifications(status);
CREATE INDEX idx_created_at ON notifications(created_at DESC);
CREATE INDEX idx_order_id ON notifications(order_id);

-- Add foreign key constraint to orders table
ALTER TABLE notifications
    ADD CONSTRAINT fk_notification_order
    FOREIGN KEY (order_id)
    REFERENCES orders(id)
    ON DELETE CASCADE;

-- Add comment to table
COMMENT ON TABLE notifications IS 'Stores notifications for different user roles about order state changes';
COMMENT ON COLUMN notifications.user_role IS 'The role this notification is for (ADMIN, RESTAURANT, CUSTOMER, COURIER, KITCHEN, WAITER)';
COMMENT ON COLUMN notifications.user_id IS 'Specific user ID within that role (null = all users with that role)';
COMMENT ON COLUMN notifications.type IS 'Type of notification event';
COMMENT ON COLUMN notifications.priority IS 'Priority level (1 = highest, 5 = lowest)';
