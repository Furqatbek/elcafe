-- =====================================================
-- V86: Create Audit Logs Table
-- Comprehensive audit trail for security-critical operations
-- =====================================================

CREATE TABLE audit_logs (
    id BIGSERIAL PRIMARY KEY,

    -- WHO performed the action
    user_id BIGINT,
    username VARCHAR(100) NOT NULL,
    user_role VARCHAR(50),

    -- WHAT action was performed
    action VARCHAR(50) NOT NULL,
    action_detail VARCHAR(500),

    -- WHICH entity was affected
    entity_type VARCHAR(50) NOT NULL,
    entity_id BIGINT,

    -- Order reference for financial operations
    order_id BIGINT,
    order_number VARCHAR(50),

    -- Restaurant context
    restaurant_id BIGINT,

    -- Financial impact
    amount DECIMAL(10,2),
    currency VARCHAR(3),

    -- WHERE the action originated
    ip_address VARCHAR(45),
    terminal_id VARCHAR(100),
    device_info VARCHAR(500),
    user_agent VARCHAR(500),
    session_id VARCHAR(100),

    -- Change tracking
    previous_value TEXT,
    new_value TEXT,

    -- Authorization context
    authorized_by_id BIGINT,
    authorized_by_username VARCHAR(100),
    authorization_reason VARCHAR(500),

    -- Result
    result VARCHAR(20) NOT NULL DEFAULT 'SUCCESS',
    failure_reason VARCHAR(500),

    -- WHEN
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for common query patterns
CREATE INDEX idx_audit_restaurant ON audit_logs(restaurant_id);
CREATE INDEX idx_audit_user ON audit_logs(user_id);
CREATE INDEX idx_audit_action ON audit_logs(action);
CREATE INDEX idx_audit_entity ON audit_logs(entity_type, entity_id);
CREATE INDEX idx_audit_created ON audit_logs(created_at);
CREATE INDEX idx_audit_order ON audit_logs(order_id);
CREATE INDEX idx_audit_result ON audit_logs(result);
CREATE INDEX idx_audit_ip ON audit_logs(ip_address);

-- Composite indexes for common queries
CREATE INDEX idx_audit_restaurant_action ON audit_logs(restaurant_id, action, created_at);
CREATE INDEX idx_audit_user_action ON audit_logs(user_id, action, created_at);

-- Add comments for documentation
COMMENT ON TABLE audit_logs IS 'Comprehensive audit trail for security-critical operations';
COMMENT ON COLUMN audit_logs.action IS 'The type of action performed (see AuditAction enum)';
COMMENT ON COLUMN audit_logs.result IS 'Result of the action: SUCCESS, FAILURE, DENIED, PENDING_APPROVAL';
COMMENT ON COLUMN audit_logs.ip_address IS 'Client IP address for fraud detection';
COMMENT ON COLUMN audit_logs.terminal_id IS 'POS terminal identifier';
