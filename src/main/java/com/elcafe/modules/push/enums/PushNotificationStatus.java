package com.elcafe.modules.push.enums;

/**
 * Status of push notification delivery
 */
public enum PushNotificationStatus {
    PENDING,        // Not yet sent
    SENT,           // Sent to push service
    DELIVERED,      // Confirmed delivered
    CLICKED,        // User clicked notification
    CLOSED,         // User closed notification
    FAILED,         // Delivery failed
    EXPIRED         // Subscription expired
}
