package com.elcafe.modules.order.service;

import com.elcafe.modules.order.dto.payment.PaymentIntentRequest;
import com.elcafe.modules.order.dto.payment.PaymentIntentResponse;
import com.elcafe.modules.order.dto.payment.RefundRequest;
import com.elcafe.modules.order.dto.payment.RefundResponse;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.entity.Payment;
import com.elcafe.modules.order.enums.PaymentStatus;
import com.elcafe.modules.order.repository.OrderRepository;
import com.elcafe.modules.order.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Payment Gateway Integration Service
 * Ready for Stripe, PayPal, or other payment gateway integration
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentGatewayService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final OrderEventBroadcaster orderEventBroadcaster;

    @Value("${payment.gateway.provider:STRIPE}")
    private String paymentProvider;

    @Value("${payment.gateway.stripe.secret-key:}")
    private String stripeSecretKey;

    @Value("${payment.gateway.stripe.publishable-key:}")
    private String stripePublishableKey;

    @Value("${payment.gateway.webhook.secret:}")
    private String webhookSecret;

    /**
     * Create payment intent for order
     * This prepares the payment on the gateway side without charging
     */
    @Transactional
    public PaymentIntentResponse createPaymentIntent(PaymentIntentRequest request) {
        log.info("Creating payment intent for order: {}", request.getOrderId());

        Order order = orderRepository.findById(request.getOrderId())
                .orElseThrow(() -> new RuntimeException("Order not found: " + request.getOrderId()));

        if (order.getPayment() == null) {
            throw new RuntimeException("Order has no payment information");
        }

        try {
            // TODO: Integrate with actual payment gateway (Stripe, PayPal, etc.)
            // Example for Stripe:
            // PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
            //     .setAmount(order.getTotal().multiply(BigDecimal.valueOf(100)).longValue()) // Amount in cents
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

        } catch (Exception e) {
            log.error("Failed to create payment intent for order {}: {}", order.getOrderNumber(), e.getMessage(), e);
            throw new RuntimeException("Failed to create payment intent: " + e.getMessage());
        }
    }

    /**
     * Confirm payment and update order status
     * Called when payment is successfully completed on the client side
     */
    @Transactional
    public void confirmPayment(String paymentIntentId) {
        log.info("Confirming payment for intent: {}", paymentIntentId);

        Order order = orderRepository.findByPaymentIntentId(paymentIntentId)
                .orElseThrow(() -> new RuntimeException("Order not found for payment intent: " + paymentIntentId));

        try {
            // TODO: Verify payment with gateway
            // Example for Stripe:
            // PaymentIntent intent = PaymentIntent.retrieve(paymentIntentId);
            // if (!"succeeded".equals(intent.getStatus())) {
            //     throw new RuntimeException("Payment not successful");
            // }

            // Update payment status
            Payment payment = order.getPayment();
            payment.setStatus(PaymentStatus.COMPLETED);
            payment.setCompletedAt(LocalDateTime.now());
            paymentRepository.save(payment);

            // Update order status from PENDING to PLACED
            order.setPaymentStatus(com.elcafe.modules.order.enums.PaymentStatus.COMPLETED);
            orderService.updateOrderStatus(
                    order.getId(),
                    com.elcafe.modules.order.enums.OrderStatus.PLACED,
                    "Payment confirmed",
                    "PAYMENT_GATEWAY"
            );

            // Broadcast order placed event to admin
            orderEventBroadcaster.broadcastOrderPlaced(order);

            log.info("Payment confirmed for order: {}", order.getOrderNumber());

        } catch (Exception e) {
            log.error("Failed to confirm payment for intent {}: {}", paymentIntentId, e.getMessage(), e);

            // Mark payment as failed
            Payment payment = order.getPayment();
            payment.setStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);

            throw new RuntimeException("Failed to confirm payment: " + e.getMessage());
        }
    }

    /**
     * Process refund for cancelled or rejected orders
     */
    @Transactional
    public RefundResponse processRefund(RefundRequest request) {
        log.info("Processing refund for order: {}", request.getOrderId());

        Order order = orderRepository.findById(request.getOrderId())
                .orElseThrow(() -> new RuntimeException("Order not found: " + request.getOrderId()));

        Payment payment = order.getPayment();
        if (payment == null) {
            throw new RuntimeException("No payment found for order");
        }

        if (payment.getStatus() != PaymentStatus.COMPLETED) {
            throw new RuntimeException("Cannot refund payment that is not completed");
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
            payment.setRefundedAt(LocalDateTime.now());
            paymentRepository.save(payment);

            // Update order payment status
            order.setPaymentStatus(com.elcafe.modules.order.enums.PaymentStatus.REFUNDED);
            orderRepository.save(order);

            log.info("Refund processed: {} for order: {}", refundId, order.getOrderNumber());

            return RefundResponse.builder()
                    .refundId(refundId)
                    .amount(request.getAmount())
                    .status("succeeded")
                    .estimatedArrival(LocalDateTime.now().plusDays(7))
                    .build();

        } catch (Exception e) {
            log.error("Failed to process refund for order {}: {}", order.getOrderNumber(), e.getMessage(), e);
            throw new RuntimeException("Failed to process refund: " + e.getMessage());
        }
    }

    /**
     * Handle webhook from payment gateway
     * Called when payment gateway sends status updates
     */
    @Transactional
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
            throw new RuntimeException("Failed to process webhook: " + e.getMessage());
        }
    }

    /**
     * Verify payment status with gateway
     * Used by background job to check pending payments
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
            log.error("Failed to verify payment status for intent {}: {}", paymentIntentId, e.getMessage(), e);
            return "unknown";
        }
    }

    /**
     * Get publishable key for client-side payment form
     */
    public String getPublishableKey() {
        if (stripePublishableKey == null || stripePublishableKey.isEmpty()) {
            log.warn("Stripe publishable key not configured");
            return "pk_test_mock_key";
        }
        return stripePublishableKey;
    }
}
