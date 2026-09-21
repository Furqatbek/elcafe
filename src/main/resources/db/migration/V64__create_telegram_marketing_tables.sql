-- Telegram Marketing Tables for Campaign Management and Automation

-- Telegram Bot Configuration
CREATE TABLE telegram_bot_config (
    id BIGSERIAL PRIMARY KEY,
    bot_token VARCHAR(100) NOT NULL,
    bot_username VARCHAR(100) NOT NULL,
    webhook_url VARCHAR(500),
    is_active BOOLEAN DEFAULT true,
    welcome_message TEXT,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- Telegram Subscribers (users who interacted with the bot)
CREATE TABLE telegram_subscribers (
    id BIGSERIAL PRIMARY KEY,
    telegram_user_id BIGINT NOT NULL UNIQUE,
    username VARCHAR(100),
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    language_code VARCHAR(10),
    customer_id BIGINT REFERENCES customers(id),
    is_active BOOLEAN DEFAULT true,
    is_blocked BOOLEAN DEFAULT false,
    subscribed_at TIMESTAMP DEFAULT NOW(),
    last_interaction_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- Telegram Message Templates
CREATE TABLE telegram_templates (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    content TEXT NOT NULL,                    -- Message with placeholders: {name}, {code}, {amount}, etc.
    type VARCHAR(50) NOT NULL,                -- WELCOME, PROMOTION, BIRTHDAY, REFERRAL, REMINDER, ORDER_STATUS, CUSTOM
    description VARCHAR(500),
    has_image BOOLEAN DEFAULT false,
    image_url VARCHAR(500),
    has_buttons BOOLEAN DEFAULT false,
    buttons_config JSONB,                     -- Button configuration: [{text: "Order Now", url: "..."}, ...]
    is_active BOOLEAN DEFAULT true,
    usage_count INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- Telegram Broadcast Campaigns
CREATE TABLE telegram_campaigns (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    template_id BIGINT REFERENCES telegram_templates(id),
    custom_message TEXT,                      -- Custom message instead of template
    image_url VARCHAR(500),
    buttons_config JSONB,
    target_audience VARCHAR(50) NOT NULL,     -- ALL, ACTIVE, INACTIVE, LINKED_CUSTOMERS, CUSTOM
    filter_criteria JSONB,                    -- Additional filters: {days_inactive: 30, has_customer: true}
    recipient_count INTEGER DEFAULT 0,
    sent_count INTEGER DEFAULT 0,
    delivered_count INTEGER DEFAULT 0,
    failed_count INTEGER DEFAULT 0,
    status VARCHAR(20) DEFAULT 'DRAFT',       -- DRAFT, SCHEDULED, SENDING, PAUSED, COMPLETED, CANCELLED
    scheduled_at TIMESTAMP,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- Telegram Campaign Recipients
CREATE TABLE telegram_campaign_recipients (
    id BIGSERIAL PRIMARY KEY,
    campaign_id BIGINT NOT NULL REFERENCES telegram_campaigns(id) ON DELETE CASCADE,
    subscriber_id BIGINT NOT NULL REFERENCES telegram_subscribers(id),
    telegram_user_id BIGINT NOT NULL,
    message_content TEXT,
    telegram_message_id BIGINT,               -- Telegram API message ID
    status VARCHAR(20) DEFAULT 'PENDING',     -- PENDING, SENT, DELIVERED, FAILED, BLOCKED
    sent_at TIMESTAMP,
    delivered_at TIMESTAMP,
    error_message TEXT,
    created_at TIMESTAMP DEFAULT NOW()
);

-- Telegram Automation Rules
CREATE TABLE telegram_automation_rules (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    trigger_type VARCHAR(50) NOT NULL,        -- BOT_START, NEW_SUBSCRIBER, INACTIVE_USER, BIRTHDAY, REFERRAL_REWARD, ORDER_STATUS
    template_id BIGINT NOT NULL REFERENCES telegram_templates(id),
    delay_minutes INTEGER DEFAULT 0,
    is_active BOOLEAN DEFAULT true,
    conditions JSONB,
    sent_count INTEGER DEFAULT 0,
    last_triggered_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- Telegram Message Logs
CREATE TABLE telegram_logs (
    id BIGSERIAL PRIMARY KEY,
    subscriber_id BIGINT REFERENCES telegram_subscribers(id),
    telegram_user_id BIGINT NOT NULL,
    username VARCHAR(100),
    message TEXT NOT NULL,
    message_type VARCHAR(50),                 -- CAMPAIGN, AUTOMATION, NOTIFICATION, MANUAL
    template_id BIGINT REFERENCES telegram_templates(id),
    campaign_id BIGINT REFERENCES telegram_campaigns(id),
    automation_rule_id BIGINT REFERENCES telegram_automation_rules(id),
    telegram_message_id BIGINT,
    status VARCHAR(20) DEFAULT 'PENDING',     -- PENDING, SENT, DELIVERED, FAILED, BLOCKED
    sent_at TIMESTAMP,
    delivered_at TIMESTAMP,
    error_message TEXT,
    metadata JSONB,
    created_at TIMESTAMP DEFAULT NOW()
);

-- Telegram Bot Commands
CREATE TABLE telegram_bot_commands (
    id BIGSERIAL PRIMARY KEY,
    command VARCHAR(50) NOT NULL UNIQUE,      -- /start, /menu, /order, /help, etc.
    description VARCHAR(200),
    response_template_id BIGINT REFERENCES telegram_templates(id),
    custom_response TEXT,
    is_active BOOLEAN DEFAULT true,
    usage_count INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- Indexes
CREATE INDEX idx_telegram_subscribers_user_id ON telegram_subscribers(telegram_user_id);
CREATE INDEX idx_telegram_subscribers_customer ON telegram_subscribers(customer_id);
CREATE INDEX idx_telegram_subscribers_active ON telegram_subscribers(is_active);

CREATE INDEX idx_telegram_templates_type ON telegram_templates(type);
CREATE INDEX idx_telegram_templates_active ON telegram_templates(is_active);

CREATE INDEX idx_telegram_campaigns_status ON telegram_campaigns(status);
CREATE INDEX idx_telegram_campaigns_scheduled ON telegram_campaigns(scheduled_at);
CREATE INDEX idx_telegram_campaigns_created ON telegram_campaigns(created_at);

CREATE INDEX idx_telegram_campaign_recipients_campaign ON telegram_campaign_recipients(campaign_id);
CREATE INDEX idx_telegram_campaign_recipients_status ON telegram_campaign_recipients(status);

CREATE INDEX idx_telegram_automation_rules_trigger ON telegram_automation_rules(trigger_type);
CREATE INDEX idx_telegram_automation_rules_active ON telegram_automation_rules(is_active);

CREATE INDEX idx_telegram_logs_subscriber ON telegram_logs(subscriber_id);
CREATE INDEX idx_telegram_logs_campaign ON telegram_logs(campaign_id);
CREATE INDEX idx_telegram_logs_status ON telegram_logs(status);
CREATE INDEX idx_telegram_logs_created ON telegram_logs(created_at);
CREATE INDEX idx_telegram_logs_type ON telegram_logs(message_type);

-- Insert default Telegram templates
INSERT INTO telegram_templates (name, content, type, description, has_buttons, buttons_config) VALUES
('Welcome Message', '👋 Assalomu alaykum {name}!\n\nJangirovs botiga xush kelibsiz! 🍽️\n\nBu yerda siz:\n✅ Menuni ko''rishingiz\n✅ Buyurtma berishingiz\n✅ Aksiyalar haqida bilib turishingiz mumkin', 'WELCOME', 'Sent when user starts the bot', true, '[{"text": "📋 Menuni ko''rish", "callback_data": "menu"}, {"text": "🛒 Buyurtma berish", "url": "https://jangirovs.uz/order"}]'),
('Birthday Greeting', '🎂 Tug''ilgan kuningiz bilan {name}!\n\nJangirovs sizga 20% chegirma taqdim etadi! 🎁\n\nPromo kod: BDAY{year}\nAmal qilish muddati: bugun!', 'BIRTHDAY', 'Sent on subscriber birthday', true, '[{"text": "🛒 Buyurtma berish", "url": "https://jangirovs.uz/order"}]'),
('Inactive User', '👋 {name}, sizni sog''indik!\n\nJangirovs''da yangi taomlar kutmoqda! 🍕🍔\n\n15% chegirma: COMEBACK15', 'REMINDER', 'Sent to inactive users', true, '[{"text": "📋 Yangi menuni ko''rish", "callback_data": "menu"}]'),
('Order Status', '📦 {name}, buyurtmangiz #{order_number}\n\nHolati: {status}\n{message}', 'ORDER_STATUS', 'Order status notifications', false, null),
('Referral Reward', '🎉 Tabriklaymiz {name}!\n\nDo''stingiz birinchi buyurtma berdi.\n{amount} bonus ball hisobingizga qo''shildi! 💰', 'REFERRAL', 'Sent when referral completes', false, null),
('Promotion', '🔥 {name}, maxsus taklif!\n\n{promo_text}\n\nPromo kod: {promo_code}\nAmal qilish: {expiry_date} gacha', 'PROMOTION', 'General promotion template', true, '[{"text": "🛒 Hoziroq buyurtma bering", "url": "https://jangirovs.uz/order"}]');

-- Insert default automation rules
INSERT INTO telegram_automation_rules (name, description, trigger_type, template_id, delay_minutes, conditions) VALUES
('Welcome Message', 'Send welcome message to new subscribers', 'BOT_START', (SELECT id FROM telegram_templates WHERE type = 'WELCOME' LIMIT 1), 0, '{}'),
('Birthday Message', 'Send birthday greeting', 'BIRTHDAY', (SELECT id FROM telegram_templates WHERE type = 'BIRTHDAY' LIMIT 1), 0, '{}'),
('Win-back Campaign', 'Send message to inactive subscribers', 'INACTIVE_USER', (SELECT id FROM telegram_templates WHERE type = 'REMINDER' LIMIT 1), 0, '{"days_inactive": 14}');

-- Insert default bot commands
INSERT INTO telegram_bot_commands (command, description, custom_response) VALUES
('/start', 'Start the bot', NULL),
('/menu', 'View the menu', '📋 Bizning menuni ko''ring: https://jangirovs.uz/menu'),
('/order', 'Place an order', '🛒 Buyurtma berish: https://jangirovs.uz/order'),
('/help', 'Get help', '❓ Yordam kerakmi?\n\n📞 Telefon: +998770049909\n📍 Manzil: Hazorasp, Xorazm\n⏰ Ish vaqti: 09:00 - 23:00'),
('/promo', 'View current promotions', '🔥 Joriy aksiyalarni saytimizda ko''ring: https://jangirovs.uz/promotions');
