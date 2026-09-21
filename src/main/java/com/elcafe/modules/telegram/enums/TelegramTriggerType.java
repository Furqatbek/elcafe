package com.elcafe.modules.telegram.enums;

public enum TelegramTriggerType {
    BOT_START("User starts the bot"),
    NEW_SUBSCRIBER("New subscriber joined"),
    INACTIVE_USER("User inactive for specified period"),
    BIRTHDAY("User birthday"),
    REFERRAL_REWARD("Referral completed"),
    ORDER_STATUS("Order status changed"),
    LOYALTY_MILESTONE("Customer reached loyalty milestone");

    private final String description;

    TelegramTriggerType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
