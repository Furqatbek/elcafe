package com.elcafe.modules.order.service;

import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.entity.OrderFinancialEvent;
import com.elcafe.modules.order.entity.OrderFinancialEvent.EventType;
import com.elcafe.modules.order.entity.OrderItem;
import com.elcafe.modules.order.entity.Payment;
import com.elcafe.modules.order.repository.OrderFinancialEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing order financial events.
 * Provides event sourcing capabilities to reconstruct order state at any point in time.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderFinancialEventService {

    private final OrderFinancialEventRepository eventRepository;

    /**
     * Record an order creation event.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OrderFinancialEvent recordOrderCreated(Order order, String performedBy) {
        OrderFinancialEvent event = OrderFinancialEvent.fromOrder(order, EventType.ORDER_CREATED, performedBy);
        event.setSequenceNumber(getNextSequenceNumber(order.getId()));
        event.setNotes("Order created with " + order.getItems().size() + " items");

        return eventRepository.save(event);
    }

    /**
     * Record item added event.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OrderFinancialEvent recordItemAdded(Order order, OrderItem item, String performedBy) {
        OrderFinancialEvent event = OrderFinancialEvent.fromOrder(order, EventType.ITEM_ADDED, performedBy);
        event.setSequenceNumber(getNextSequenceNumber(order.getId()));
        event.setItemId(item.getId());
        event.setItemName(item.getProductName());
        event.setItemQuantity(item.getQuantity());
        event.setItemPrice(item.getUnitPrice());
        event.setNotes("Added " + item.getQuantity() + "x " + item.getProductName());

        return eventRepository.save(event);
    }

    /**
     * Record item removed event.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OrderFinancialEvent recordItemRemoved(Order order, OrderItem item, String performedBy, String reason) {
        OrderFinancialEvent event = OrderFinancialEvent.fromOrder(order, EventType.ITEM_REMOVED, performedBy);
        event.setSequenceNumber(getNextSequenceNumber(order.getId()));
        event.setItemId(item.getId());
        event.setItemName(item.getProductName());
        event.setItemQuantity(item.getQuantity());
        event.setItemPrice(item.getUnitPrice());
        event.setReason(reason);
        event.setNotes("Removed " + item.getQuantity() + "x " + item.getProductName());

        return eventRepository.save(event);
    }

    /**
     * Record payment received event.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OrderFinancialEvent recordPaymentReceived(Order order, Payment payment, String performedBy) {
        OrderFinancialEvent event = OrderFinancialEvent.fromOrder(order, EventType.PAYMENT_RECEIVED, performedBy);
        event.setSequenceNumber(getNextSequenceNumber(order.getId()));
        event.setPaymentId(payment.getId());
        event.setPaymentMethod(payment.getMethod().name());
        event.setPaymentAmount(payment.getAmount());
        event.setTipAmount(payment.getTipAmount());
        event.setNotes(String.format("Payment of %s via %s", payment.getAmount(), payment.getMethod()));

        return eventRepository.save(event);
    }

    /**
     * Record refund issued event.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OrderFinancialEvent recordRefundIssued(Order order, Payment payment, BigDecimal refundAmount,
                                                   String performedBy, String reason) {
        EventType eventType = refundAmount.compareTo(payment.getAmount()) >= 0
                ? EventType.REFUND_ISSUED
                : EventType.PARTIAL_REFUND;

        OrderFinancialEvent event = OrderFinancialEvent.fromOrder(order, eventType, performedBy);
        event.setSequenceNumber(getNextSequenceNumber(order.getId()));
        event.setPaymentId(payment.getId());
        event.setPaymentMethod(payment.getMethod().name());
        event.setPaymentAmount(refundAmount.negate()); // Negative for refunds
        event.setRefundedAmount(refundAmount);
        event.setReason(reason);
        event.setNotes(String.format("Refund of %s for payment %d", refundAmount, payment.getId()));

        return eventRepository.save(event);
    }

    /**
     * Record discount applied event.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OrderFinancialEvent recordDiscountApplied(Order order, String discountType, BigDecimal discountAmount,
                                                      Long promotionId, String couponCode, String performedBy) {
        EventType eventType = switch (discountType) {
            case "COUPON" -> EventType.COUPON_APPLIED;
            case "PROMOTION" -> EventType.PROMOTION_APPLIED;
            case "HAPPY_HOUR" -> EventType.HAPPY_HOUR_APPLIED;
            default -> EventType.DISCOUNT_APPLIED;
        };

        OrderFinancialEvent event = OrderFinancialEvent.fromOrder(order, eventType, performedBy);
        event.setSequenceNumber(getNextSequenceNumber(order.getId()));
        event.setDiscount(discountAmount);
        event.setDiscountType(discountType);
        event.setPromotionId(promotionId);
        event.setCouponCode(couponCode);
        event.setNotes(String.format("Applied %s discount of %s", discountType, discountAmount));

        return eventRepository.save(event);
    }

    /**
     * Record service fee applied event.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OrderFinancialEvent recordServiceFeeApplied(Order order, BigDecimal feeAmount,
                                                        BigDecimal feePercent, String performedBy) {
        OrderFinancialEvent event = OrderFinancialEvent.fromOrder(order, EventType.SERVICE_FEE_APPLIED, performedBy);
        event.setSequenceNumber(getNextSequenceNumber(order.getId()));
        event.setServiceFee(feeAmount);
        event.setServiceFeePercent(feePercent);
        event.setNotes(String.format("Service fee of %s (%s%%)", feeAmount, feePercent));

        return eventRepository.save(event);
    }

    /**
     * Record tip added event.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OrderFinancialEvent recordTipAdded(Order order, BigDecimal tipAmount, String performedBy) {
        OrderFinancialEvent event = OrderFinancialEvent.fromOrder(order, EventType.TIP_ADDED, performedBy);
        event.setSequenceNumber(getNextSequenceNumber(order.getId()));
        event.setTipAmount(tipAmount);
        event.setNotes(String.format("Tip of %s added", tipAmount));

        return eventRepository.save(event);
    }

    /**
     * Record order cancelled event.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OrderFinancialEvent recordOrderCancelled(Order order, String performedBy, String reason) {
        OrderFinancialEvent event = OrderFinancialEvent.fromOrder(order, EventType.ORDER_CANCELLED, performedBy);
        event.setSequenceNumber(getNextSequenceNumber(order.getId()));
        event.setReason(reason);
        event.setNotes("Order cancelled: " + reason);

        return eventRepository.save(event);
    }

    /**
     * Record order voided event.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OrderFinancialEvent recordOrderVoided(Order order, String performedBy, String reason) {
        OrderFinancialEvent event = OrderFinancialEvent.fromOrder(order, EventType.ORDER_VOIDED, performedBy);
        event.setSequenceNumber(getNextSequenceNumber(order.getId()));
        event.setReason(reason);
        event.setNotes("Order voided: " + reason);

        return eventRepository.save(event);
    }

    /**
     * Get all financial events for an order.
     */
    @Transactional(readOnly = true)
    public List<OrderFinancialEvent> getOrderEventHistory(Long orderId) {
        return eventRepository.findByOrderIdOrderBySequenceNumber(orderId);
    }

    /**
     * Get order state at a specific point in time.
     * Returns the financial snapshot from the last event before or at the timestamp.
     */
    @Transactional(readOnly = true)
    public Optional<OrderFinancialEvent> getOrderStateAtTime(Long orderId, LocalDateTime timestamp) {
        List<OrderFinancialEvent> events = eventRepository.getEventsUpToTimestamp(orderId, timestamp);
        return events.isEmpty() ? Optional.empty() : Optional.of(events.get(0));
    }

    /**
     * Get all payment events for an order.
     */
    @Transactional(readOnly = true)
    public List<OrderFinancialEvent> getPaymentEvents(Long orderId) {
        return eventRepository.findByOrderIdAndEventTypeInOrderBySequenceNumber(
                orderId,
                List.of(EventType.PAYMENT_RECEIVED, EventType.PARTIAL_PAYMENT,
                        EventType.REFUND_ISSUED, EventType.PARTIAL_REFUND,
                        EventType.PAYMENT_VOIDED, EventType.TIP_ADDED)
        );
    }

    /**
     * Reconstruct order totals from events.
     * Useful for validation and auditing.
     */
    @Transactional(readOnly = true)
    public FinancialSummary reconstructFinancialSummary(Long orderId) {
        List<OrderFinancialEvent> events = getOrderEventHistory(orderId);

        if (events.isEmpty()) {
            return new FinancialSummary();
        }

        // Get the latest event for final state
        OrderFinancialEvent latest = events.get(events.size() - 1);

        // Calculate totals from payment events
        BigDecimal totalPayments = events.stream()
                .filter(e -> e.getEventType() == EventType.PAYMENT_RECEIVED ||
                        e.getEventType() == EventType.PARTIAL_PAYMENT)
                .map(OrderFinancialEvent::getPaymentAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalRefunds = events.stream()
                .filter(e -> e.getEventType() == EventType.REFUND_ISSUED ||
                        e.getEventType() == EventType.PARTIAL_REFUND)
                .map(OrderFinancialEvent::getRefundedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalTips = events.stream()
                .filter(e -> e.getEventType() == EventType.TIP_ADDED)
                .map(OrderFinancialEvent::getTipAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new FinancialSummary(
                latest.getSubtotal(),
                latest.getTotal(),
                latest.getGrandTotal(),
                totalPayments,
                totalRefunds,
                totalTips,
                events.size()
        );
    }

    private int getNextSequenceNumber(Long orderId) {
        return eventRepository.getMaxSequenceNumber(orderId).orElse(0) + 1;
    }

    public record FinancialSummary(
            BigDecimal subtotal,
            BigDecimal total,
            BigDecimal grandTotal,
            BigDecimal totalPayments,
            BigDecimal totalRefunds,
            BigDecimal totalTips,
            int eventCount
    ) {
        public FinancialSummary() {
            this(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0);
        }

        public BigDecimal getNetPayments() {
            return totalPayments.subtract(totalRefunds);
        }
    }
}
