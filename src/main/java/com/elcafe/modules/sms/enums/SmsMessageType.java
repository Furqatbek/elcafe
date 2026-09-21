package com.elcafe.modules.sms.enums;

public enum SmsMessageType {
    CAMPAIGN,           // Sent as part of a marketing campaign
    AUTOMATION,         // Sent by automation rule
    TRANSACTIONAL,      // Order status, OTP, etc.
    MANUAL              // Manually sent by admin
}
