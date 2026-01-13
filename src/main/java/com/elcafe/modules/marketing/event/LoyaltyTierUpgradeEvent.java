package com.elcafe.modules.marketing.event;

import com.elcafe.modules.customer.entity.Customer;
import lombok.Getter;

/**
 * Event fired when a customer's loyalty tier is upgraded.
 * Triggers LOYALTY_MILESTONE automation.
 */
@Getter
public class LoyaltyTierUpgradeEvent extends MarketingEvent {

    private final Customer customer;
    private final String previousTier;
    private final String newTier;
    private final int totalPoints;

    public LoyaltyTierUpgradeEvent(Object source, Customer customer,
                                    String previousTier, String newTier, int totalPoints) {
        super(source, "LOYALTY_TIER_UPGRADE");
        this.customer = customer;
        this.previousTier = previousTier;
        this.newTier = newTier;
        this.totalPoints = totalPoints;
    }

    @Override
    public String getDescription() {
        return String.format("Customer %s upgraded from %s to %s tier (%d points)",
                customer.getFullName(),
                previousTier,
                newTier,
                totalPoints);
    }
}
