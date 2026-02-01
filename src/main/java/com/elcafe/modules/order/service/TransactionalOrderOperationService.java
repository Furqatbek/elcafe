package com.elcafe.modules.order.service;

import com.elcafe.modules.financial.service.RevenueService;
import com.elcafe.modules.inventory.service.InventoryService;
import com.elcafe.modules.kitchen.entity.KitchenOrder;
import com.elcafe.modules.kitchen.service.KitchenOrderService;
import com.elcafe.modules.notification.service.NotificationService;
import com.elcafe.modules.order.dto.pos.PaymentRequestDTO;
import com.elcafe.modules.order.dto.pos.PaymentResponseDTO;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.entity.OrderStatusHistory;
import com.elcafe.modules.order.entity.Payment;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.enums.PaymentMethod;
import com.elcafe.modules.order.enums.PaymentStatus;
import com.elcafe.modules.order.exception.OrderTransactionException;
import com.elcafe.modules.order.exception.OrderTransactionException.OrderFailureReason;
import com.elcafe.modules.order.exception.PaymentTransactionException;
import com.elcafe.modules.order.exception.PaymentTransactionException.PaymentFailureReason;
import com.elcafe.modules.order.repository.OrderRepository;
import com.elcafe.modules.order.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for orchestrating complex order operations that require atomic transaction boundaries.
 * <p>
 * This service ensures that multi-step operations (payment + status update + kitchen order)
 * are executed atomically - if any step fails, all changes are rolled back.
 * </p>
 * <p>
 * Key operations:
 * - processPaymentAndUpdateOrder: Atomically process payment and update order status
 * - acceptOrderAndCreateKitchenOrder: Atomically accept order and send to kitchen
 * - completeOrderWithPayment: Atomically complete order after payment confirmation
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionalOrderOperationService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final KitchenOrderService kitchenOrderService;
    private final InventoryService inventoryService;
    private final NotificationService notificationService;
    private final RevenueService revenueService;

    /**
     * Atomically process payment and update order status.
     * If payment processing succeeds but order update fails, everything is rolled back.
     *
     * @param orderId The order to process payment for
     * @param request The payment request details
     * @return Payment response with order and payment details
     * @throws PaymentTransactionException if payment processing fails (triggers rollback)
     * @throws OrderTransactionException if order update fails (triggers rollback)
     */
    @Transactional(rollbackFor = {PaymentTransactionException.class, OrderTransactionException.class, RuntimeException.class})
    public PaymentResponseDTO processPaymentAndUpdateOrder(Long orderId, PaymentRequestDTO request) {
        log.info("Starting atomic payment processing for order {}", orderId);

        // Step 1: Load and validate order
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new PaymentTransactionException(
                        "Order not found: " + orderId, orderId, PaymentFailureReason.ORDER_STATUS_INVALID));

        validateOrderForPayment(order);

        // Step 2: Calculate payment amounts
        BigDecimal tipAmount = request.getTipAmount() != null ? request.getTipAmount() : BigDecimal.ZERO;
        BigDecimal totalPayment = request.getAmount().add(tipAmount);

        BigDecimal changeDue = BigDecimal.ZERO;
        if (request.getMethod() == PaymentMethod.CASH && request.getAmountTendered() != null) {
            if (request.getAmountTendered().compareTo(totalPayment) < 0) {
                throw new PaymentTransactionException(
                        "Amount tendered is less than payment amount",
                        orderId, PaymentFailureReason.INVALID_AMOUNT);
            }
            changeDue = request.getAmountTendered().subtract(totalPayment);
        }

        // Step 3: Create and save payment
        Payment payment = createPayment(order, request, tipAmount, changeDue);
        Payment savedPayment = paymentRepository.save(payment);
        order.addPayment(savedPayment);

        // Step 4: Update order with tip if applicable
        if (tipAmount.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal currentTip = order.getTipAmount() != null ? order.getTipAmount() : BigDecimal.ZERO;
            order.setTipAmount(currentTip.add(tipAmount));
            order.setGrandTotal(order.getTotal().add(order.getTipAmount()));
        }

        // Step 5: Check if order is fully paid and update status
        if (order.isFullyPaid()) {
            order.setPaymentStatus(PaymentStatus.COMPLETED);
            log.info("Order {} is now fully paid", orderId);

            // Step 6: Record revenue (within same transaction)
            recordRevenue(order);
        }

        // Step 7: Save order (if this fails, everything rolls back)
        Order savedOrder = orderRepository.save(order);

        log.info("Atomic payment processing completed for order {}", orderId);

        // Step 8: Send notifications (after transaction, non-critical)
        sendPaymentNotification(savedOrder, savedPayment);

        return buildPaymentResponse(savedPayment, savedOrder);
    }

    /**
     * Atomically accept an order, check inventory, deduct stock, and create kitchen order.
     * If any step fails, all changes are rolled back.
     *
     * @param orderId    The order to accept
     * @param acceptedBy Who is accepting the order
     * @return The updated order
     * @throws OrderTransactionException if any step fails (triggers rollback)
     */
    @Transactional(rollbackFor = {OrderTransactionException.class, RuntimeException.class})
    public Order acceptOrderAndCreateKitchenOrder(Long orderId, String acceptedBy) {
        log.info("Starting atomic order acceptance for order {}", orderId);

        // Step 1: Load and validate order
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderTransactionException(
                        "Order not found: " + orderId, orderId, OrderFailureReason.ORDER_NOT_FOUND));

        if (order.getStatus() != OrderStatus.NEW && order.getStatus() != OrderStatus.PLACED) {
            throw new OrderTransactionException(
                    "Order cannot be accepted in current status: " + order.getStatus(),
                    orderId, order.getStatus(), OrderStatus.ACCEPTED,
                    OrderFailureReason.INVALID_STATUS_TRANSITION);
        }

        // Step 2: Check inventory availability
        if (!inventoryService.checkIngredientAvailability(order)) {
            List<String> missingIngredients = order.getItems().stream()
                    .flatMap(item -> inventoryService.getMissingIngredients(
                            item.getProductId(), item.getQuantity()).stream())
                    .collect(Collectors.toList());

            throw new OrderTransactionException(
                    "Insufficient ingredients: " + String.join(", ", missingIngredients),
                    orderId, order.getStatus(), OrderStatus.ACCEPTED,
                    OrderFailureReason.INSUFFICIENT_INVENTORY);
        }

        // Step 3: Deduct inventory
        try {
            inventoryService.deductIngredientsForOrder(order);
            log.info("Inventory deducted for order {}", order.getOrderNumber());
        } catch (Exception e) {
            throw new OrderTransactionException(
                    "Failed to deduct inventory: " + e.getMessage(),
                    orderId, order.getStatus(), OrderStatus.ACCEPTED,
                    OrderFailureReason.INVENTORY_DEDUCTION_FAILED, e);
        }

        // Step 4: Update order status
        order.setStatus(OrderStatus.ACCEPTED);
        OrderStatusHistory statusHistory = OrderStatusHistory.builder()
                .order(order)
                .status(OrderStatus.ACCEPTED)
                .changedBy(acceptedBy)
                .notes("Order accepted and sent to kitchen")
                .build();
        order.addStatusHistory(statusHistory);

        Order savedOrder = orderRepository.save(order);

        // Step 5: Create kitchen order
        try {
            KitchenOrder kitchenOrder = kitchenOrderService.createKitchenOrder(savedOrder);
            log.info("Kitchen order {} created for order {}", kitchenOrder.getId(), order.getOrderNumber());
        } catch (Exception e) {
            throw new OrderTransactionException(
                    "Failed to create kitchen order: " + e.getMessage(),
                    orderId, OrderStatus.ACCEPTED, OrderStatus.ACCEPTED,
                    OrderFailureReason.KITCHEN_ORDER_CREATION_FAILED, e);
        }

        log.info("Atomic order acceptance completed for order {}", orderId);

        // Send notifications (after transaction, non-critical)
        sendAcceptanceNotification(savedOrder);

        return savedOrder;
    }

    /**
     * Atomically complete an order after payment confirmation from gateway.
     * This is called after webhook confirms payment success.
     *
     * @param paymentIntentId The payment intent ID from the gateway
     * @return The updated order
     * @throws PaymentTransactionException if any step fails (triggers rollback)
     */
    @Transactional(rollbackFor = {PaymentTransactionException.class, OrderTransactionException.class, RuntimeException.class})
    public Order completeOrderWithPaymentConfirmation(String paymentIntentId) {
        log.info("Completing order for payment intent {}", paymentIntentId);

        // Step 1: Find order by payment intent
        Order order = orderRepository.findByPaymentIntentId(paymentIntentId)
                .orElseThrow(() -> new PaymentTransactionException(
                        "Order not found for payment intent: " + paymentIntentId,
                        null, PaymentFailureReason.ORDER_STATUS_INVALID));

        // Step 2: Update payment status
        Payment payment = order.getPayment();
        if (payment != null) {
            payment.setStatus(PaymentStatus.COMPLETED);
            payment.setCompletedAt(LocalDateTime.now());
            paymentRepository.save(payment);
        }

        // Step 3: Update order payment status
        order.setPaymentStatus(PaymentStatus.COMPLETED);

        // Step 4: Update order status to PLACED (ready for acceptance)
        if (order.getStatus() == OrderStatus.PENDING || order.getStatus() == OrderStatus.NEW) {
            order.setStatus(OrderStatus.PLACED);
            OrderStatusHistory statusHistory = OrderStatusHistory.builder()
                    .order(order)
                    .status(OrderStatus.PLACED)
                    .changedBy("PAYMENT_GATEWAY")
                    .notes("Payment confirmed via gateway")
                    .build();
            order.addStatusHistory(statusHistory);
        }

        // Step 5: Record revenue
        recordRevenue(order);

        Order savedOrder = orderRepository.save(order);

        log.info("Order {} completed with payment confirmation", order.getOrderNumber());

        return savedOrder;
    }

    /**
     * Atomically void an order and all associated payments.
     *
     * @param orderId    The order to void
     * @param reason     The reason for voiding
     * @param voidedBy   Who is voiding the order
     * @return The voided order
     */
    @Transactional(rollbackFor = {PaymentTransactionException.class, RuntimeException.class})
    public Order voidOrderWithPayments(Long orderId, String reason, String voidedBy) {
        log.info("Voiding order {} with all payments", orderId);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new PaymentTransactionException(
                        "Order not found: " + orderId, orderId, PaymentFailureReason.ORDER_STATUS_INVALID));

        // Step 1: Void all payments
        List<Payment> payments = paymentRepository.findByOrderId(orderId);
        for (Payment payment : payments) {
            payment.setStatus(PaymentStatus.VOIDED);
            payment.setRefundedAmount(payment.getTotalWithTip());
            payment.setRefundReason(reason);
            payment.setRefundedAt(LocalDateTime.now());
            paymentRepository.save(payment);
        }

        // Step 2: Update order status
        order.setStatus(OrderStatus.CANCELLED);
        order.setPaymentStatus(PaymentStatus.VOIDED);
        order.setCancelledAt(LocalDateTime.now());
        order.setCancelledBy(voidedBy);
        order.setCancellationReason(reason);

        OrderStatusHistory statusHistory = OrderStatusHistory.builder()
                .order(order)
                .status(OrderStatus.CANCELLED)
                .changedBy(voidedBy)
                .notes("Order voided: " + reason)
                .build();
        order.addStatusHistory(statusHistory);

        Order savedOrder = orderRepository.save(order);

        log.info("Order {} and {} payments voided", orderId, payments.size());

        return savedOrder;
    }

    // ==================== Private Helper Methods ====================

    private void validateOrderForPayment(Order order) {
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new PaymentTransactionException(
                    "Cannot process payment for cancelled order",
                    order.getId(), PaymentFailureReason.ORDER_STATUS_INVALID);
        }
        if (order.isFullyPaid()) {
            throw new PaymentTransactionException(
                    "Order is already fully paid",
                    order.getId(), PaymentFailureReason.ORDER_ALREADY_PAID);
        }
    }

    private Payment createPayment(Order order, PaymentRequestDTO request,
                                  BigDecimal tipAmount, BigDecimal changeDue) {
        return Payment.builder()
                .order(order)
                .method(request.getMethod())
                .status(PaymentStatus.COMPLETED)
                .amount(request.getAmount())
                .tipAmount(tipAmount)
                .amountTendered(request.getAmountTendered())
                .changeDue(changeDue)
                .transactionId(request.getTransactionId() != null ?
                        request.getTransactionId() : generateTransactionId(request.getMethod()))
                .paymentGateway(request.getPaymentGateway())
                .paymentDetails(request.getPaymentDetails())
                .processedBy(request.getProcessedBy())
                .splitNumber(request.getSplitNumber())
                .paidAt(LocalDateTime.now())
                .completedAt(LocalDateTime.now())
                .build();
    }

    private String generateTransactionId(PaymentMethod method) {
        String prefix = switch (method) {
            case CASH -> "CASH";
            case CARD, CREDIT_CARD, DEBIT_CARD -> "CARD";
            case MOBILE_PAYMENT -> "MOBILE";
            default -> "PAY";
        };
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private void recordRevenue(Order order) {
        try {
            revenueService.recordOrderRevenue(order);
            log.info("Revenue recorded for order {}", order.getOrderNumber());
        } catch (Exception e) {
            // Revenue recording failure should NOT rollback the transaction
            // as the payment was successful and order is valid
            log.error("Failed to record revenue for order {}: {} - continuing transaction",
                    order.getOrderNumber(), e.getMessage());
        }
    }

    /**
     * Send payment notification in a separate transaction to avoid rollback issues.
     * This runs AFTER the main transaction commits.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void sendPaymentNotification(Order order, Payment payment) {
        try {
            // Payment notifications are logged; order completion notifications
            // are sent when the order is marked as completed/delivered
            log.info("Payment {} processed for order {}", payment.getId(), order.getOrderNumber());
        } catch (Exception e) {
            log.error("Failed to send payment notification for order {}: {}",
                    order.getOrderNumber(), e.getMessage());
        }
    }

    /**
     * Send acceptance notification in a separate transaction to avoid rollback issues.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void sendAcceptanceNotification(Order order) {
        try {
            notificationService.notifyOrderAccepted(order);
        } catch (Exception e) {
            log.error("Failed to send acceptance notification for order {}: {}",
                    order.getOrderNumber(), e.getMessage());
        }
    }

    private PaymentResponseDTO buildPaymentResponse(Payment payment, Order order) {
        List<Payment> allPayments = paymentRepository.findByOrderId(order.getId());
        List<PaymentResponseDTO.PaymentSummary> paymentSummaries = allPayments.stream()
                .map(this::mapToPaymentSummary)
                .collect(Collectors.toList());

        BigDecimal grandTotal = order.getGrandTotal() != null ? order.getGrandTotal() : order.getTotal();

        return PaymentResponseDTO.builder()
                .paymentId(payment.getId())
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .method(payment.getMethod())
                .status(payment.getStatus())
                .amount(payment.getAmount())
                .tipAmount(payment.getTipAmount())
                .totalWithTip(payment.getTotalWithTip())
                .amountTendered(payment.getAmountTendered())
                .changeDue(payment.getChangeDue())
                .transactionId(payment.getTransactionId())
                .processedBy(payment.getProcessedBy())
                .splitNumber(payment.getSplitNumber())
                .paidAt(payment.getPaidAt())
                .orderTotal(order.getTotal())
                .orderGrandTotal(grandTotal)
                .totalPaid(order.getTotalPaid())
                .remainingBalance(order.getRemainingBalance())
                .orderFullyPaid(order.isFullyPaid())
                .allPayments(paymentSummaries)
                .build();
    }

    private PaymentResponseDTO.PaymentSummary mapToPaymentSummary(Payment payment) {
        return PaymentResponseDTO.PaymentSummary.builder()
                .id(payment.getId())
                .method(payment.getMethod())
                .status(payment.getStatus())
                .amount(payment.getAmount())
                .tipAmount(payment.getTipAmount())
                .refundedAmount(payment.getRefundedAmount())
                .splitNumber(payment.getSplitNumber())
                .paidAt(payment.getPaidAt())
                .build();
    }
}
