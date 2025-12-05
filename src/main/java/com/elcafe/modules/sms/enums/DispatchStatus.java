package com.elcafe.modules.sms.enums;

/**
 * SMS dispatch status in Eskiz.uz system
 */
public enum DispatchStatus {
    /**
     * Dispatch is pending
     */
    PENDING,

    /**
     * Dispatch is in progress
     */
    PROCESSING,

    /**
     * Dispatch has been completed
     */
    COMPLETED,

    /**
     * Dispatch has been cancelled
     */
    CANCELLED,

    /**
     * Dispatch failed
     */
    FAILED
}
