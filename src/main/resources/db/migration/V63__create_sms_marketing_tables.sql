-- SMS Marketing Tables for Campaign Management and Automation

-- SMS Templates for marketing messages
CREATE TABLE sms_templates (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    content TEXT NOT NULL,                    -- Message with placeholders: {name}, {code}, {amount}, etc.
    type VARCHAR(50) NOT NULL,                -- WELCOME, PROMOTION, BIRTHDAY, REFERRAL, REMINDER, ORDER_STATUS, CUSTOM
    description VARCHAR(500),
    is_active BOOLEAN DEFAULT true,
    usage_count INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- SMS Campaigns for bulk messaging
CREATE TABLE sms_campaigns (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    template_id BIGINT REFERENCES sms_templates(id),
    custom_message TEXT,                      -- Custom message instead of template
    target_audience VARCHAR(50) NOT NULL,     -- ALL, SEGMENT, CUSTOM, BIRTHDAY_TODAY, INACTIVE
    segment_id BIGINT,                        -- Customer segment ID if targeting segment
    filter_criteria JSONB,                    -- Additional filters: days_inactive, min_orders, etc.
    recipient_count INTEGER DEFAULT 0,
    sent_count INTEGER DEFAULT 0,
    delivered_count INTEGER DEFAULT 0,
    failed_count INTEGER DEFAULT 0,
    total_cost DECIMAL(12,2) DEFAULT 0,
    status VARCHAR(20) DEFAULT 'DRAFT',       -- DRAFT, SCHEDULED, SENDING, PAUSED, COMPLETED, CANCELLED
    scheduled_at TIMESTAMP,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- Campaign Recipients tracking
CREATE TABLE sms_campaign_recipients (
    id BIGSERIAL PRIMARY KEY,
    campaign_id BIGINT NOT NULL REFERENCES sms_campaigns(id) ON DELETE CASCADE,
    customer_id BIGINT,
    phone VARCHAR(20) NOT NULL,
    customer_name VARCHAR(200),
    message_content TEXT,                     -- Actual message sent (with placeholders replaced)
    eskiz_message_id BIGINT,                  -- Eskiz broker message ID
    eskiz_dispatch_id BIGINT,                 -- Eskiz batch dispatch ID
    status VARCHAR(20) DEFAULT 'PENDING',     -- PENDING, QUEUED, SENT, DELIVERED, FAILED, REJECTED
    cost DECIMAL(10,2),
    sent_at TIMESTAMP,
    delivered_at TIMESTAMP,
    error_message TEXT,
    created_at TIMESTAMP DEFAULT NOW()
);

-- Automated SMS Rules (triggers for automatic messages)
CREATE TABLE sms_automation_rules (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    trigger_type VARCHAR(50) NOT NULL,        -- WELCOME, BIRTHDAY, INACTIVE_CUSTOMER, FIRST_ORDER, LOYALTY_MILESTONE, REFERRAL_SIGNUP, REFERRAL_REWARD
    template_id BIGINT NOT NULL REFERENCES sms_templates(id),
    delay_minutes INTEGER DEFAULT 0,          -- Delay after trigger event
    is_active BOOLEAN DEFAULT true,
    conditions JSONB,                         -- Conditions: {days_inactive: 30, min_orders: 1, loyalty_tier: "GOLD"}
    sent_count INTEGER DEFAULT 0,
    last_triggered_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- SMS Logs (all sent messages for tracking and analytics)
CREATE TABLE sms_logs (
    id BIGSERIAL PRIMARY KEY,
    customer_id BIGINT,
    phone VARCHAR(20) NOT NULL,
    customer_name VARCHAR(200),
    message TEXT NOT NULL,
    message_type VARCHAR(50),                 -- CAMPAIGN, AUTOMATION, TRANSACTIONAL, MANUAL
    template_id BIGINT REFERENCES sms_templates(id),
    campaign_id BIGINT REFERENCES sms_campaigns(id),
    automation_rule_id BIGINT REFERENCES sms_automation_rules(id),
    eskiz_message_id BIGINT,
    eskiz_dispatch_id BIGINT,
    status VARCHAR(20) DEFAULT 'PENDING',     -- PENDING, QUEUED, SENT, DELIVERED, FAILED, REJECTED
    cost DECIMAL(10,2),
    sent_at TIMESTAMP,
    delivered_at TIMESTAMP,
    status_updated_at TIMESTAMP,
    error_message TEXT,
    metadata JSONB,                           -- Additional context data
    created_at TIMESTAMP DEFAULT NOW()
);

-- Indexes for performance
CREATE INDEX idx_sms_templates_type ON sms_templates(type);
CREATE INDEX idx_sms_templates_active ON sms_templates(is_active);

CREATE INDEX idx_sms_campaigns_status ON sms_campaigns(status);
CREATE INDEX idx_sms_campaigns_scheduled ON sms_campaigns(scheduled_at);
CREATE INDEX idx_sms_campaigns_created ON sms_campaigns(created_at);

CREATE INDEX idx_sms_campaign_recipients_campaign ON sms_campaign_recipients(campaign_id);
CREATE INDEX idx_sms_campaign_recipients_status ON sms_campaign_recipients(status);
CREATE INDEX idx_sms_campaign_recipients_customer ON sms_campaign_recipients(customer_id);

CREATE INDEX idx_sms_automation_rules_trigger ON sms_automation_rules(trigger_type);
CREATE INDEX idx_sms_automation_rules_active ON sms_automation_rules(is_active);

CREATE INDEX idx_sms_logs_customer ON sms_logs(customer_id);
CREATE INDEX idx_sms_logs_campaign ON sms_logs(campaign_id);
CREATE INDEX idx_sms_logs_status ON sms_logs(status);
CREATE INDEX idx_sms_logs_created ON sms_logs(created_at);
CREATE INDEX idx_sms_logs_type ON sms_logs(message_type);

-- Default SMS templates and automation rules were originally seeded here. Removed for production:
-- they embedded demo branding and promised promo codes (WELCOME10, BDAY{year}, COMEBACK15) that do
-- not exist as real promotions. Deployments start from a clean database; restaurants create their
-- own templates and rules through the admin UI.
