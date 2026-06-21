package com.elcafe.modules.order.service;

import com.elcafe.common.audit.entity.AuditAction;
import com.elcafe.common.audit.service.AuditService;
import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.modules.order.dto.*;
import com.elcafe.modules.order.dto.pos.PaymentRequestDTO;
import com.elcafe.modules.order.dto.pos.PaymentResponseDTO;
import com.elcafe.modules.order.dto.pos.RefundRequestDTO;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.entity.OrderItem;
import com.elcafe.modules.order.entity.Payment;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.enums.PaymentMethod;
import com.elcafe.modules.order.enums.PaymentStatus;
import com.elcafe.modules.order.exception.PaymentTransactionException;
import com.elcafe.modules.order.exception.PaymentTransactionException.PaymentFailureReason;
import com.elcafe.modules.order.repository.OrderRepository;
import com.elcafe.modules.order.repository.PaymentRepository;
import com.elcafe.modules.financial.service.RevenueRecordingService;
import com.elcafe.modules.financial.service.RevenueService;
import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final RevenueService revenueService;
    private final RevenueRecordingService revenueRecordingService;
    private final PaymentIdempotencyService idempotencyService;
    private final AuditService auditService;
    private final POSTableService posTableService;
    private final RestaurantAuthorizationService restaurantAuthorizationService;

    @Transactional(readOnly = true)
    public Page<PaymentResponse> getAllPayments(Pageable pageable) {
        return paymentRepository.findAll(pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPaymentById(Long orderId, Long paymentId) {
        Payment payment = paymentRepository.findByIdAndOrderId(paymentId, orderId)
                .orElseThrow(() -> new RuntimeException("Payment not found with id: " + paymentId + " for order: " + orderId));
        restaurantAuthorizationService.checkAccess(payment.getOrder().getRestaurant().getId()); // §3.3: tenant guard
        return toResponse(payment);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPaymentByOrderId(Long orderId) {
        List<Payment> payments = paymentRepository.findByOrderId(orderId);
        if (payments.isEmpty()) {
            throw new RuntimeException("Payment not found for order: " + orderId);
        }
        // Return the first/primary payment (for backward compatibility)
        restaurantAuthorizationService.checkAccess(payments.get(0).getOrder().getRestaurant().getId()); // §3.3: tenant guard
        return toResponse(payments.get(0));
    }

    @Transactional(readOnly = true)
    public Page<PaymentResponse> getPaymentsByStatus(PaymentStatus status, Pageable pageable) {
        return paymentRepository.findByStatus(status, pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<PaymentResponse> getPaymentsByMethod(PaymentMethod method, Pageable pageable) {
        return paymentRepository.findByMethod(method, pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPaymentByTransactionId(String transactionId) {
        Payment payment = paymentRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new RuntimeException("Payment not found with transaction ID: " + transactionId));
        restaurantAuthorizationService.checkAccess(payment.getOrder().getRestaurant().getId()); // §3.3: tenant guard
        return toResponse(payment);
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> getPaymentsByStatusAndDateRange(
            PaymentStatus status,
            OffsetDateTime startDate,
            OffsetDateTime endDate
    ) {
        return paymentRepository.findByStatusAndDateRange(status, startDate, endDate).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public PaymentResponse createPayment(Long orderId, CreatePaymentRequest request) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found with id: " + orderId));

        // Check if payment already exists for this order (for single-payment flows)
        if (!paymentRepository.findByOrderId(orderId).isEmpty()) {
            throw new RuntimeException("Payment already exists for order: " + orderId);
        }

        Payment payment = Payment.builder()
                .order(order)
                .method(request.getMethod())
                .status(request.getStatus() != null ? request.getStatus() : PaymentStatus.PENDING)
                .amount(request.getAmount())
                .transactionId(request.getTransactionId())
                .paymentGateway(request.getPaymentGateway())
                .paymentDetails(request.getPaymentDetails())
                .processedBy(getCurrentUsername())
                .build();

        // Set paidAt if status is COMPLETED
        if (payment.getStatus() == PaymentStatus.COMPLETED) {
            payment.setPaidAt(OffsetDateTime.now());
        }

        Payment saved;
        try {
            saved = paymentRepository.save(payment);
        } catch (DataIntegrityViolationException e) {
            // Handle race condition: transaction ID uniqueness enforced by DB constraint
            if (e.getMessage() != null && e.getMessage().contains("transaction_id")) {
                throw new RuntimeException("Payment with transaction ID '" + request.getTransactionId() + "' already exists");
            }
            throw e;
        }

        log.info("Created payment: {} for order: {}", saved.getId(), orderId);
        return toResponse(saved);
    }

    @Transactional
    public PaymentResponse updatePayment(Long orderId, Long paymentId, UpdatePaymentRequest request) {
        Payment payment = paymentRepository.findByIdAndOrderId(paymentId, orderId)
                .orElseThrow(() -> new RuntimeException("Payment not found with id: " + paymentId + " for order: " + orderId));

        if (request.getMethod() != null) payment.setMethod(request.getMethod());

        if (request.getStatus() != null) {
            PaymentStatus oldStatus = payment.getStatus();
            payment.setStatus(request.getStatus());

            // Set paidAt when status changes to COMPLETED
            if (request.getStatus() == PaymentStatus.COMPLETED && oldStatus != PaymentStatus.COMPLETED) {
                payment.setPaidAt(OffsetDateTime.now());
            }
            // Clear paidAt if status is changed from COMPLETED to something else
            else if (request.getStatus() != PaymentStatus.COMPLETED && oldStatus == PaymentStatus.COMPLETED) {
                payment.setPaidAt(null);
            }
        }

        if (request.getAmount() != null) payment.setAmount(request.getAmount());

        if (request.getTransactionId() != null) {
            String currentTransactionId = payment.getTransactionId();
            if ((currentTransactionId == null || !currentTransactionId.equals(request.getTransactionId())) &&
                paymentRepository.existsByTransactionId(request.getTransactionId())) {
                throw new RuntimeException("Payment with transaction ID '" + request.getTransactionId() + "' already exists");
            }
            payment.setTransactionId(request.getTransactionId());
        }

        if (request.getPaymentGateway() != null) payment.setPaymentGateway(request.getPaymentGateway());
        if (request.getPaymentDetails() != null) payment.setPaymentDetails(request.getPaymentDetails());

        Payment updated = paymentRepository.save(payment);
        log.info("Updated payment: {} for order: {}", updated.getId(), orderId);
        return toResponse(updated);
    }

    /**
     * Soft delete a payment. Financial records should never be hard deleted for audit compliance.
     *
     * @param orderId The order ID
     * @param paymentId The payment ID to delete
     * @param deletedBy Username of the person performing the deletion
     */
    @Transactional
    public void deletePayment(Long orderId, Long paymentId, String deletedBy) {
        Payment payment = paymentRepository.findByIdAndOrderId(paymentId, orderId)
                .orElseThrow(() -> new RuntimeException("Payment not found with id: " + paymentId + " for order: " + orderId));

        if (payment.isDeleted()) {
            throw new RuntimeException("Payment has already been deleted");
        }

        Order order = payment.getOrder();

        // Use soft delete instead of hard delete for audit compliance
        payment.softDelete(deletedBy);
        paymentRepository.save(payment);

        // Audit log the deletion
        auditService.logFinancialOperation(
                AuditAction.PAYMENT_CANCELLED,
                orderId,
                order.getOrderNumber(),
                order.getRestaurant() != null ? order.getRestaurant().getId() : null,
                payment.getAmount(),
                "UZS",
                String.format("Payment %d soft deleted by %s. Method: %s, TransactionId: %s",
                        paymentId, deletedBy, payment.getMethod(), payment.getTransactionId())
        );

        log.info("Soft deleted payment: {} for order: {} by user: {}", payment.getId(), orderId, deletedBy);
    }

    private PaymentResponse toResponse(Payment payment) {
        return PaymentResponse.builder()
                .id(payment.getId())
                .orderId(payment.getOrder().getId())
                .orderNumber(payment.getOrder().getOrderNumber())
                .method(payment.getMethod())
                .status(payment.getStatus())
                .amount(payment.getAmount())
                .tipAmount(payment.getTipAmount())
                .totalWithTip(payment.getTotalWithTip())
                .refundedAmount(payment.getRefundedAmount())
                .netAmount(payment.getNetAmount())
                .amountTendered(payment.getAmountTendered())
                .changeDue(payment.getChangeDue())
                .transactionId(payment.getTransactionId())
                .paymentGateway(payment.getPaymentGateway())
                .paymentDetails(payment.getPaymentDetails())
                .refundReason(payment.getRefundReason())
                .processedBy(payment.getProcessedBy())
                .paidAt(payment.getPaidAt() != null ? payment.getPaidAt().toLocalDateTime() : null)
                .completedAt(payment.getCompletedAt() != null ? payment.getCompletedAt().toLocalDateTime() : null)
                .refundedAt(payment.getRefundedAt() != null ? payment.getRefundedAt().toLocalDateTime() : null)
                .createdAt(payment.getCreatedAt() != null ? payment.getCreatedAt().toLocalDateTime() : null)
                .updatedAt(payment.getUpdatedAt() != null ? payment.getUpdatedAt().toLocalDateTime() : null)
                .build();
    }

    // ==================== POS Payment Methods ====================

    /**
     * Process a POS payment with tip support.
     * <p>
     * Transaction boundary ensures atomicity: if payment creation succeeds but order update fails,
     * everything is rolled back. For complex payment flows requiring external gateway integration,
     * use {@link TransactionalOrderOperationService#processPaymentAndUpdateOrder(Long, PaymentRequestDTO)}.
     * </p>
     *
     * @param orderId The order to process payment for
     * @param request The payment request details
     * @return Payment response with order and payment details
     * @throws PaymentTransactionException if payment processing fails (triggers rollback)
     */
    @Transactional(rollbackFor = {PaymentTransactionException.class, RuntimeException.class})
    public PaymentResponseDTO processPOSPayment(Long orderId, PaymentRequestDTO request) {
        log.info("Processing POS payment for order {}: method={}, amount={}, tip={}",
                orderId, request.getMethod(), request.getAmount(), request.getTipAmount());

        // If order is already fully paid, return current payment summary instead of error.
        // This handles the case where the frontend retries after a successful payment
        // (e.g., due to network timeout or UI not updating). Returning the summary
        // allows the frontend to see the order is paid and close/release the table.
        Order existingOrder = orderRepository.findById(orderId).orElse(null);
        if (existingOrder != null && existingOrder.isFullyPaid()) {
            log.info("Order {} is already fully paid, returning existing payment summary", orderId);
            return getPOSPaymentSummary(orderId);
        }

        // Check for duplicate transaction ID (idempotency)
        if (request.getTransactionId() != null) {
            Long existingOrderId = idempotencyService.getProcessedOrderForTransaction(request.getTransactionId());
            if (existingOrderId != null) {
                log.info("Duplicate payment request detected for transaction {}, returning existing payment",
                        request.getTransactionId());
                // Return the existing payment summary instead of creating a duplicate
                return getPOSPaymentSummary(existingOrderId);
            }
        }

        // Acquire lock to prevent concurrent payments for the same order
        if (!idempotencyService.acquireOrderPaymentLock(orderId)) {
            throw new PaymentTransactionException(
                    "Another payment is being processed for this order. Please wait and retry.",
                    orderId, PaymentFailureReason.CONCURRENT_MODIFICATION);
        }

        try {
            return doProcessPOSPayment(orderId, request);
        } finally {
            idempotencyService.releaseOrderPaymentLock(orderId);
        }
    }

    /**
     * Internal method to process POS payment after idempotency checks.
     */
    private PaymentResponseDTO doProcessPOSPayment(Long orderId, PaymentRequestDTO request) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new PaymentTransactionException(
                        "Order not found: " + orderId, orderId, PaymentFailureReason.ORDER_STATUS_INVALID));

        // Validate order can accept payment
        validateOrderForPayment(order, orderId);

        // Calculate total payment with tip
        BigDecimal tipAmount = request.getTipAmount() != null ? request.getTipAmount() : BigDecimal.ZERO;
        BigDecimal totalPayment = request.getAmount().add(tipAmount);

        // For cash payments, calculate change
        BigDecimal changeDue = BigDecimal.ZERO;
        if (request.getMethod() == PaymentMethod.CASH && request.getAmountTendered() != null) {
            if (request.getAmountTendered().compareTo(totalPayment) < 0) {
                throw new PaymentTransactionException(
                        "Amount tendered is less than payment amount",
                        orderId, PaymentFailureReason.INVALID_AMOUNT);
            }
            changeDue = request.getAmountTendered().subtract(totalPayment);
        }

        // Create payment record
        Payment payment = Payment.builder()
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
                .paidAt(OffsetDateTime.now())
                .completedAt(OffsetDateTime.now())
                .build();

        Payment savedPayment;
        try {
            savedPayment = paymentRepository.save(payment);
        } catch (Exception e) {
            throw new PaymentTransactionException(
                    "Failed to save payment: " + e.getMessage(),
                    orderId, null, request.getTransactionId(),
                    PaymentFailureReason.DATABASE_ERROR, e);
        }

        order.addPayment(savedPayment);

        // Update order tip if this payment includes tip
        if (tipAmount.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal currentTip = order.getTipAmount() != null ? order.getTipAmount() : BigDecimal.ZERO;
            order.setTipAmount(currentTip.add(tipAmount));
            order.setGrandTotal(order.getTotal().add(order.getTipAmount()));
        }

        // Check if order is fully paid
        boolean orderFullyPaid = order.isFullyPaid();
        if (orderFullyPaid) {
            order.setPaymentStatus(PaymentStatus.COMPLETED);
            order.setStatus(OrderStatus.DELIVERED);
            order.setCompletedAt(OffsetDateTime.now());
            log.info("Order {} is now fully paid - marking DELIVERED and releasing tables", orderId);

            // Record revenue when order is fully paid (non-critical - logged but doesn't rollback)
            recordRevenueNonCritical(order, orderId);
        }

        try {
            orderRepository.save(order);
        } catch (OptimisticLockException | ObjectOptimisticLockingFailureException e) {
            log.warn("Concurrent modification detected for order {}, retrying may be needed", orderId);
            throw new PaymentTransactionException(
                    "Order was modified by another transaction. Please retry.",
                    orderId, savedPayment.getId(), savedPayment.getTransactionId(),
                    PaymentFailureReason.CONCURRENT_MODIFICATION, e);
        } catch (Exception e) {
            throw new PaymentTransactionException(
                    "Failed to update order after payment: " + e.getMessage(),
                    orderId, savedPayment.getId(), savedPayment.getTransactionId(),
                    PaymentFailureReason.DATABASE_ERROR, e);
        }

        // Release tables after order is saved so the table status update is part of the same transaction
        if (orderFullyPaid) {
            posTableService.releaseTablesForOrder(order);
        }

        // Register successful payment for idempotency tracking
        idempotencyService.registerSuccessfulPayment(savedPayment.getTransactionId(), orderId);

        return buildPOSPaymentResponse(savedPayment, order);
    }

    /**
     * Record revenue for an order. Non-critical operation with retry and alerting.
     * Uses async processing with retry to ensure revenue recording doesn't block the payment.
     */
    private void recordRevenueNonCritical(Order order, Long orderId) {
        try {
            // Use async revenue recording with retry and alerting
            revenueRecordingService.recordRevenueWithRetry(order);
            log.info("Revenue recording initiated for order {}", orderId);
        } catch (Exception e) {
            // Even the async call itself failed - this shouldn't normally happen
            log.error("Failed to initiate revenue recording for order {}: {} - payment will still succeed",
                    orderId, e.getMessage());
        }
    }

    /**
     * Get all payments for an order (POS)
     */
    @Transactional(readOnly = true)
    public List<PaymentResponseDTO.PaymentSummary> getPOSOrderPayments(Long orderId) {
        List<Payment> payments = paymentRepository.findByOrderId(orderId);

        return payments.stream()
                .map(this::mapToPaymentSummary)
                .collect(Collectors.toList());
    }

    /**
     * Get payment summary for an order (POS)
     */
    @Transactional(readOnly = true)
    public PaymentResponseDTO getPOSPaymentSummary(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

        List<Payment> payments = paymentRepository.findByOrderId(orderId);
        List<PaymentResponseDTO.PaymentSummary> paymentSummaries = payments.stream()
                .map(this::mapToPaymentSummary)
                .collect(Collectors.toList());

        BigDecimal grandTotal = order.getGrandTotal() != null ? order.getGrandTotal() : order.getTotal();

        return PaymentResponseDTO.builder()
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .orderSubtotal(order.getSubtotal())
                .orderTax(order.getTax())
                .orderDeliveryFee(order.getDeliveryFee())
                .orderServiceFeePercent(order.getServiceFeePercent())
                .orderServiceFee(order.getServiceFee())
                .orderTotal(order.getTotal())
                .orderGrandTotal(grandTotal)
                .totalPaid(order.getTotalPaid())
                .remainingBalance(order.getRemainingBalance())
                .orderFullyPaid(order.isFullyPaid())
                .orderStatus(order.getStatus())
                .tableReleased(order.isFullyPaid() &&
                        (order.getStatus() == OrderStatus.DELIVERED || order.getStatus() == OrderStatus.CANCELLED))
                .allPayments(paymentSummaries)
                .build();
    }

    /**
     * Process a refund (POS).
     * Transaction boundary ensures all refund operations are atomic.
     *
     * @param orderId The order to refund
     * @param request The refund request details
     * @return Updated payment summary
     * @throws PaymentTransactionException if refund processing fails (triggers rollback)
     */
    @Transactional(rollbackFor = {PaymentTransactionException.class, RuntimeException.class})
    public PaymentResponseDTO processPOSRefund(Long orderId, RefundRequestDTO request) {
        String processedBy = request.getProcessedBy() != null ? request.getProcessedBy() : getCurrentUsername();

        log.info("Processing {} refund for order {}: reason={}, processedBy={}",
                request.getType(), orderId, request.getReason(), processedBy);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new PaymentTransactionException(
                        "Order not found: " + orderId, orderId, PaymentFailureReason.ORDER_STATUS_INVALID));

        BigDecimal refundAmount;

        switch (request.getType()) {
            case FULL:
                refundAmount = order.getTotalPaid();
                break;
            case PARTIAL:
                if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new IllegalArgumentException("Refund amount must be specified for partial refunds");
                }
                if (request.getAmount().compareTo(order.getTotalPaid()) > 0) {
                    throw new IllegalArgumentException("Refund amount cannot exceed total paid");
                }
                refundAmount = request.getAmount();
                break;
            case ITEMS:
                if (request.getItemIds() == null || request.getItemIds().isEmpty()) {
                    throw new IllegalArgumentException("Item IDs must be specified for item refunds");
                }
                refundAmount = calculateItemsRefundAmount(order, request.getItemIds());
                break;
            default:
                throw new IllegalArgumentException("Invalid refund type");
        }

        // Apply refund to payments (starting with most recent)
        List<Payment> completedPayments = paymentRepository.findByOrderIdAndStatus(orderId, PaymentStatus.COMPLETED);
        BigDecimal remainingRefund = refundAmount;
        BigDecimal totalRefunded = BigDecimal.ZERO;

        for (Payment payment : completedPayments) {
            if (remainingRefund.compareTo(BigDecimal.ZERO) <= 0) break;

            BigDecimal availableToRefund = payment.getNetAmount();
            BigDecimal toRefund = remainingRefund.min(availableToRefund);

            BigDecimal currentRefunded = payment.getRefundedAmount() != null ?
                    payment.getRefundedAmount() : BigDecimal.ZERO;
            payment.setRefundedAmount(currentRefunded.add(toRefund));
            payment.setRefundReason(request.getReason());
            payment.setRefundedAt(OffsetDateTime.now());
            payment.setProcessedBy(processedBy);

            // Update payment status
            if (payment.getRefundedAmount().compareTo(payment.getTotalWithTip()) >= 0) {
                payment.setStatus(PaymentStatus.REFUNDED);
            } else {
                payment.setStatus(PaymentStatus.PARTIALLY_REFUNDED);
            }

            try {
                paymentRepository.save(payment);
            } catch (OptimisticLockException | ObjectOptimisticLockingFailureException e) {
                log.warn("Concurrent modification detected for payment {}, retrying may be needed", payment.getId());
                throw new PaymentTransactionException(
                        "Payment was modified by another transaction. Please retry.",
                        orderId, payment.getId(), payment.getTransactionId(),
                        PaymentFailureReason.CONCURRENT_MODIFICATION, e);
            }
            totalRefunded = totalRefunded.add(toRefund);
            remainingRefund = remainingRefund.subtract(toRefund);

            log.info("Refunded {} from payment {}", toRefund, payment.getId());
        }

        // Update order payment status
        updateOrderPaymentStatus(order);
        try {
            orderRepository.save(order);
        } catch (OptimisticLockException | ObjectOptimisticLockingFailureException e) {
            log.warn("Concurrent modification detected for order {} during refund", orderId);
            throw new PaymentTransactionException(
                    "Order was modified by another transaction. Please retry refund.",
                    orderId, PaymentFailureReason.CONCURRENT_MODIFICATION, e);
        }

        // Audit log the refund operation
        auditService.logFinancialOperation(
                AuditAction.REFUND_COMPLETED,
                orderId,
                order.getOrderNumber(),
                order.getRestaurant() != null ? order.getRestaurant().getId() : null,
                totalRefunded,
                "UZS",
                String.format("%s refund processed by %s. Reason: %s",
                        request.getType(), processedBy, request.getReason())
        );

        return getPOSPaymentSummary(orderId);
    }

    /**
     * Void an order and cancel all payments.
     * Transaction boundary ensures all void operations are atomic.
     *
     * @param orderId The order to void
     * @param reason The reason for voiding
     * @param processedBy Who is voiding the order
     * @return Updated payment summary
     * @throws PaymentTransactionException if void processing fails (triggers rollback)
     */
    @Transactional(rollbackFor = {PaymentTransactionException.class, RuntimeException.class})
    public PaymentResponseDTO voidOrder(Long orderId, String reason, String processedBy) {
        log.info("Voiding order {}: reason={}", orderId, reason);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new PaymentTransactionException(
                        "Order not found: " + orderId, orderId, PaymentFailureReason.ORDER_STATUS_INVALID));

        // Void all payments
        List<Payment> payments = paymentRepository.findByOrderId(orderId);
        for (Payment payment : payments) {
            payment.setStatus(PaymentStatus.VOIDED);
            payment.setRefundedAmount(payment.getTotalWithTip());
            payment.setRefundReason(reason);
            payment.setRefundedAt(OffsetDateTime.now());
            paymentRepository.save(payment);
        }

        // Update order status
        order.setStatus(OrderStatus.CANCELLED);
        order.setPaymentStatus(PaymentStatus.VOIDED);
        order.setCancelledAt(OffsetDateTime.now());
        order.setCancelledBy(processedBy);
        order.setCancellationReason(reason);
        orderRepository.save(order);

        return getPOSPaymentSummary(orderId);
    }

    /**
     * Add tip to order
     */
    @Transactional
    public PaymentResponseDTO addTip(Long orderId, BigDecimal tipAmount) {
        log.info("Adding tip {} to order {}", tipAmount, orderId);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

        if (tipAmount == null || tipAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Tip amount must be greater than 0");
        }

        // Update order tip
        BigDecimal currentTip = order.getTipAmount() != null ? order.getTipAmount() : BigDecimal.ZERO;
        order.setTipAmount(currentTip.add(tipAmount));
        order.setGrandTotal(order.getTotal().add(order.getTipAmount()));
        orderRepository.save(order);

        return getPOSPaymentSummary(orderId);
    }

    // ==================== POS Helper Methods ====================

    private void validateOrderForPayment(Order order, Long orderId) {
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new PaymentTransactionException(
                    "Cannot process payment for cancelled order",
                    orderId, PaymentFailureReason.ORDER_STATUS_INVALID);
        }
        if (order.isFullyPaid()) {
            throw new PaymentTransactionException(
                    "Order is already fully paid",
                    orderId, PaymentFailureReason.ORDER_ALREADY_PAID);
        }
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

    private BigDecimal calculateItemsRefundAmount(Order order, List<Long> itemIds) {
        return order.getItems().stream()
                .filter(item -> itemIds.contains(item.getId()))
                .map(OrderItem::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void updateOrderPaymentStatus(Order order) {
        List<Payment> payments = paymentRepository.findByOrderId(order.getId());

        boolean allRefunded = payments.stream()
                .allMatch(p -> p.getStatus() == PaymentStatus.REFUNDED || p.getStatus() == PaymentStatus.VOIDED);
        boolean anyPartial = payments.stream()
                .anyMatch(p -> p.getStatus() == PaymentStatus.PARTIALLY_REFUNDED);

        if (allRefunded) {
            order.setPaymentStatus(PaymentStatus.REFUNDED);
        } else if (anyPartial) {
            order.setPaymentStatus(PaymentStatus.PARTIALLY_REFUNDED);
        }
    }

    private PaymentResponseDTO buildPOSPaymentResponse(Payment payment, Order order) {
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
                .paidAt(payment.getPaidAt() != null ? payment.getPaidAt().toLocalDateTime() : null)
                .orderTotal(order.getTotal())
                .orderGrandTotal(grandTotal)
                .totalPaid(order.getTotalPaid())
                .remainingBalance(order.getRemainingBalance())
                .orderFullyPaid(order.isFullyPaid())
                .orderStatus(order.getStatus())
                .tableReleased(order.isFullyPaid() &&
                        (order.getStatus() == OrderStatus.DELIVERED || order.getStatus() == OrderStatus.CANCELLED))
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
                .paidAt(payment.getPaidAt() != null ? payment.getPaidAt().toLocalDateTime() : null)
                .build();
    }

    /**
     * Get the current authenticated username for audit trail.
     */
    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() ? auth.getName() : "SYSTEM";
    }
}
