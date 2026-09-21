package com.elcafe.modules.telegram.enums;

public enum TelegramMessageType {
    CAMPAIGN,           // Sent as part of a broadcast campaign
    AUTOMATION,         // Sent by automation rule
    NOTIFICATION,       // System notifications (order status, etc.)
    MANUAL              // Manually sent by admin
}
