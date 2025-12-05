package com.elcafe.modules.sms.enums;

/**
 * SMS message status in Eskiz.uz system
 */
public enum MessageStatus {
    /**
     * Message is waiting to be sent
     */
    WAITING,

    /**
     * Message has been sent
     */
    SENT,

    /**
     * Message has been delivered to recipient
     */
    DELIVERED,

    /**
     * Message delivery failed
     */
    FAILED,

    /**
     * Message has been rejected
     */
    REJECTED,

    /**
     * Unknown status
     */
    UNKNOWN
}
