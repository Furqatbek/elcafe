package com.elcafe.modules.sms.enums;

public enum AutomationTrigger {
    WELCOME,                // New customer registered
    BIRTHDAY,               // Customer's birthday
    INACTIVE_CUSTOMER,      // Customer inactive for X days
    FIRST_ORDER,            // Customer placed first order
    ORDER_COMPLETED,        // Order completed/delivered
    LOYALTY_MILESTONE,      // Customer reached loyalty tier
    REFERRAL_SIGNUP,        // Someone used customer's referral code
    REFERRAL_REWARD,        // Referral completed, reward earned
    CART_ABANDONED,         // Customer has items in cart (future)
    REVIEW_REQUEST          // Request review after order (future)
}
