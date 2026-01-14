-- Stock Alert Subscriptions Table
-- Stores Telegram chat subscriptions for stock alerts

CREATE TABLE IF NOT EXISTS stock_alert_subscriptions (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    telegram_chat_id BIGINT NOT NULL,
    subscriber_name VARCHAR(100),
    alert_on_low_stock BOOLEAN NOT NULL DEFAULT TRUE,
    alert_on_reorder BOOLEAN NOT NULL DEFAULT TRUE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    last_alert_sent_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_subscription_restaurant_chat UNIQUE (restaurant_id, telegram_chat_id)
);

-- Indexes for efficient querying
CREATE INDEX idx_stock_alert_subs_restaurant ON stock_alert_subscriptions(restaurant_id);
CREATE INDEX idx_stock_alert_subs_active ON stock_alert_subscriptions(active);
CREATE INDEX idx_stock_alert_subs_last_alert ON stock_alert_subscriptions(last_alert_sent_at);

-- Comments
COMMENT ON TABLE stock_alert_subscriptions IS 'Telegram chat subscriptions for stock alerts';
COMMENT ON COLUMN stock_alert_subscriptions.telegram_chat_id IS 'Telegram chat ID to send alerts to';
COMMENT ON COLUMN stock_alert_subscriptions.alert_on_low_stock IS 'Send alerts when stock is below minimum level';
COMMENT ON COLUMN stock_alert_subscriptions.alert_on_reorder IS 'Send alerts when stock is at reorder level';
COMMENT ON COLUMN stock_alert_subscriptions.last_alert_sent_at IS 'Timestamp of last alert sent (for cooldown)';
