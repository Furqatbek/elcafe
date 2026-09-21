package com.elcafe.modules.sms.enums;

public enum CampaignStatus {
    DRAFT,          // Campaign created but not scheduled
    SCHEDULED,      // Campaign scheduled for future sending
    SENDING,        // Campaign is currently being sent
    PAUSED,         // Campaign paused by admin
    COMPLETED,      // All messages sent
    CANCELLED       // Campaign cancelled
}
