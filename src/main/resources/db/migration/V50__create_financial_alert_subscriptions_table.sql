-- Create financial alert subscriptions table for daily financial reports via Telegram
CREATE TABLE financial_alert_subscriptions (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    telegram_chat_id BIGINT NOT NULL,
    subscriber_name VARCHAR(100),
    alert_daily_revenue BOOLEAN NOT NULL DEFAULT true,
    alert_daily_expenses BOOLEAN NOT NULL DEFAULT true,
    alert_daily_profit BOOLEAN NOT NULL DEFAULT true,
    report_time TIME DEFAULT '23:00:00',
    active BOOLEAN NOT NULL DEFAULT true,
    last_report_sent_at TIMESTAMP,
    last_report_date DATE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT uk_financial_alert_restaurant_chat UNIQUE (restaurant_id, telegram_chat_id)
);

-- Create indexes for efficient queries
CREATE INDEX idx_financial_alert_restaurant ON financial_alert_subscriptions(restaurant_id);
CREATE INDEX idx_financial_alert_active ON financial_alert_subscriptions(active);
CREATE INDEX idx_financial_alert_report_time ON financial_alert_subscriptions(report_time);
CREATE INDEX idx_financial_alert_last_report_date ON financial_alert_subscriptions(last_report_date);

COMMENT ON TABLE financial_alert_subscriptions IS 'Stores Telegram subscriptions for daily financial reports';
COMMENT ON COLUMN financial_alert_subscriptions.report_time IS 'Time of day to send the daily report (default 23:00)';
COMMENT ON COLUMN financial_alert_subscriptions.last_report_date IS 'Date of last sent report to prevent duplicate sends';
