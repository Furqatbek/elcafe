-- Web Push Notification Tables
-- For browser push notification subscriptions and logs

-- Push subscriptions table - stores browser push subscription data
CREATE TABLE push_subscriptions (
    id BIGSERIAL PRIMARY KEY,
    customer_id BIGINT REFERENCES customers(id) ON DELETE CASCADE,
    user_id BIGINT REFERENCES users(id) ON DELETE CASCADE,

    -- Web Push subscription data
    endpoint VARCHAR(500) NOT NULL,
    p256dh_key VARCHAR(500) NOT NULL,
    auth_key VARCHAR(500) NOT NULL,

    -- Device info
    device_type VARCHAR(50),
    browser VARCHAR(100),
    user_agent TEXT,

    -- Status
    is_active BOOLEAN DEFAULT true,
    last_used_at TIMESTAMP,

    -- Timestamps
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP,

    -- Ensure unique endpoint per user
    CONSTRAINT uq_push_subscription_endpoint UNIQUE (endpoint)
);

-- Push notification logs - tracks sent notifications
CREATE TABLE push_notification_logs (
    id BIGSERIAL PRIMARY KEY,
    subscription_id BIGINT REFERENCES push_subscriptions(id) ON DELETE SET NULL,
    customer_id BIGINT REFERENCES customers(id) ON DELETE SET NULL,

    -- Notification content
    title VARCHAR(255) NOT NULL,
    body TEXT,
    icon VARCHAR(500),
    image VARCHAR(500),
    badge VARCHAR(500),
    tag VARCHAR(100),
    data JSONB,

    -- Action buttons
    actions JSONB,

    -- Notification type
    notification_type VARCHAR(50) NOT NULL,

    -- Delivery status
    status VARCHAR(20) DEFAULT 'PENDING',
    error_message TEXT,

    -- Tracking
    sent_at TIMESTAMP,
    delivered_at TIMESTAMP,
    clicked_at TIMESTAMP,
    closed_at TIMESTAMP,

    -- Source info
    campaign_id BIGINT,
    automation_rule_id BIGINT,

    -- Timestamps
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Push notification templates
CREATE TABLE push_templates (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    title VARCHAR(255) NOT NULL,
    body TEXT NOT NULL,
    icon VARCHAR(500),
    image VARCHAR(500),
    badge VARCHAR(500),

    -- Template type
    type VARCHAR(50) NOT NULL,

    -- Action buttons template
    actions JSONB,

    -- Default data payload
    default_data JSONB,

    -- Status
    is_active BOOLEAN DEFAULT true,

    -- Usage tracking
    usage_count INTEGER DEFAULT 0,

    -- Timestamps
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Push notification campaigns
CREATE TABLE push_campaigns (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,

    -- Content (can use template or custom)
    template_id BIGINT REFERENCES push_templates(id) ON DELETE SET NULL,
    title VARCHAR(255),
    body TEXT,
    icon VARCHAR(500),
    image VARCHAR(500),
    actions JSONB,
    data JSONB,

    -- Targeting
    target_audience VARCHAR(50) NOT NULL DEFAULT 'ALL',
    filter_criteria JSONB,

    -- Scheduling
    status VARCHAR(20) DEFAULT 'DRAFT',
    scheduled_at TIMESTAMP,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,

    -- Statistics
    total_recipients INTEGER DEFAULT 0,
    sent_count INTEGER DEFAULT 0,
    delivered_count INTEGER DEFAULT 0,
    clicked_count INTEGER DEFAULT 0,
    failed_count INTEGER DEFAULT 0,

    -- Timestamps
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- VAPID keys configuration (for web push authentication)
CREATE TABLE push_config (
    id BIGSERIAL PRIMARY KEY,
    config_key VARCHAR(100) NOT NULL UNIQUE,
    config_value TEXT NOT NULL,
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for better query performance
CREATE INDEX idx_push_subscriptions_customer ON push_subscriptions(customer_id);
CREATE INDEX idx_push_subscriptions_user ON push_subscriptions(user_id);
CREATE INDEX idx_push_subscriptions_active ON push_subscriptions(is_active);
CREATE INDEX idx_push_subscriptions_endpoint ON push_subscriptions(endpoint);

CREATE INDEX idx_push_logs_subscription ON push_notification_logs(subscription_id);
CREATE INDEX idx_push_logs_customer ON push_notification_logs(customer_id);
CREATE INDEX idx_push_logs_status ON push_notification_logs(status);
CREATE INDEX idx_push_logs_type ON push_notification_logs(notification_type);
CREATE INDEX idx_push_logs_sent_at ON push_notification_logs(sent_at);

CREATE INDEX idx_push_campaigns_status ON push_campaigns(status);
CREATE INDEX idx_push_campaigns_scheduled ON push_campaigns(scheduled_at);

CREATE INDEX idx_push_templates_type ON push_templates(type);
CREATE INDEX idx_push_templates_active ON push_templates(is_active);

-- Comments
COMMENT ON TABLE push_subscriptions IS 'Stores web push notification subscription data for browsers';
COMMENT ON TABLE push_notification_logs IS 'Logs all sent push notifications with delivery status';
COMMENT ON TABLE push_templates IS 'Reusable push notification templates';
COMMENT ON TABLE push_campaigns IS 'Push notification marketing campaigns';
COMMENT ON TABLE push_config IS 'Web push configuration including VAPID keys';
