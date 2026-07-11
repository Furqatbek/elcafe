package com.elcafe.modules.marketing.event;

import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.sms.enums.AutomationTrigger;
import com.elcafe.modules.sms.service.SmsAutomationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Event listener for marketing automation triggers.
 * Listens to domain events and triggers appropriate SMS/marketing automations.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarketingAutomationListener {

    private final SmsAutomationService smsAutomationService;

    /**
     * Handle new customer registration - send welcome message.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void handleCustomerRegistered(CustomerRegisteredEvent event) {
        Customer customer = event.getCustomer();
        log.info("Marketing automation triggered for new customer registration: {}", customer.getId());

        try {
            // Trigger welcome SMS
            smsAutomationService.triggerWelcomeSms(customer);
            log.debug("Welcome SMS triggered for customer {}", customer.getId());
        } catch (Exception e) {
            log.error("Failed to trigger welcome automation for customer {}: {}",
                    customer.getId(), e.getMessage());
        }
    }

    /**
     * Handle order completion - send thank you message and first order bonus if applicable.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void handleOrderCompleted(OrderCompletedEvent event) {
        Customer customer = event.getCustomer();
        if (customer == null || event.getOrder() == null) {
            // Walk-in orders carry no customer; nothing to message. (OrderCompletionEvents never
            // publishes without one — this protects against any future publisher that might.)
            return;
        }
        log.info("Marketing automation triggered for order completion: {} (customer: {})",
                event.getOrder().getId(), customer.getId());

        try {
            // Build context with order details
            Map<String, Object> context = new HashMap<>();
            context.put("orderNumber", event.getOrder().getOrderNumber());
            context.put("orderTotal", event.getOrderTotal());
            context.put("isFirstOrder", event.isFirstOrder());

            // Trigger order completion SMS
            smsAutomationService.triggerAutomation(
                    AutomationTrigger.ORDER_COMPLETED,
                    customer,
                    context
            );

            // If first order, also trigger first order automation
            if (event.isFirstOrder()) {
                smsAutomationService.triggerAutomation(
                        AutomationTrigger.FIRST_ORDER,
                        customer,
                        context
                );
                log.debug("First order automation triggered for customer {}", customer.getId());
            }

        } catch (Exception e) {
            log.error("Failed to trigger order completion automation for customer {}: {}",
                    customer.getId(), e.getMessage());
        }
    }

    /**
     * Handle referral completion - notify both referrer and referee.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void handleReferralCompleted(ReferralCompletedEvent event) {
        log.info("Marketing automation triggered for referral completion: referrer={}, referee={}",
                event.getReferrer().getId(), event.getReferee().getId());

        try {
            // Notify referrer about successful referral
            smsAutomationService.triggerReferralRewardSms(
                    event.getReferrer(),
                    event.getReferrerReward().toString()
            );

            // Notify referee about their welcome bonus (if applicable)
            if (event.getRefereeReward() != null && event.getRefereeReward().compareTo(BigDecimal.ZERO) > 0) {
                Map<String, Object> context = new HashMap<>();
                context.put("rewardAmount", event.getRefereeReward());
                context.put("referrerName", event.getReferrer().getFirstName() + " " + event.getReferrer().getLastName());

                smsAutomationService.triggerAutomation(
                        AutomationTrigger.REFERRAL_SIGNUP,
                        event.getReferee(),
                        context
                );
            }

        } catch (Exception e) {
            log.error("Failed to trigger referral automation: {}", e.getMessage());
        }
    }

    /**
     * Handle loyalty tier upgrade - congratulate customer on new tier.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void handleLoyaltyTierUpgrade(LoyaltyTierUpgradeEvent event) {
        Customer customer = event.getCustomer();
        log.info("Marketing automation triggered for loyalty tier upgrade: {} -> {} (customer: {})",
                event.getPreviousTier(), event.getNewTier(), customer.getId());

        try {
            Map<String, Object> context = new HashMap<>();
            context.put("previousTier", event.getPreviousTier());
            context.put("newTier", event.getNewTier());
            context.put("totalPoints", event.getTotalPoints());

            smsAutomationService.triggerAutomation(
                    AutomationTrigger.LOYALTY_MILESTONE,
                    customer,
                    context
            );

        } catch (Exception e) {
            log.error("Failed to trigger loyalty upgrade automation for customer {}: {}",
                    customer.getId(), e.getMessage());
        }
    }
}
