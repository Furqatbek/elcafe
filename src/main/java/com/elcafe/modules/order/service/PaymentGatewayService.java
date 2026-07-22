package com.elcafe.modules.order.service;

import com.elcafe.modules.loyalty.event.LoyaltyOrderEventListener;
import com.elcafe.modules.order.dto.payment.PaymentIntentRequest;
import com.elcafe.modules.order.dto.payment.PaymentIntentResponse;
import com.elcafe.modules.order.dto.payment.RefundRequest;
import com.elcafe.modules.order.dto.payment.RefundResponse;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.entity.Payment;
import com.elcafe.modules.order.enums.PaymentStatus;
import com.elcafe.modules.order.exception.PaymentTransactionException;
import com.elcafe.modules.order.exception.PaymentTransactionException.PaymentFailureReason;
import com.elcafe.modules.order.repository.OrderRepository;
import com.elcafe.modules.order.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

/**
 * Payment Gateway Integration Service.
 * Ready for Stripe, PayPal, or other payment gateway integration.
 * <p>
 * IMPORTANT: This service uses explicit transaction boundaries to ensure atomicity.
 * All payment-related operations (payment creation, order status update) are executed
 * in a single transaction. If any step fails, the entire operation is rolled back.
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentGatewayService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final TransactionalOrderOperationService transactionalOrderOperationService;
    private final OrderEventBroadcaster orderEventBroadcaster;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${payment.gateway.provider:STRIPE}")
    private String paymentProvider;

    @Value("${payment.gateway.stripe.secret-key:}")
    private String stripeSecretKey;

    @Value("${payment.gateway.stripe.publishable-key:}")
    private String stripePublishableKey;

    @Value("${payment.gateway.webhook.secret:}")
    private String webhookSecret;

    /**
     * Create payment intent for order.
     * This prepares the payment on the gateway side without charging.
     *
     * @param request The payment intent request
     * @return Payment intent response with client secret
     * @throws PaymentTransactionException if payment intent creation fails
     */
    @Transactional(rollbackFor = {PaymentTransactionException.class, RuntimeException.class})
    public PaymentIntentResponse createPaymentIntent(PaymentIntentRequest request) {
        log.info("Creating payment intent for order: {}", request.getOrderId());

        Order order = orderRepository.findById(request.getOrderId())
                .orElseThrow(() -> new PaymentTransactionException(
                        "Order not found: " + request.getOrderId(),
                        request.getOrderId(),
                        PaymentFailureReason.ORDER_STATUS_INVALID));

        if (order.getPayment() == null) {
            throw new PaymentTransactionException(
                    "Order has no payment information",
                    request.getOrderId(),
                    PaymentFailureReason.ORDER_STATUS_INVALID);
        }

        try {
            // TODO: Integrate with actual payment gateway (Stripe, PayPal, etc.)
            // Example for Stripe:
            // PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
            //     .setAmount(order.getTotal().multiply(BigDecimal.valueOf(100)).longValue())
            //     .setCurrency("usd")
            //     .putMetadata("order_id", order.getId().toString())
            //     .putMetadata("order_number", order.getOrderNumber())
            //     .build();
            // PaymentIntent intent = PaymentIntent.create(params);

            // Mock payment intent creation
            String paymentIntentId = "pi_mock_" + System.currentTimeMillis();
            String clientSecret = "pi_" + paymentIntentId + "_secret_mock";

            // Update order with payment intent ID
            order.setPaymentIntentId(paymentIntentId);
            orderRepository.save(order);

            log.info("Payment intent created: {} for order: {}", paymentIntentId, order.getOrderNumber());

            return PaymentIntentResponse.builder()
                    .paymentIntentId(paymentIntentId)
                    .clientSecret(clientSecret)
                    .amount(order.getTotal())
                    .currency("USD")
                    .status("requires_payment_method")
                    .build();

        } catch (PaymentTransactionException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to create payment intent for order {}: {}",
                    order.getOrderNumber(), e.getMessage(), e);
            throw new PaymentTransactionException(
                    "Failed to create payment intent: " + e.getMessage(),
                    request.getOrderId(),
                    PaymentFailureReason.GATEWAY_ERROR, e);
        }
    }

    /**
     * Confirm payment and update order status atomically.
     * Called when payment is successfully completed on the client side.
     * <p>
     * This method uses TransactionalOrderOperationService to ensure atomicity:
     * - Payment status update
     * - Order status update
     * - Revenue recording
     * All succeed or all fail together.
     * </p>
     *
     * @param paymentIntentId The payment intent ID from the gateway
     * @throws PaymentTransactionException if payment confirmation fails (triggers rollback)
     */
    @Transactional(rollbackFor = {PaymentTransactionException.class, RuntimeException.class})
    public void confirmPayment(String paymentIntentId) {
        log.info("Confirming payment for intent: {}", paymentIntentId);

        try {
            // Use the transactional service for atomic operation
            Order order = transactionalOrderOperationService.completeOrderWithPaymentConfirmation(paymentIntentId);

            // Broadcast order placed event to admin (non-critical, after transaction)
            broadcastOrderPlacedNonCritical(order);

            log.info("Payment confirmed for order: {}", order.getOrderNumber());

        } catch (PaymentTransactionException e) {
            log.error("Payment confirmation failed for intent {}: {}", paymentIntentId, e.getMessage());
            // Mark payment as failed in a separate transaction
            markPaymentFailedInNewTransaction(paymentIntentId);
            throw e;
        } catch (Exception e) {
            log.error("Failed to confirm payment for intent {}: {}", paymentIntentId, e.getMessage(), e);
            markPaymentFailedInNewTransaction(paymentIntentId);
            throw new PaymentTransactionException(
                    "Failed to confirm payment: " + e.getMessage(),
                    null, paymentIntentId,
                    PaymentFailureReason.GATEWAY_ERROR, e);
        }
    }

    /**
     * Mark payment as failed in a new transaction.
     * This ensures failure is recorded even if the main transaction rolls back.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markPaymentFailedInNewTransaction(String paymentIntentId) {
        try {
            orderRepository.findByPaymentIntentId(paymentIntentId).ifPresent(order -> {
                Payment payment = order.getPayment();
                if (payment != null) {
                    payment.setStatus(PaymentStatus.FAILED);
                    paymentRepository.save(payment);
                    log.info("Marked payment as FAILED for intent {}", paymentIntentId);
                }
            });
        } catch (Exception e) {
            log.error("Failed to mark payment as failed for intent {}: {}",
                    paymentIntentId, e.getMessage());
        }
    }

    /**
     * Broadcast order placed event in a non-critical way.
     * Failure doesn't affect the main transaction.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void broadcastOrderPlacedNonCritical(Order order) {
        try {
            orderEventBroadcaster.broadcastOrderPlaced(order);
        } catch (Exception e) {
            log.error("Failed to broadcast order placed event for {}: {}",
                    order.getOrderNumber(), e.getMessage());
        }
    }

    /**
     * Process refund for cancelled or rejected orders.
     *
     * @param request The refund request
     * @return Refund response with status
     * @throws PaymentTransactionException if refund processing fails
     */
    @Transactional(rollbackFor = {PaymentTransactionException.class, RuntimeException.class})
    public RefundResponse processRefund(RefundRequest request) {
        log.info("Processing refund for order: {}", request.getOrderId());

        Order order = orderRepository.findById(request.getOrderId())
                .orElseThrow(() -> new PaymentTransactionException(
                        "Order not found: " + request.getOrderId(),
                        request.getOrderId(),
                        PaymentFailureReason.ORDER_STATUS_INVALID));

        Payment payment = order.getPayment();
        if (payment == null) {
            throw new PaymentTransactionException(
                    "No payment found for order",
                    request.getOrderId(),
                    PaymentFailureReason.ORDER_STATUS_INVALID);
        }

        if (payment.getStatus() != PaymentStatus.COMPLETED) {
            throw new PaymentTransactionException(
                    "Cannot refund payment that is not completed",
                    request.getOrderId(),
                    payment.getId(),
                    payment.getTransactionId(),
                    PaymentFailureReason.ORDER_STATUS_INVALID);
        }

        try {
            // TODO: Process refund with payment gateway
            // Example for Stripe:
            // RefundCreateParams params = RefundCreateParams.builder()
            //     .setPaymentIntent(order.getPaymentIntentId())
            //     .setAmount(request.getAmount().multiply(BigDecimal.valueOf(100)).longValue())
            //     .setReason(RefundCreateParams.Reason.REQUESTED_BY_CUSTOMER)
            //     .build();
            // Refund refund = Refund.create(params);

            // Mock refund processing
            String refundId = "re_mock_" + System.currentTimeMillis();

            // Update payment status
            payment.setStatus(PaymentStatus.REFUNDED);
            payment.setRefundedAt(OffsetDateTime.now());
            paymentRepository.save(payment);

            // Update order payment status
            order.setPaymentStatus(com.elcafe.modules.order.enums.PaymentStatus.REFUNDED);
            orderRepository.save(order);

            // Publish refund event for loyalty points reversal and other listeners
            eventPublisher.publishEvent(new LoyaltyOrderEventListener.OrderRefundedEvent(order, request.getAmount()));
            log.info("Published OrderRefundedEvent for order: {}", order.getOrderNumber());

            log.info("Refund processed: {} for order: {}", refundId, order.getOrderNumber());

            return RefundResponse.builder()
                    .refundId(refundId)
                    .amount(request.getAmount())
                    .status("succeeded")
                    .estimatedArrival(LocalDateTime.now().plusDays(7))
                    .build();

        } catch (PaymentTransactionException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to process refund for order {}: {}",
                    order.getOrderNumber(), e.getMessage(), e);
            throw new PaymentTransactionException(
                    "Failed to process refund: " + e.getMessage(),
                    request.getOrderId(),
                    payment.getId(),
                    payment.getTransactionId(),
                    PaymentFailureReason.REFUND_FAILED, e);
        }
    }

    /**
     * Handle webhook from payment gateway.
     * Called when payment gateway sends status updates.
     *
     * @param payload The webhook payload
     * @param signature The webhook signature for verification
     * @throws PaymentTransactionException if webhook processing fails
     */
    @Transactional(rollbackFor = {PaymentTransactionException.class, RuntimeException.class})
    public void handleWebhook(String payload, String signature) {
        log.info("Received payment webhook");

        try {
            // TODO: Verify webhook signature
            // Example for Stripe:
            // Event event = Webhook.constructEvent(payload, signature, webhookSecret);

            // TODO: Process webhook event based on type
            // switch (event.getType()) {
            //     case "payment_intent.succeeded":
            //         handlePaymentSucceeded(event);
            //         break;
            //     case "payment_intent.payment_failed":
            //         handlePaymentFailed(event);
            //         break;
            //     case "charge.refunded":
            //         handleRefundCompleted(event);
            //         break;
            //     default:
            //         log.warn("Unhandled webhook event type: {}", event.getType());
            // }

            log.info("Webhook processed successfully");

        } catch (Exception e) {
            log.error("Failed to process webhook: {}", e.getMessage(), e);
            throw new PaymentTransactionException(
                    "Failed to process webhook: " + e.getMessage(),
                    PaymentFailureReason.GATEWAY_ERROR);
        }
    }

    /**
     * Verify payment status with gateway.
     * Used by background job to check pending payments.
     * This is a read-only operation that doesn't require transaction.
     *
     * @param paymentIntentId The payment intent ID to verify
     * @return Payment status string
     */
    public String verifyPaymentStatus(String paymentIntentId) {
        try {
            // TODO: Check payment status with gateway
            // Example for Stripe:
            // PaymentIntent intent = PaymentIntent.retrieve(paymentIntentId);
            // return intent.getStatus();

            // Mock verification
            return "succeeded";

        } catch (Exception e) {
            log.error("Failed to verify payment status for intent {}: {}",
                    paymentIntentId, e.getMessage(), e);
            return "unknown";
        }
    }

    /**
     * Get publishable key for client-side payment form.
     *
     * @return The publishable key
     */
    public String getPublishableKey() {
        if (stripePublishableKey == null || stripePublishableKey.isEmpty()) {
            log.warn("Stripe publishable key not configured");
            return "pk_test_mock_key";
        }
        return stripePublishableKey;
    }
}
