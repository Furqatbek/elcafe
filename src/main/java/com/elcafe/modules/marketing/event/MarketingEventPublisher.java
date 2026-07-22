package com.elcafe.modules.marketing.event;

import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.order.entity.Order;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Publisher for marketing automation events.
 * Use this service to publish events from your services to trigger automations.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarketingEventPublisher {

    private final ApplicationEventPublisher eventPublisher;

    /**
     * Publish event when a new customer registers.
     */
    public void publishCustomerRegistered(Customer customer) {
        log.debug("Publishing CustomerRegisteredEvent for customer {}", customer.getId());
        eventPublisher.publishEvent(new CustomerRegisteredEvent(this, customer));
    }

    /**
     * Publish event when an order is completed.
     */
    public void publishOrderCompleted(Order order, Customer customer, boolean isFirstOrder) {
        log.debug("Publishing OrderCompletedEvent for order {}", order.getId());
        eventPublisher.publishEvent(new OrderCompletedEvent(this, order, customer, isFirstOrder));
    }

    /**
     * Publish event when a referral is completed.
     */
    public void publishReferralCompleted(Customer referrer, Customer referee,
                                          BigDecimal referrerReward, BigDecimal refereeReward) {
        log.debug("Publishing ReferralCompletedEvent for referrer {} and referee {}",
                referrer.getId(), referee.getId());
        eventPublisher.publishEvent(new ReferralCompletedEvent(
                this, referrer, referee, referrerReward, refereeReward));
    }

    /**
     * Publish event when a customer's loyalty tier is upgraded.
     */
    public void publishLoyaltyTierUpgrade(Customer customer, String previousTier,
                                           String newTier, int totalPoints) {
        log.debug("Publishing LoyaltyTierUpgradeEvent for customer {}", customer.getId());
        eventPublisher.publishEvent(new LoyaltyTierUpgradeEvent(
                this, customer, previousTier, newTier, totalPoints));
    }
}
