-- Per-waiter notification preferences (P1-2). One row per waiter.
CREATE TABLE waiter_notification_preferences (
    id BIGSERIAL PRIMARY KEY,
    waiter_id BIGINT NOT NULL UNIQUE,
    order_updates BOOLEAN NOT NULL DEFAULT TRUE,
    table_ready BOOLEAN NOT NULL DEFAULT TRUE,
    kitchen_alerts BOOLEAN NOT NULL DEFAULT TRUE,
    new_orders BOOLEAN NOT NULL DEFAULT TRUE,
    payment_notifications BOOLEAN NOT NULL DEFAULT TRUE,
    system_alerts BOOLEAN NOT NULL DEFAULT TRUE,
    shift_reminders BOOLEAN NOT NULL DEFAULT TRUE,
    sound_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    vibration_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    quiet_hours_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    quiet_hours_start VARCHAR(5),
    quiet_hours_end VARCHAR(5)
);

CREATE UNIQUE INDEX idx_waiter_pref_waiter ON waiter_notification_preferences(waiter_id);
