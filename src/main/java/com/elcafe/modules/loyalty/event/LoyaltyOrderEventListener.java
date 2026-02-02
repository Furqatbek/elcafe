package com.elcafe.modules.loyalty.event;

import com.elcafe.modules.loyalty.service.LoyaltyService;
import com.elcafe.modules.marketing.event.OrderCompletedEvent;
import com.elcafe.modules.order.entity.Order;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

import java.math.BigDecimal;

/**
 * Event listener for order lifecycle events.
 * Triggers loyalty operations automatically.
 *
 * Listens for marketing module's OrderCompletedEvent to process loyalty points.
 * Also listens for OrderRefundedEvent to reverse loyalty points on refunds.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoyaltyOrderEventListener {

    private final LoyaltyService loyaltyService;

    /**
     * Listen for order completion events from marketing module.
     * Triggers after transaction commits to ensure order is persisted.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void handleOrderCompleted(OrderCompletedEvent event) {
        try {
            log.info("Handling order completed event for order {}", event.getOrder().getId());
            loyaltyService.processOrderCompletion(event.getOrder());
        } catch (Exception e) {
            log.error("Error processing loyalty for completed order {}", event.getOrder().getId(), e);
            // Don't throw exception - loyalty processing should not fail the order
        }
    }

    /**
     * Listen for order refund events.
     * Processes loyalty point reversals when orders are refunded.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void handleOrderRefunded(OrderRefundedEvent event) {
        try {
            log.info("Handling order refund event for order {}", event.getOrder().getId());
            loyaltyService.processRefund(event.getOrder(), event.getRefundAmount());
        } catch (Exception e) {
            log.error("Error processing loyalty refund for order {}", event.getOrder().getId(), e);
        }
    }

    /**
     * Event published when an order is refunded.
     * This event should be published by the payment/refund service.
     */
    public static class OrderRefundedEvent {
        private final Order order;
        private final BigDecimal refundAmount;

        public OrderRefundedEvent(Order order, BigDecimal refundAmount) {
            this.order = order;
            this.refundAmount = refundAmount;
        }

        public Order getOrder() {
            return order;
        }

        public BigDecimal getRefundAmount() {
            return refundAmount;
        }
    }
}
