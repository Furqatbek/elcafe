package com.elcafe.modules.referral.enums;

public enum ReferralStatus {
    PENDING,    // Referee signed up but hasn't completed qualifying order
    COMPLETED,  // Referee completed qualifying order, rewards given
    EXPIRED,    // Referral expired before completion
    CANCELLED   // Referral was cancelled
}
