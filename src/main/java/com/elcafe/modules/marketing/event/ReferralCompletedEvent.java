package com.elcafe.modules.marketing.event;

import com.elcafe.modules.customer.entity.Customer;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * Event fired when a referral is completed and rewards are earned.
 * Triggers REFERRAL_REWARD and REFERRAL_SIGNUP automations.
 */
@Getter
public class ReferralCompletedEvent extends MarketingEvent {

    private final Customer referrer;
    private final Customer referee;
    private final BigDecimal referrerReward;
    private final BigDecimal refereeReward;

    public ReferralCompletedEvent(Object source, Customer referrer, Customer referee,
                                   BigDecimal referrerReward, BigDecimal refereeReward) {
        super(source, "REFERRAL_COMPLETED");
        this.referrer = referrer;
        this.referee = referee;
        this.referrerReward = referrerReward;
        this.refereeReward = refereeReward;
    }

    @Override
    public String getDescription() {
        return String.format("Referral completed: %s %s referred %s %s (Rewards: %s / %s)",
                referrer.getFirstName(),
                referrer.getLastName(),
                referee.getFirstName(),
                referee.getLastName(),
                referrerReward,
                refereeReward);
    }
}
