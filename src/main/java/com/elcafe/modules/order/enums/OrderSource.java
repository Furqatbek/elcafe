package com.elcafe.modules.order.enums;

public enum OrderSource {
    TELEGRAM_BOT,
    WEBSITE,
    ADMIN_PANEL,
    MOBILE_APP,
    PHONE_CALL,
    WALK_IN,
    WAITER,
    SELF_SERVICE,
    INSTAGRAM_BOT,
    /** Pushed in over the partner API by a delivery aggregator (V187). */
    AGGREGATOR,
    OTHER
}
