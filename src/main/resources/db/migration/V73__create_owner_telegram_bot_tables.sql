-- Owner/Staff Telegram Bot Tables for notifications

-- Owner telegram subscribers (staff linked to telegram)
CREATE TABLE owner_telegram_subscribers (
    id BIGSERIAL PRIMARY KEY,
    telegram_user_id BIGINT NOT NULL UNIQUE,
    username VARCHAR(100),
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    language_code VARCHAR(10),

    -- Link to user account (owner, manager, etc.)
    user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    restaurant_id BIGINT REFERENCES restaurants(id) ON DELETE SET NULL,

    -- Role-based access
    role VARCHAR(50), -- OWNER, MANAGER, CHEF, WAITER

    -- Status
    is_active BOOLEAN DEFAULT TRUE,
    is_verified BOOLEAN DEFAULT FALSE,
    verification_code VARCHAR(10),
    verification_expires_at TIMESTAMP,

    -- Timestamps
    subscribed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_interaction_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Owner notification settings
CREATE TABLE owner_notification_settings (
    id BIGSERIAL PRIMARY KEY,
    subscriber_id BIGINT NOT NULL REFERENCES owner_telegram_subscribers(id) ON DELETE CASCADE,

    -- Notification types
    notify_new_order BOOLEAN DEFAULT TRUE,
    notify_new_reservation BOOLEAN DEFAULT TRUE,
    notify_low_stock BOOLEAN DEFAULT TRUE,
    notify_customer_review BOOLEAN DEFAULT TRUE,
    notify_daily_report BOOLEAN DEFAULT TRUE,
    notify_critical_alerts BOOLEAN DEFAULT TRUE,
    notify_order_cancelled BOOLEAN DEFAULT TRUE,
    notify_reservation_cancelled BOOLEAN DEFAULT TRUE,

    -- Quiet hours (don't send during these hours)
    quiet_hours_enabled BOOLEAN DEFAULT FALSE,
    quiet_hours_start TIME DEFAULT '23:00:00',
    quiet_hours_end TIME DEFAULT '07:00:00',

    -- Summary preferences
    receive_hourly_summary BOOLEAN DEFAULT FALSE,
    receive_daily_summary BOOLEAN DEFAULT TRUE,
    daily_summary_time TIME DEFAULT '22:00:00',

    -- Thresholds
    min_order_amount_notify DECIMAL(10,2) DEFAULT 0,
    low_stock_threshold INTEGER DEFAULT 10,

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    UNIQUE(subscriber_id)
);

-- Owner notification log
CREATE TABLE owner_notification_log (
    id BIGSERIAL PRIMARY KEY,
    subscriber_id BIGINT REFERENCES owner_telegram_subscribers(id) ON DELETE SET NULL,
    telegram_user_id BIGINT NOT NULL,

    -- Notification details
    notification_type VARCHAR(50) NOT NULL, -- NEW_ORDER, NEW_RESERVATION, LOW_STOCK, REVIEW, DAILY_REPORT, CRITICAL_ALERT
    title VARCHAR(255),
    message TEXT NOT NULL,

    -- Related entity
    related_entity_type VARCHAR(50), -- ORDER, RESERVATION, INVENTORY, REVIEW
    related_entity_id BIGINT,

    -- Telegram response
    telegram_message_id INTEGER,
    status VARCHAR(20) DEFAULT 'PENDING', -- PENDING, SENT, DELIVERED, FAILED
    error_message TEXT,

    -- Timestamps
    sent_at TIMESTAMP,
    delivered_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Owner bot config (separate from customer bot)
CREATE TABLE owner_telegram_bot_config (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT REFERENCES restaurants(id) ON DELETE CASCADE,

    bot_token VARCHAR(255),
    bot_username VARCHAR(100),

    -- Features
    is_active BOOLEAN DEFAULT TRUE,
    welcome_message TEXT,

    -- Auto-verification
    auto_verify_owners BOOLEAN DEFAULT TRUE,

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    UNIQUE(restaurant_id)
);

-- Indexes
CREATE INDEX idx_owner_telegram_subscribers_telegram_user_id ON owner_telegram_subscribers(telegram_user_id);
CREATE INDEX idx_owner_telegram_subscribers_user_id ON owner_telegram_subscribers(user_id);
CREATE INDEX idx_owner_telegram_subscribers_restaurant_id ON owner_telegram_subscribers(restaurant_id);
CREATE INDEX idx_owner_notification_log_subscriber_id ON owner_notification_log(subscriber_id);
CREATE INDEX idx_owner_notification_log_type ON owner_notification_log(notification_type);
CREATE INDEX idx_owner_notification_log_created_at ON owner_notification_log(created_at);
CREATE INDEX idx_owner_notification_log_related_entity ON owner_notification_log(related_entity_type, related_entity_id);

-- Add reservation notification triggers to customer bot automation
INSERT INTO telegram_templates (name, content, type, is_active, created_at, updated_at)
SELECT 'Подтверждение бронирования',
       E'🎉 <b>Бронирование подтверждено!</b>\n\n📅 Дата: {date}\n⏰ Время: {time}\n👥 Гостей: {guests}\n🏪 Ресторан: {restaurant}\n\n📝 Код подтверждения: <code>{code}</code>\n\nЖдём вас!',
       'ORDER_STATUS',
       true,
       NOW(),
       NOW()
WHERE NOT EXISTS (SELECT 1 FROM telegram_templates WHERE name = 'Подтверждение бронирования');

INSERT INTO telegram_templates (name, content, type, is_active, created_at, updated_at)
SELECT 'Напоминание о бронировании',
       E'⏰ <b>Напоминание о бронировании</b>\n\nУважаемый(ая) {name},\n\nНапоминаем, что завтра у вас бронирование:\n\n📅 {date}\n⏰ {time}\n👥 {guests} гостей\n🏪 {restaurant}\n\nЖдём вас!',
       'REMINDER',
       true,
       NOW(),
       NOW()
WHERE NOT EXISTS (SELECT 1 FROM telegram_templates WHERE name = 'Напоминание о бронировании');

INSERT INTO telegram_templates (name, content, type, is_active, created_at, updated_at)
SELECT 'Статус заказа - Готовится',
       E'👨‍🍳 <b>Ваш заказ готовится!</b>\n\n📦 Заказ #{order_number}\n⏱ Примерное время: {estimated_time} мин\n\nМы сообщим когда заказ будет готов.',
       'ORDER_STATUS',
       true,
       NOW(),
       NOW()
WHERE NOT EXISTS (SELECT 1 FROM telegram_templates WHERE name = 'Статус заказа - Готовится');

INSERT INTO telegram_templates (name, content, type, is_active, created_at, updated_at)
SELECT 'Статус заказа - Готов',
       E'✅ <b>Ваш заказ готов!</b>\n\n📦 Заказ #{order_number}\n🏪 {restaurant}\n\nПожалуйста, заберите ваш заказ.',
       'ORDER_STATUS',
       true,
       NOW(),
       NOW()
WHERE NOT EXISTS (SELECT 1 FROM telegram_templates WHERE name = 'Статус заказа - Готов');
