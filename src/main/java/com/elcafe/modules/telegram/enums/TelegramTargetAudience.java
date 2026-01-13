package com.elcafe.modules.telegram.enums;

public enum TelegramTargetAudience {
    ALL,                // All active subscribers
    ACTIVE,             // Active subscribers (interacted recently)
    INACTIVE,           // Inactive subscribers
    LINKED_CUSTOMERS,   // Subscribers linked to customer accounts
    CUSTOM              // Custom selection
}
