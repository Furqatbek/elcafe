package com.elcafe.modules.loyalty.event;

import com.elcafe.modules.loyalty.service.LoyaltyService;
import com.elcafe.modules.order.entity.Order;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

/**
 * Event listener for order lifecycle events
 * Triggers loyalty operations automatically
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventListener {

    private final LoyaltyService loyaltyService;

    /**
     * Listen for order completion events
     * Triggers after transaction commits to ensure order is persisted
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
     * Listen for order refund events
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
     * Event published when an order is completed
     */
    public static class OrderCompletedEvent {
        private final Order order;

        public OrderCompletedEvent(Order order) {
            this.order = order;
        }

        public Order getOrder() {
            return order;
        }
    }

    /**
     * Event published when an order is refunded
     */
    public static class OrderRefundedEvent {
        private final Order order;
        private final java.math.BigDecimal refundAmount;

        public OrderRefundedEvent(Order order, java.math.BigDecimal refundAmount) {
            this.order = order;
            this.refundAmount = refundAmount;
        }

        public Order getOrder() {
            return order;
        }

        public java.math.BigDecimal getRefundAmount() {
            return refundAmount;
        }
    }
}
